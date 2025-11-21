# ANALISA BUG v1.2

Dokumen ini adalah laporan diagnostik menyeluruh mengenai beberapa bug yang teramati di aplikasi Panic Button (kode Kotlin / Compose / ViewModel). Analisis berfokus pada alur data user, prioritas/level, penggunaan ViewModel, dan penulisan ke Firebase.

---

## 1. Ringkasan Bug

Bug utama yang dianalisis:

- BUG 1 — Wrong user shown in dashboard (User 3 login, but User 1 sent data)
  - Gejala: Setelah login sebagai user X, dashboard menampilkan data user yang berbeda (mis. user Y) atau riwayat menampilkan entri dari user lain.

- BUG 2 — Priority mismatch (selected “Biasa” but Firebase receives “Darurat”)
  - Gejala: Saat pengguna memilih prioritas "Biasa" ketika mengirim panic, Firebase / alarm menerima "Darurat".

- BUG 3 — Toggle OFF not working
  - Gejala: Mematikan toggle (OFF) tidak mematikan buzzer/ESP; state tetap on di device.

- BUG 4 — Auto-off (30 seconds) not synced with new alarm system
  - Gejala: Auto-off setelah 30 detik menulis ke path yang tidak dibaca oleh ESP atau tidak mematikan buzzer pada device baru.

Inti penyebab umum yang ditemukan: ketidakkonsistenan sumber data (perumahan vs global), state composable yang tidak di-hoist/survive lifecycle (priority), pembuatan beberapa instance ViewModel di beberapa composable, dan dual-path Firebase (legacy vs perumahan) tanpa mekanisme sinkronisasi atau re-attach listener.

---

## 2. Analisa Detail per File

Saya membaca dan melacak beberapa file kunci berikut; di bawah ini ringkasan temuan per file dan baris relevan.

- `app/src/main/java/.../data/UserPreferences.kt`
  - Menyimpan: `perumahan_id`, `perumahan_name`, `user_name`, `house_number`, `is_logged_in`, `isAdminLoggedIn`.
  - Implementasi SharedPreferences (synchronous). Semua instance `UserPreferences(context)` berbagi backing prefs.

- `app/src/main/java/.../viewmodel/ViewModel.kt`
  - Fields: `var currentUserName by mutableStateOf("")`, `var currentUserHouseNumber by mutableStateOf("")`.
  - init: membaca `currentUserName` dan `currentUserHouseNumber` dari `userPreferences` pada saat ViewModel dibuat.
  - `validateLogin(...)`: baca `perumahanId = userPreferences.getPerumahanId()` lalu query ke `perumahan/{perumahanId}/users`; saat sukses: `userPreferences.saveUserInfo(...)` dan set `currentUserName`/`currentUserHouseNumber` pada instance ViewModel.
  - `saveMonitorData(...)` menulis map yang mengambil `currentUserName`/`currentUserHouseNumber` dari variabel ViewModel ketika dipanggil.
  - Buzzer logic:
    - `getBuzzerState()` (dipanggil di `init`) memilih listener berdasarkan `userPreferences.getPerumahanId()` saat inisialisasi: jika non-empty attach ke `perumahan/{id}/buzzers/main` (membaca child `state`) else fallback ke global `/buzzer`.
    - `updateAlarmForPerumahan(perumahanId, state, level)` menulis ke `perumahan/{perumahanId}/buzzers/main`.
    - Legacy writers: `setBuzzerState(state)` => `/buzzer`, `updateBuzzerState(...)` => `/buzzer_priority`.
  - Observasi: `getBuzzerState()` hanya membaca `perumahanId` saat `init` — tidak ada re-attach ketika perumahanId berubah.

- `app/src/main/java/.../viewmodel/ViewModelFactory.kt`
  - Factory membuat `ViewModel(context)`. Memungkinkan pembuatan instance ViewModel di setiap composable yang memanggil `viewModel(factory = ...)`.

