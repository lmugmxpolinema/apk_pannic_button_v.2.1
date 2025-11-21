- `app/src/main/java/com/example/panicbuttonrtdb/utils/LocationUtils.kt` (used via `getCurrentLocation`) — used to fetch location in `ToggleSwitch`.

---

5. Per-issue detail analysis (technical detail, no code changes suggested)

5.1 BUG LOGIN: Detailed trace
- Sequence yang terjadi saat login:
  1. Di `LoginScreen`, user memilih perumahan -> `UserPreferences.savePerumahan(perumahanId, nama)` dipanggil.
  2. `viewModel.validateLogin(houseNumber, password)` dipanggil. ViewModel membaca `userPreferences.getPerumahanId()` (yang baru saja disimpan) dan melakukan query ke `perumahan/{perumahanId}/users`.
  3. Jika login valid, ViewModel menulis `userPreferences.saveUserLoggedIn(true)` dan `userPreferences.saveUserInfo(user.name, user.houseNumber)` dan juga mengatur `currentUserName` dan `currentUserHouseNumber` pada instance ViewModel.
- Mengapa bisa tampil user lain di Dashboard?
  - Dashboard menampilkan `viewModel.currentUserName` — jika viewModel yang ditampilkan sama instance dengan yang menjalankan validateLogin (umumnya iya karena MainApp melewatkan instance), teks welcome akan benar.
  - Namun profil dan fetch lain di Dashboard menggunakan query ke global `users` berdasarkan `houseNumber` yang dibaca langsung dari SharedPreferences. Jika global `/users` berisi data user lain untuk houseNumber tersebut atau jika SharedPreferences tidak update/di-read oleh code yang sama (mis. race timing), Dashboard dapat menampilkan informasi berbeda.
  - Selain itu, jika ada composable yang membuat instance ViewModel sendiri (mis. SignUpScreen), maka ada keduanya: satu instance yang menyimpan data user di internal field dan instance lain yang tidak. Ini dapat memicu kondisi di mana `saveMonitorData()` dipanggil dari instance ViewModel yang tidak berisi `currentUserName/currentUserHouseNumber` terbaru.

5.2 BUG ALARM (Toggle OFF not working): Detailed trace
- `ToggleSwitch`'s Switch OFF handler memanggil `viewModel.updateAlarmForPerumahan(perumahanId, "off", "off")`.
- `ViewModel.updateAlarmForPerumahan` menulis ke `perumahan/{perumahanId}/buzzers/main`.
- `ViewModel.getBuzzerState()` yang menentukan nilai LiveData `buzzerState` bekerja sbb: saat ViewModel diinit ia membaca `userPreferences.getPerumahanId()` *sekali*, dan jika value non-empty, attach listener ke `perumahan/{id}/buzzers/main` (membaca child `state`), else attach ke legacy `databaseRef` (`/buzzer`).
- Jika ViewModel dibuat saat aplikasi start sebelum perumahanId disimpan, listener attach ke legacy `/buzzer`. Setelah login (perumahanId diset), listener tidak otomatis berpindah. Akibatnya UI masih memantau `/buzzer`, sementara updateAlarmForPerumahan menulis ke `perumahan/{id}/buzzers/main` (atau sebaliknya). Toggle OFF akan menulis ke perumahan path, tetapi UI/device yang membaca path lama tidak melihat perubahan.
- Auto-off 30s menulis ke perumahan path jika `getPerumahanId()` pada saat auto-off non-empty, else fallback ke legacy nodes. Jika perumahanId tersedia tetapi listener masih di legacy path, sinkronisasi gagal.

