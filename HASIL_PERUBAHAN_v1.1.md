Laporan Perubahan v1.1 — Panic Button App

Tanggal: 2025-11-21

🔧 1. Perbaikan Toggle OFF

Penjelasan teknis perubahan
- Sebelumnya: UI Switch (Toggle) memutuskan checked state dari LiveData `buzzerState` yang diisi dari node global `/buzzer`. Namun tindakan ON/OFF manual menulis ke `perumahan/{perumahanId}/buzzers/main`. Akibatnya OFF manual tidak selalu terlihat oleh device/ESP yang memantau `perumahan/{perumahanId}/buzzers/main`.
- Perubahan: Switch OFF sekarang memanggil `viewModel.updateAlarmForPerumahan(perumahanId, state = "off", level = "off")` sehingga menulis langsung ke node yang sama yang dibaca ESP (`perumahan/{perumahanId}/buzzers/main`). Selain itu, `ViewModel.getBuzzerState()` sekarang memprioritaskan listener pada `perumahan/{perumahanId}/buzzers/main/state` (mengambil `perumahanId` dari `UserPreferences`) sehingga LiveData `buzzerState` mencerminkan state perumahan.
- Efek: Toggle UI sekarang membaca dan menulis ke sumber data yang sama (perumahan-based buzzer node). Risiko kondisi tidak sinkron (UI stuck ON) berkurang karena UI meng-observe node yang sama yang diupdate pada OFF.

File apa yang diubah
- `app/src/main/java/com/example/panicbuttonrtdb/prensentation/components/ToggleSwitch.kt`
- `app/src/main/java/com/example/panicbuttonrtdb/viewmodel/ViewModel.kt`

Node Firebase sebelum & sesudah
- Sebelum:
  - Tulis ON/OFF: `perumahan/{perumahanId}/buzzers/main` (ON/Manual OFF menulis di sini)
  - Baca (UI): `/buzzer` (global)
- Sesudah:
  - Tulis ON/OFF: `perumahan/{perumahanId}/buzzers/main` (tidak berubah)
  - Baca (UI): `perumahan/{perumahanId}/buzzers/main/state` (diprioritaskan; fallback ke `/buzzer` jika perumahanId kosong)

🔧 2. Perbaikan Auto-Off

Penjelasan sinkronisasi node
- Sebelumnya: mekanisme auto-off bergantung pada `buzzerState` (dari `/buzzer`) dan ketika auto-off terjadi, kode menulis ke `/buzzer` dan `/buzzer_priority` (fungsi `setBuzzerState()` dan `updateBuzzerState()`), sementara ESP membaca `perumahan/{perumahanId}/buzzers/main/*`.
- Perubahan: auto-off sekarang, saat `perumahanId` tersedia, memanggil `viewModel.updateAlarmForPerumahan(perumahanId, state = "off", level = "off")`. Ini menjamin auto-off menulis ke node yang sama yang dipantau ESP.

File yang diperbarui
- `app/src/main/java/com/example/panicbuttonrtdb/prensentation/components/ToggleSwitch.kt`
- `app/src/main/java/com/example/panicbuttonrtdb/viewmodel/ViewModel.kt`

Efek ke ESP8266
- Setelah perubahan ini, auto-off akan menghasilkan update pada node `perumahan/{perumahanId}/buzzers/main` dengan payload:
  - `state: "off"`
  - `level: "off"`
- ESP yang memonitor `perumahan/{perumahanId}/buzzers/main/state` dan `.../level` akan menerima event dan bisa mematikan buzzer sesuai perintah auto-off. Ini menghilangkan gap sebelumnya di mana auto-off menulis ke node global yang tidak dipantau oleh ESP.

🔧 3. Perbaikan Login & Signup

Path database lama → baru
- Sebelum:
  - Sign up menyimpan user ke: `/users/{pushId}`
  - Login mencari user di: `perumahan/{perumahanId}/users` (menggunakan `userPreferences.getPerumahanId()`)
- Sesudah (perubahan):
  - Sign up sekarang memungkinkan menulis user ke: `perumahan/{perumahanId}/users/{pushId}` (fungsi `saveUserToFirebase` menerima parameter opsional `perumahanId` dan menggunakan targetRef sesuai).
  - Login tetap membaca di: `perumahan/{perumahanId}/users` (tidak perlu perubahan karena signup sekarang dapat menulis ke path ini).

Validasi yang diperbaiki
- Validasi duplikasi (nomor rumah dan nama) kini dilakukan pada `targetRef` yang benar: jika `perumahanId` diberikan, pemeriksaan dan penulisan dilakukan di `perumahan/{perumahanId}/users`.
- Nomor rumah masih divalidasi menggunakan `orderByChild("houseNumber").equalTo(houseNumber)` pada node perumahan sehingga validasi nomor rumah tetap bekerja.