- `app/src/main/java/.../navigation/MainApp.kt`
  - `MainApp()` membuat satu instance `viewModel` via `viewModel(factory = ViewModelFactory(context))` dan passing ke banyak screen (contoh `LoginScreen`, `DashboardUserScreen`, dsb.). Start destination juga tergantung `viewModel.isUserLoggedIn()`.
  - Observasi: MainApp membuat shared ViewModel; namun beberapa screens menggunakan default param `viewModel = viewModel(factory = ViewModelFactory(LocalContext.current))` dan bisa membuat instance tersendiri bila MainApp tidak mengoper instance itu.

- `app/src/main/java/.../prensentation/screens/LoginScreen.kt` (attachment & dibaca)
  - Signature: `viewModel: ViewModel = viewModel(factory = ViewModelFactory(LocalContext.current))` tetapi MainApp memanggil `LoginScreen(viewModel = viewModel, ...)` sehingga login biasanya menggunakan `MainApp`'s shared instance.
  - Proses login: sebelum `validateLogin`, screen memanggil `UserPreferences(context).savePerumahan(selectedPerumahan!!.id, ...)` untuk menyimpan perumahan ke SharedPreferences.
  - `validateLogin(...)` di ViewModel membaca perumahanId dari `UserPreferences` lalu query `perumahan/{id}/users`.

- `app/src/main/java/.../prensentation/screens/SignUpScreen.kt`
  - Signature juga mendeklarasikan `viewModel = viewModel(factory = ...)` tetapi MainApp memanggil `SignUpScreen` tanpa parameter `viewModel`. Oleh karena itu `SignUpScreen` membuat instance `ViewModel` tersendiri: CONCRETE evidence terjadinya multiple ViewModel instances.

- `app/src/main/java/.../prensentation/screens/DashboardUserScreen.kt`
  - Menerima `viewModel` dari MainApp.
  - Namun untuk memuat profil user, screen membaca `houseNumber` langsung dari SharedPreferences (`sharedPref.getString("house_number", "")`) lalu query ke global `FirebaseDatabase.getReference("users")` untuk profil — **bukan** ke `perumahan/{id}/users`.
  - UI menampilkan `viewModel.currentUserName` dan `viewModel.currentUserHouseNumber` untuk label "Selamat Datang".
  - Observasi: Dashboard mengkombinasikan dua sumber: `viewModel` (yang login lewat perumahan path) dan global `users` (yang bisa berbeda) → penyebab utama BUG 1.

- `app/src/main/java/.../prensentation/components/ToggleSwitch.kt`
  - Local state: `var selectedPriority by remember { mutableStateOf("Darurat") }` dan `var selectedLevel by remember { mutableStateOf("Darurat") }`.
  - Menggunakan `PriorityButton(onPrioritySelected = { priority, level -> selectedPriority = priority; selectedLevel = level })`.
  - `PriorityButton` sendiri memiliki internal `remember` state default "Darurat" (lihat `PrioritySelection.kt`) — ada duplikasi sumber kebenaran.
  - Saat user konfirmasi: `locationPermissionLauncher.launch(...)`. Di callback `onResult`, code memanggil `viewModel.saveMonitorData(..., priority = selectedPriority, ...)` dan `viewModel.updateAlarmForPerumahan(perumahanId, state = "on", level = selectedLevel.lowercase())`.
  - `Switch` OFF branch: langsung memanggil `viewModel.updateAlarmForPerumahan(perumahanId, "off", "off")` tanpa verifikasi perumahanId.
  - Auto-off: ada `LaunchedEffect(key1 = buzzerState)` yang `delay(30000)` lalu jika `perumahanId.isNotEmpty()` writes to perumahan path else fallback ke legacy global nodes.

- `app/src/main/java/.../prensentation/components/PrioritySelection.kt` (`PriorityButton`)
  - `PriorityButton` memiliki lokal `var selectedPriority by remember { mutableStateOf("Darurat") }` dan memanggil `onPrioritySelected` setiap click.
  - Tidak ada parameter `selectedPriority` pada komponen agar parent dapat mengontrol state.

---

## 3. Alur Data (User, Priority, Level)

Ringkas alur data end-to-end penting:

- Login / Perumahan selection:
  1. User pilih perumahan pada `LoginScreen` → `UserPreferences(context).savePerumahan(selectedPerumahan!!.id, ...)` (SharedPreferences ditulis).
  2. `viewModel.validateLogin(...)` membaca `userPreferences.getPerumahanId()` dan query `perumahan/{id}/users`.
  3. Jika valid: `userPreferences.saveUserInfo(user.name, user.houseNumber)` dan instance ViewModel set `currentUserName`/`currentUserHouseNumber`.

- Menampilkan Dashboard:
  - `DashboardUserScreen` menampilkan `viewModel.currentUserName`/`currentUserHouseNumber`.
  - Namun profil tambahan diambil dengan QUERY ke global `/users` berdasarkan `houseNumber` yang dibaca langsung dari SharedPreferences (bukan perumahan path).

- Mengirim panic (Toggle -> send):
  1. User memilih priority via `PriorityButton` (internal state) yang memanggil callback ke `ToggleSwitch` untuk meng-set parent `selectedPriority`.
  2. User tekan "Kirim", app meminta permission lokasi (`locationPermissionLauncher.launch`). Pada `onResult` (callback), dipanggil:
     - `viewModel.saveMonitorData(message, priority = selectedPriority, status = "Proses", latitude = lat, longitude = lon)` → `saveMonitorData` menggunakan `currentUserName` & `currentUserHouseNumber` dari ViewModel instance saat itu.
     - `viewModel.updateAlarmForPerumahan(perumahanId, state = "on", level = selectedLevel.lowercase())` → menulis ke `perumahan/{perumahanId}/buzzers/main`.
  3. Auto-off: `LaunchedEffect(buzzerState == "on")` delay 30 detik lalu menulis `off` ke perumahan path (jika perumahanId tersedia) else fallback ke legacy nodes.

---

## 4. Titik Penyebab Bug (detil teknis)

BUG 1 — Wrong user shown in dashboard
- Penyebab utama:
  1. Inconsistency path: login menggunakan `perumahan/{id}/users`, dashboard profile fetch menggunakan global `/users`.
  2. Multiple ViewModel instances: `SignUpScreen` (dan beberapa composable) menggunakan `viewModel(factory = ViewModelFactory(...))` sendiri sehingga dapat menimbulkan instance berbeda; jika satu instance menyimpan `user_name` ke prefs tapi screen lain menggunakan instance lain atau membaca dari `/users`, tampilan bisa berbeda.
  3. Mixed sources: Dashboard menggunakan data dari `viewModel` sekaligus query global `/users` & SharedPreferences — membuka peluang mismatch.

BUG 2 — Priority mismatch (Biasa vs Darurat)
- Penyebab utama:
  1. `PriorityButton` stateful internal default = "Darurat"; parent `ToggleSwitch` juga menyimpan `selectedPriority`. Tidak ada single source-of-truth.
  2. Asynchronous permission flow: Firebase write terjadi di permission `onResult` callback; jika UI lifecycle/recomposition terjadi selama permission dialog, parent/child composable state bisa reset ke default "Darurat" saat callback membaca nilai.

BUG 3 — Toggle OFF not working
- Penyebab utama:
  1. Listener attachment: `ViewModel.getBuzzerState()` attach listener pada `init` berdasarkan `userPreferences.getPerumahanId()` pada waktu itu; jika perumahanId belum terset atau ViewModel dibuat sebelum login, listener bisa menempel ke legacy `/buzzer` dan tidak pindah setelah login.
  2. OFF path: `Switch` OFF hanya memanggil `updateAlarmForPerumahan(perumahanId, "off","off")` tanpa verifikasi, sehingga jika `perumahanId` kosong atau tidak sesuai device, tidak mematikan hardware.
  3. Auto-off fallback dan manual OFF menulis ke path yang berbeda di beberapa kondisi → inkonsistensi.