5.3 BUG WRONG USER SEND PANIC: Detailed trace
- `ToggleSwitch` pada confirm Dialog melakukan: (pseudologic) -> request location permission -> onResult { getCurrentLocation { viewModel.saveMonitorData(message, selectedPriority, "Proses", lat, lon); viewModel.updateAlarmForPerumahan(perumahanId, "on", level) }}
- `saveMonitorData` constructs a map using `currentUserName` and `currentUserHouseNumber` from the ViewModel instance that executes the method.
- Jika instance tersebut tidak ter-update (karena: login terjadi pada instance lain, or user info saved in SharedPreferences but this instance's fields not refreshed), `currentUserName`/`currentUserHouseNumber` bisa berisi nilai lama (User1) sehingga entry `monitor` di Firebase menunjukkan user lama.
- SharedPreferences menjadi sumber truth persistence, namun code terkadang membaca langsung prefs (Dashboard) dan terkadang mengandalkan ViewModel's in-memory fields (saveMonitorData). Perbedaan ini membuat mismatch.

5.4 DATABASE RELATED CHECK: Detailed trace
- Code paths:
  - Reads:
    - ViewModel.getBuzzerState() -> sometimes listens to `perumahan/{id}/buzzers/main` or fallback `/buzzer`
    - DashboardUserScreen -> reads profile from global `/users`
  - Writes:
    - updateAlarmForPerumahan() -> write to `perumahan/{id}/buzzers/main`
    - setBuzzerState() -> write to `/buzzer` (string state)
    - updateBuzzerState() -> write to `/buzzer_priority` (map priority/level)
    - saveMonitorData() -> writes to `monitor` (global)
- Jika infrastruktur (ESP) sekarang membaca `perumahan/{id}/buzzers/main` path, pastikan bahwa pada saat ON/OFF/auto-off semua penulisan diarahkan ke path ini. Kode ada fallback ke legacy nodes, sehingga tergantung kondisi runtime perumahanId, penulisan mungkin ke yang lama dan tidak efektif untuk perangkat baru.

5.5 STATE MANAGEMENT: LiveData/StateFlow checks
- `buzzerState` (MutableLiveData): source tergantung listener yang di-attach dalam `getBuzzerState()` (perumahan-based vs global). Karena attach hanya terjadi saat init, perubahan perumahanId tidak memicu re-attach. Potensi race: UI menampilkan `buzzerState` dari node yang berbeda.
- `latestRecord` (MutableStateFlow): fetchLatestRecord() di-init di ViewModel init; ia men-set `_latestRecord` saat monitorRef changed. Jika monitor entries ditulis ke `monitor` path, latestRecord should reflect pushed entries. Namun, jika `saveMonitorData` wrote with wrong user value, latestRecord will correctly reflect the data *that was written* (even if name wrong) — but does not cause cross-instance confusion.
- `monitorData` (MutableLiveData): dipakai untuk lists; functions like `userHistory()` filter on `currentUserHouseNumber` — jika `currentUserHouseNumber` stale, filter akan menghasilkan wrong data.

5.6 LOCATION & PERMISSION check
- `ToggleSwitch` requests permissions via `rememberLauncherForActivityResult` and in `onResult` if ACCESS_FINE_LOCATION granted, it calls `getCurrentLocation(context) { lat, lon -> ... }` and proceeds to save data.
- `getCurrentLocation` lives in `utils/LocationUtils.kt` — code path used is correct; as long as permissions are granted, location callback yields coordinates and `saveMonitorData()` is invoked inside that callback.
- Race condition possibility: permission dialog triggers activity-level lifecycle changes; since priority/level are stored in composable local state, there is a risk of those values being reset before location callback runs — leading to wrong priority data being saved.

---

6. Kesimpulan Ringkas (Final conclusions)

- Penyebab utama masalah "wrong user shown / wrong user saved" adalah kombinasi: inconsistent DB access paths (perumahan vs global users) dan multi-instance ViewModel / mixed state sources (SharedPreferences vs in-memory ViewModel fields). Ini menyebabkan `saveMonitorData()` kadang menggunakan stale user data.

- Toggle OFF dan Auto-off 30s gagal mempengaruhi perangkat karena perbedaan antara node yang di-listen (`buzzerState` listener) dan node yang di-write (`updateAlarmForPerumahan`). Listener terpasang pada saat inisialisasi dan tidak berpindah saat perumahanId berubah.

- Prioritas yang salah (Biasa -> Darurat) kemungkinan besar karena `PriorityButton` memiliki default internal "Darurat" dan state komposabel tidak di-hoist; permission flow asynchronous bisa membuat nilai ini kembali ke default sebelum `saveMonitorData` dipanggil.

- Secara keseluruhan, issue bukan akibat bug syntax atau error compile — melainkan desain sinkronisasi state (cross-instance), pemilihan path database ganda, dan lifecycle/asynchronous flows.

---

7. Rekomendasi non-kode / tindakan operasional (sesuai permintaan: tidak menyarankan rewrites)

Catatan: Anda minta *tidak* memberi saran yang melibatkan rewrite file. Di bawah ini hanya tindakan non-kode atau proses yang dapat dilakukan oleh tim tanpa penulisan ulang kode, atau langkah verifikasi untuk debug.

7.1 Verifikasi runtime & debugging (prioritas tinggi)
- Jalankan aplikasi di emulator/device dan catat log (logcat) saat sequence berikut terjadi: (a) pilih perumahan & login sebagai user X, (b) segera tekan toggle ON dan kirim panic, (c) lihat entri di Firebase `monitor` dan periksa fields `name`/`houseNumber`.
- Verifikasi value perumahanId di SharedPreferences sebelum dan sesudah `validateLogin()` (Anda bisa melihat melalui logging runtime or temporary debug UI). Pastikan perumahanId benar.
- Verifikasi pada saat ON/OFF bahwa penulisan ke Firebase diarahkan ke path yang diharapkan (`perumahan/{id}/buzzers/main`), dan periksa apakah ada entri pada `/buzzer` atau `/buzzer_priority`.

7.2 Operational mitigations (non-code)
- Dokumen migrasi DB: Pastikan tim infrastruktur/devops memastikan device (ESP) membaca node yang sama yang dipakai aplikasi. Jika ESP menggunakan `perumahan/{id}/buzzers/main`, informasikan tim app untuk memfavor path tersebut.
- Konfigurasi testing harness: Buat langkah manual test checklist yang memastikan saat login, dashboard dan monitor entries sesuai user. Uji juga toggle ON/OFF dan auto-off pada real device.

7.3 Quick audit steps
- Audit database content: lihat apakah ada duplikasi data user (global `/users` vs `perumahan/{id}/users`) untuk houseNumber yang sama. Jika ada duplikasi, catat entri mana yang benar.
- Audit timing: jalankan scenario login -> immediately send panic -> verify if monitor entry contains new user or old. Lakukan dengan logging timestamps.

7.4 Communication for dev team (non-code)
- Sampaikan hasil ini ke developer: inti masalah adalah *sinkronisasi state & path mismatch*, bukan bug minor. Tim harus memprioritaskan memastikan single source-of-truth untuk user identity dan canonical DB path for buzzer.

---

8. Lampiran — Ringkasan File yang paling relevan (sebagai referensi cepat)
- `UserPreferences.kt` — SharedPreferences management
- `ViewModel.kt` — semua logic state, Firebase reads/writes, saveMonitorData(), getBuzzerState(), updateAlarmForPerumahan()
- `ViewModelFactory.kt` — factory yang memicu multiple instances jika digunakan tanpa hati-hati
- `MainApp.kt` — membuat shared ViewModel dan melewatkan ke screens
- `LoginScreen.kt` — menyimpan perumahan dan memanggil validateLogin
- `DashboardUserScreen.kt` — menampilkan viewModel.currentUserName dan juga query global `/users`
- `ToggleSwitch.kt` — local priority state, Switch on/off logic, auto-off LaunchedEffect
- `PrioritySelection.kt` — PriorityButton internal state default "Darurat"
- `LocationUtils.kt` — digunakan untuk mendapatkan lat/lon

---

9. Penutup

Laporan ini adalah analisis teknis lengkap berdasarkan kode yang ada di workspace. Tidak ada perubahan kode yang dilakukan. Semua temuan, akar penyebab, dan file terkait telah dicatat. Jika Anda ingin, saya dapat selanjutnya menyiapkan: (A) daftar perubahan kode terperinci (differences/patch list) untuk developer implementasikan, atau (B) skrip testing / log commands untuk mereproduksi issue pada runtime. Pilih satu opsi jika Anda ingin saya lanjutkan.

ANALISA APK v1.2

Penerimaan tugas dan rencana singkat
- Tugas: Buat laporan analisis teknis menyeluruh tanpa mengubah kode, fokus pada bug login, alarm, wrong user send panic, database path mismatch, state management, lokasi/permission.
- Rencana: (1) cek alur login & prefs, (2) telusuri ViewModel inisialisasi dan state, (3) analisa Toggle/Alarm/Auto-off, (4) verifikasi penulisan ke Firebase (perumahan vs legacy), (5) evaluasi LiveData/StateFlow, (6) verifikasi alur lokasi & permission.

Checklist (saya selesaikan semua item berikut dalam laporan ini):
- [x] Ringkasan temuan
- [x] Daftar bug terperinci
- [x] Akar penyebab untuk tiap bug
- [x] File yang terlibat dan fungsi/kontainer terkait
- [x] Validasi bahwa tidak ada perubahan kode dilakukan
- [x] Tidak memberi saran berupa rewrite kode (hanya observasi dan perbaikan non-kode atau proses)


---

1. Rangkuman Temuan (Summary of findings)

Secara garis besar, analisis menemukan beberapa pola penyebab berulang:
- Ketidakkonsistenan sumber data pengguna: sebagian kode menulis/membaca user dari path perumahan/{id}/users sementara beberapa UI/komponen lain membaca dari global /users. Ini menghasilkan kondisi di mana tampilan atau penulisan data memakai user berbeda.
- Multi-instance ViewModel: beberapa composable mendeklarasikan `viewModel(factory = ViewModelFactory(...))` sendiri (contoh: `SignUpScreen`), sementara `MainApp` membuat dan meneruskan satu instance ke screen lain. Akibatnya, state tersimpan di beberapa instance berbeda yang tidak tersinkron.
- State lokal composable yang tidak di-hoist: komponen priority (`PriorityButton`) dan pilihan prioritas di `ToggleSwitch` menyimpan state lokal (dengan default "Darurat") sehingga nilai dapat kembali ke default saat terjadi recomposition atau saat permission dialog berlangsung.
- Listener Firebase yang di-attach sekali (di ViewModel init) berdasarkan `perumahanId` saat itu dan tidak di-reattach ketika perumahanId diubah setelah login — menyebabkan UI membaca path yang berbeda dengan path yang ditulis pada event ON/OFF.
- Dual-path database architecture: aplikasi masih mempunyai fallback ke legacy nodes (`/buzzer` dan `/buzzer_priority`) selain path baru (`perumahan/{id}/buzzers/main`). Terdapat kondisi di mana penulisan/auto-off menggunakan path lama sementara ESP membaca path baru, atau sebaliknya.
- Asynchronous permission/location flow: penulisan `monitor` terjadi di callback permission result; jika prioritas/state disimpan hanya di composable lokal, state tersebut berisiko berubah/reinit sebelum callback berjalan.

Dampak langsung dari temuan di atas menimbulkan fenomena: (1) dashboard menampilkan user lain daripada user yang melakukan login/pengiriman panic, (2) prioritas ter-sent salah (Biasa -> Darurat), (3) toggle OFF tidak mematikan device karena path mismatch, (4) auto-off 30s menulis ke path yang tidak dibaca device.

---

2. Daftar Bug (dengan ringkasan tiap bug)

BUG LOGIN (User A login but Firebase reads data from User B)
- Gejala: Setelah login sebagai User A, beberapa data yang dibaca dari Firebase (atau tampilan profil) menampilkan User B.

BUG ALARM (Toggle OFF not working)
- Gejala: Menekan switch untuk OFF tidak mematikan buzzer pada perangkat (ESP) atau tidak mengubah state yang terlihat di UI/device.

BUG WRONG USER SEND PANIC
- Gejala: Ketika User3 mengirim panic, entri `monitor` atau tampilan di dashboard mencatat User1 sebagai pengirim.

BUG DATABASE MISMATCH
- Gejala: Kode menulis dan membaca pada lokasi yang berbeda (`/buzzer`, `/buzzer_priority` versus `perumahan/{id}/buzzers/main`), sehingga device yang membaca path baru tidak mendapat update.

BUG STATE MANAGEMENT
- Gejala: UI dan ViewModel/SharedPreferences keluar dari sinkronisasi (mis. `buzzerState`, `latestRecord`, `monitorData` tidak update/atau update dari sumber yang salah).

BUG LOCATION & PERMISSION TIMING
- Gejala: `saveMonitorData()` dipanggil dalam callback permission/location; potensi kondisi di mana location belum tersedia atau state yang dikirim berbeda karena race.

---

3. Akar Penyebab (Root causes) — per bug

3.1 BUG LOGIN (Wrong user shown)
- Penyebab teknis utama:
  - Inconsistent data sources: `validateLogin()` mencari user pada `perumahan/{perumahanId}/users` (ViewModel), sementara `DashboardUserScreen` fetch profile dari global `users` (query ke `FirebaseDatabase.getReference("users")`). Bila global `users` dan perumahan users tidak sepenuhnya sinkron, Dashboard dapat menampilkan pengguna berbeda.
  - SharedPreferences usage: `LoginScreen` menyimpan perumahan selection ke SharedPreferences melalui `UserPreferences(context).savePerumahan(...)` sebelum memanggil `viewModel.validateLogin()`; `ViewModel` juga menyimpan user info ke SharedPreferences pada login sukses. Namun, beberapa komponen membaca langsung SharedPreferences (Dashboard reads house_number) sementara `ViewModel` menyimpan `currentUserName`/`currentUserHouseNumber` di memory instance-nya — perbedaan ini berpotensi menyebabkan inkonsistensi ketika ada multiple ViewModel instances.
  - Multiple ViewModel instances: `MainApp` membuat satu instance dan meneruskannya, tapi beberapa screens (mis. `SignUpScreen`) membuat instance sendiri via default param `viewModel(factory=...)`. Jika screen yang berinteraksi (contoh sign up) menggunakan instance berbeda, SharedPreferences mungkin diubah tanpa instance ViewModel yang dipakai UI mendapatkan update internalnya.

3.2 BUG ALARM (Toggle OFF not working)
- Penyebab teknis utama:
  - `getBuzzerState()` memasang listener pada init dengan memilih perumahan path hanya jika `userPreferences.getPerumahanId()` bukan kosong pada saat inisialisasi ViewModel; jika perumahanId belum diset saat itu, listener terpasang ke legacy `/buzzer` dan tidak otomatis berpindah setelah login.
  - Switch OFF path menulis ke `updateAlarmForPerumahan(perumahanId, "off", "off")` tanpa verifikasi perumahanId; jika perumahanId kosong atau tidak sesuai, write tidak mencapai path yang dibaca ESP.
  - Auto-off fallback dan manual OFF menulis ke jalur yang berbeda tergantung kondisi perumahanId: ini menyebabkan ketidakkonsistenan dan device tidak mematikan.

3.3 BUG WRONG USER SEND PANIC
- Penyebab teknis utama:
  - `saveMonitorData()` menyimpan field `"name"` dan `"houseNumber"` dari `ViewModel.currentUserName` dan `ViewModel.currentUserHouseNumber` pada saat panggilan. Jika ViewModel instance yang dipakai tidak memiliki nilai terbaru (mis. instance berbeda, atau belum di-update karena login baru dilakukan di instance lain), maka nilai lama akan tersimpan.
  - Login path mengupdate SharedPreferences, tetapi `ViewModel` instance yang mengeksekusi `saveMonitorData()` mungkin belum sinkron/diinisialisasi ulang dengan nilai prefs terbaru.
  - Penempatan panggilan `UserPreferences(context).savePerumahan(...)` di `LoginScreen` terjadi sebelum `validateLogin` dipanggil; jika ada penundaan/pemilihan instance, perumahanId yang digunakan untuk menentukan listener/writes bisa berbeda.

3.4 DATABASE RELATED CHECK
- Penyebab teknis utama:
  - Dual schema exists: legacy global nodes (`/buzzer`, `/buzzer_priority`) dan new perumahan-based nodes (`perumahan/{id}/buzzers/main`). Kode menggunakan keduanya sebagai fallback: `getBuzzerState()` akan attach ke perumahan path jika perumahanId ada saat init, else fallback ke `/buzzer`. Auto-off logic juga menulis ke perumahan path if perumahanId set, else to legacy nodes.
  - Jika ESP sekarang membaca `perumahan/{id}/buzzers/main`, tetapi aplikasi menulis ke `/buzzer` karena perumahanId tidak tersedia saat penulisan, ESP tidak akan menerima update.

3.5 STATE MANAGEMENT
- Penyebab teknis utama:
  - `buzzerState` adalah LiveData yang nilai awal dan listener tergantung pada ViewModel init path selection; tidak di-reattach saat perumahanId berubah.
  - `latestRecord` adalah StateFlow yang diemit oleh `fetchLatestRecord()` - ini tampak terpasang di `init()`, tapi tidak ada masalah besar di implementasi fetch (selama monitorRef path tetap benar). `monitorData` (MutableLiveData) diisi oleh fetch listener, namun filter userHistory() menggunakan `currentUserHouseNumber` saat iterasi — jika `currentUserHouseNumber` stale, hasil filter salah.

3.6 LOCATION & PERMISSION
- Penyebab teknis utama:
  - `ToggleSwitch` meminta permission dan di `onResult` callback memanggil `getCurrentLocation(context) { lat, lon -> viewModel.saveMonitorData(...) }`. Jika permission ditolak/diterima lalu lokasi belum tersedia, callback tidak menulis data; jika state prioritas di-composable hilang saat permission dialog, data yang dikirim bisa salah.
  - `getCurrentLocation` merupakan util function (tidak diubah) — analisis menunjukkan file util tersedia (`LocationUtils.kt`) dan `ToggleSwitch` menggunakan `getCurrentLocation(context) { ... }` dalam launcher callback.

---

4. Files involved (daftar file penting dan fungsi yang relevan)

- `app/src/main/java/com/example/panicbuttonrtdb/data/UserPreferences.kt`
  - savePerumahan, saveUserInfo, getPerumahanId, getUserName, getHouseNumber

- `app/src/main/java/com/example/panicbuttonrtdb/viewmodel/ViewModel.kt`
  - currentUserName/currentUserHouseNumber fields
  - init() reads from UserPreferences
  - validateLogin(...): queries `perumahan/{id}/users` and sets prefs + fields
  - saveMonitorData(...): writes monitor ref with `name` and `houseNumber` taken from ViewModel fields
  - getBuzzerState(), setBuzzerState(), updateBuzzerState(), updateAlarmForPerumahan()
  - fetchMonitorData(), fetchLatestRecord(), userHistory()

- `app/src/main/java/com/example/panicbuttonrtdb/viewmodel/ViewModelFactory.kt`
  - Factory that constructs ViewModel(context)

- `app/src/main/java/com/example/panicbuttonrtdb/navigation/MainApp.kt`
  - Top-level creation of ViewModel and passing to multiple screens. Start destination logic reads viewModel.isUserLoggedIn() / isAdminLoggedIn().

- `app/src/main/java/com/example/panicbuttonrtdb/prensentation/screens/LoginScreen.kt`
  - Saves perumahan via `UserPreferences(context).savePerumahan(...)` before calling `viewModel.validateLogin(...)`.

- `app/src/main/java/com/example/panicbuttonrtdb/prensentation/screens/SignUpScreen.kt`
  - Declares default `viewModel = viewModel(factory = ViewModelFactory(LocalContext.current))` → creates separate ViewModel instance if not provided.

- `app/src/main/java/com/example/panicbuttonrtdb/prensentation/screens/DashboardUserScreen.kt`
  - UI uses `viewModel.currentUserName`, `viewModel.currentUserHouseNumber` and also fetches profile from global `users` by reading `house_number` from SharedPreferences.

- `app/src/main/java/com/example/panicbuttonrtdb/prensentation/components/ToggleSwitch.kt`
  - Local `selectedPriority`/`selectedLevel`, `Switch` onCheckedChange writes to `updateAlarmForPerumahan` for OFF and ON flows; auto-off LaunchedEffect 30s.

- `app/src/main/java/com/example/panicbuttonrtdb/prensentation/components/PrioritySelection.kt` (`PriorityButton`)
  - Has internal `remember` default = "Darurat" and calls `onPrioritySelected` callback.