Dampak ke login screen
- Setelah signup memilih perumahan dan berhasil, user disimpan di `perumahan/{id}/users/{pushId}` dan dapat langsung login melalui alur login yang mencari pada `perumahan/{id}/users`.
- Jika sign up tidak memilih perumahan (sebagai fallback), perilaku lama tetap ada (menulis ke `/users`) — namun alur login akan memerlukan perumahan di preferences agar berhasil.

🔧 4. Daftar File yang diubah
- `app/src/main/java/com/example/panicbuttonrtdb/viewmodel/ViewModel.kt` (perubahan: getBuzzerState prioritizes perumahan path; saveUserToFirebase supports perumahanId target)
- `app/src/main/java/com/example/panicbuttonrtdb/prensentation/components/ToggleSwitch.kt` (perubahan: manual OFF writes to perumahan path; auto-off writes perumahan path; fallback legacy behavior retained)
- `app/src/main/java/com/example/panicbuttonrtdb/prensentation/screens/SignupScreen.kt` (perubahan: added perumahan dropdown and pass selected perumahan id to saveUserToFirebase)
- Catatan: `UserPreferences.kt` tidak diubah.

🔧 5. Status Akhir
- Semua fitur utama yang direncanakan pada scope ini telah dihubungkan:
  - Toggle OFF sekarang menulis ke `perumahan/{perumahanId}/buzzers/main` sehingga ESP menerima perintah OFF.
  - Auto-off menulis ke node yang sama ketika `perumahanId` tersedia; fallback legacy di-maintain jika tidak ada perumahanId.
  - Signup sekarang dapat menulis user langsung ke `perumahan/{perumahanId}/users` sehingga login yang mencari di path tersebut akan menemukan user baru.
- Harapan operasional:
  - ESP akan menerima alarm ON/OFF dan level dari `perumahan/{perumahanId}/buzzers/main`.
  - Auto-off kini akan efektif menghentikan buzzer pada ESP karena menulis ke node yang dipantau perangkat.
  - Login/Signup sinkron: user yang mendaftar memilih perumahan akan dapat login.

Catatan teknis & pemeriksaan yang saya lakukan (tool outputs & warnings)
- Setelah perubahan, saya menjalankan pemeriksaan error pada file yang diubah.
- Tidak ditemukan error kompilasi (no errors). Terdapat beberapa *warnings* di kode Compose yang bersifat non-fatal (unused imports dan beberapa variabel state yang tidak dibaca seperti `pendingToggleState`, `isLoading` di `ToggleSwitch.kt`, dan `errorMessage` di `SignupScreen.kt`). Warnings ini tidak mencegah build tetapi bisa dibersihkan kemudian untuk kebersihan kode.
- Saya juga memperbaiki kesalahan sintaks kecil di `SignupScreen.kt` (lambda `forEach` yang salah) sehingga kompilasi kembali bersih.

Langkah verifikasi disarankan (manual & integrasi)
1. Verifikasi Toggle OFF
   - Login ke aplikasi dengan user yang mempunyai `perumahanId` set.
   - Buka halaman dengan `ToggleSwitch`.
   - Tekan ON; cek Firebase Console pada `perumahan/{id}/buzzers/main` apakah `state` dan `level` ter-update.
   - Tekan OFF manual; cek Firebase Console bahwa `state` berubah menjadi `off` dan `level` menjadi `off`.
   - Pastikan UI Switch berubah segera sesuai perubahan di DB.

2. Verifikasi Auto-Off
   - Trigger ON (dengan permission lokasi) dan pastikan `perumahan/{id}/buzzers/main/state` ter-set menjadi `on`.
   - Tunggu 30 detik; cek bahwa `perumahan/{id}/buzzers/main` berubah menjadi `{"state":"off","level":"off"}`.
   - Konfirmasi ESP menerima event dan mematikan buzzer.

3. Verifikasi Signup/Login
   - Buka Sign Up; pilih perumahan dari dropdown, isi nama/houseNumber/password.
   - Setelah sign up sukses, cek Firebase Console pada `perumahan/{selectedId}/users/{pushId}` untuk memastikan entry user baru ada.
   - Coba login dengan houseNumber/password yang sama melalui Login screen yang sudah memilih perumahan; harus berhasil.

Catatan mitigasi & next cleanup
- Bersihkan warnings (unused imports/vars) jika ingin merapikan repo.
- Pertimbangkan migrasi server-side (Cloud Functions) jika ingin menyinkronkan data legacy `/buzzer` <-> `perumahan/...` atau `/users` -> `perumahan/...` untuk data lama.

Jika kamu ingin, saya dapat:
- Menambahkan file migrasi plan (skrip JSON/instruksi) untuk memindahkan users dari `/users` ke `perumahan/{id}/users` (perlu mapping perumahan untuk setiap user).
- Membersihkan warnings kecil (unused vars/imports) di commit terpisah.

Selesai — perubahan sudah diterapkan di source code proyek:
- `ViewModel.kt`
- `ToggleSwitch.kt`
- `SignupScreen.kt`

Kalau mau, saya akan menjalankan langkah-langkah verifikasi secara otomatis di lingkungan test atau bantu susun korte test plan yang lebih detail untuk QA.