BUG 4 — Auto-off (30s) not synced
- Penyebab utama:
  1. Dual-path logic: auto-off hanya menulis ke new path jika `perumahanId.isNotEmpty()` saat 30s timeout; jika `perumahanId` tidak tersedia atau listener awalnya di-attach ke legacy path, ESP yang membaca new path tidak akan mendapat update auto-off.
  2. Tidak ada mekanisme re-attach listener atau memastikan single canonical node untuk both read & write.

---

## 5. Rekomendasi Perbaikan (tanpa mengubah kode di repo ini, hanya rekomendasi)

Catatan: berikut adalah langkah konseptual dan prioritas perbaikan. Saya tidak mengubah kode — rekomendasi ini untuk developer tim agar diterapkan.

Prioritas tinggi (harus segera):
1. Konsolidasikan data user / profil ke satu path
   - Pastikan `DashboardUserScreen` memuat profil dari `perumahan/{perumahanId}/users` (atau pastikan signup/login menulis juga ke global `/users`) sehingga semua screen memakai sumber yang sama.
   - Menghapus query ke global `/users` dari Dashboard jika aplikasi sekarang memakai struktur perumahan.

2. Hindari multiple ViewModel instances untuk state kritis
   - Pastikan screens menerima/shared `ViewModel` yang sama yang dibuat di `MainApp()` (pass `viewModel` parameter ke semua screens termasuk `SignUpScreen`) atau gunakan per-screen ViewModel secara konsisten dengan re-synchronization ke central store.
   - Konsekuensi langsung: `currentUserName`/`currentUserHouseNumber` hanya dipegang dan di-update di satu instance yang authoritative.

3. Reattach buzzer listener setelah perumahanId berubah
   - Di `ViewModel`, ubah mekanisme `getBuzzerState()` sehingga jika `userPreferences.getPerumahanId()` berubah (mis. saat login), listener lama dilepas dan listener baru terpasang ke `perumahan/{id}/buzzers/main`.
   - Alternatif: panggil fungsi re-attach secara eksplisit setelah login sukses.

Prioritas menengah (perbaiki race & state loss):
4. Hoist pilihan prioritas ke ViewModel atau make `PriorityButton` controlled
   - Buat `selectedPriority`/`selectedLevel` disimpan di `ViewModel` atau parent `ToggleSwitch` yang tidak akan hilang selama permission prompt. Ubah `PriorityButton` agar menerima `selectedPriority` dan `onPrioritySelected` sehingga tidak memiliki internal default state yang independen.

5. Simpan snapshot data sebelum permission prompt
   - Saat user menekan "Kirim", simpan message/priority/level ke ViewModel atau persistent temp storage sebelum memanggil permission launcher. Di callback permission, baca snapshot tersebut untuk menulis ke Firebase — menghindari state reset akibat lifecycle change.

6. Konsolidasikan ON/OFF write ke canonical path
   - Tentukan canonical path yang dibaca oleh ESP (mis: `perumahan/{id}/buzzers/main/state`). Pastikan semua operasi ON/OFF/auto-off dan priority updates menulis ke path tersebut (dan jika perlu juga ke legacy nodes untuk backward compat).

7. Defensive checks
   - Sebelum memanggil `updateAlarmForPerumahan`, verifikasi `perumahanId` non-empty. Jika kosong, beri notifikasi kepada user atau fallback yang tepat (atau tulis ke legacy nodes hanya jika memang intended).

Logging & debug (cepat):
- Tambahkan logging pada titik-titik berikut untuk memverifikasi runtime behavior: saat login sukses (nama/houseNumber/perumahanId), saat `saveMonitorData` dipanggil (log name/house/priority), saat `updateAlarmForPerumahan` dipanggil (log perumahanId/state/level), dan saat `getBuzzerState` attach listener (log path).

Usability tambahan:
- Tampilkan prioritas saat konfirmasi dialog dan disable/lock UI selama permission prompt sehingga state tidak bisa berubah diam-diam.

---

Jika Anda mau, saya dapat:
- Menyusun patch terperinci (daftar file + potongan kode yang disarankan) agar developer Anda menerapkan perubahan, atau
- Menyiapkan PR implementasi rekomendasi (saya tidak akan menjalankan/commit PR tanpa konfirmasi Anda).


