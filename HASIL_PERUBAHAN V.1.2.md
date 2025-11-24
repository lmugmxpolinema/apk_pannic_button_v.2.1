# HASIL PERUBAHAN v1.2

## Ringkasan
Perubahan ini memperbaiki penuh alur Panic Button agar selaras dengan struktur Firebase baru dan pembacaan ESP8266, serta memastikan data pemantauan selalu memakai identitas user dari sesi login saat ini.

## Detail Perubahan

### 1. `ViewModel.kt`
- Menambahkan fungsi `getPerumahanId()` sebagai helper tunggal untuk mengambil `perumahan_id` dari `UserPreferences`.
- Mengubah `saveMonitorData()` sehingga **selalu** mengambil `name` dan `houseNumber` terbaru dari `UserPreferences` sebelum menyimpan ke `/monitor`. Ini menjamin dashboard web menampilkan user yang benar.
- Menulis ulang `getBuzzerState()` agar hanya menjadi listener pada path:
  - `/perumahan/{perumahanId}/buzzers/main/state`.
  Tidak lagi memakai node lama `/buzzer` dan fallback terkait.
- Menjadikan `updateAlarmForPerumahan(perumahanId, state, level)` sebagai satu-satunya fungsi penulisan alarm ke path:
  - `/perumahan/{perumahanId}/buzzers/main`.
- Merefaktor `setBuzzerState()` untuk mengarah ke `updateAlarmForPerumahan()` dengan level yang sesuai ("biasa" saat `on`, "off" saat `off`).
- Menulis ulang `updateBuzzerState(isOn, priority, level)` agar:
  - Saat `isOn = true` menulis `state = "on"` dan `level` ("biasa" / "penting" / "darurat") ke `updateAlarmForPerumahan`.
  - Saat `isOn = false` menulis `state = "off"` dan `level = "off"`.
  - Seluruh akses ke node lama `/buzzer` dan `/buzzer_priority` dihilangkan.

### 2. `ToggleSwitch.kt`
- Menghubungkan handler ON pada `Switch` ke alur baru: saat ON, dialog konfirmasi akan memanggil `viewModel.updateBuzzerState(isOn = true, priority, level)` setelah lokasi dan data monitor tersimpan.
- Menghubungkan handler OFF pada `Switch` untuk memanggil `viewModel.updateBuzzerState(isOn = false)` sehingga:
  - `state = "off"` dan `level = "off"` tersinkron dengan ESP8266.
- Memperbaiki auto-OFF 30 detik dengan memanggil fungsi yang sama:
  - `viewModel.updateBuzzerState(isOn = false)`.
- Memastikan tidak ada penulisan langsung ke node lama `/buzzer` dan `/buzzer_priority` dari komponen ini.

## Dampak terhadap ESP8266
- ESP8266 kini hanya perlu mendengarkan path:
  - `/perumahan/{perumahanId}/buzzers/main/state`
  - `/perumahan/{perumahanId}/buzzers/main/level`
- Nilai yang dijamin:
  - Saat tombol ON: `state = "on"`, `level = "biasa" | "penting" | "darurat"`.
  - Saat tombol OFF (manual maupun auto 30 detik): `state = "off"`, `level = "off"`.

## Catatan Versi
- **Tidak ada perubahan** pada struktur Firebase maupun kode ESP8266.
- Perubahan hanya menyentuh kode Android (ViewModel & komponen UI Toggle).

