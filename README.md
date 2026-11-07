# Media Mover 2.0

Aplikasi Android yang otomatis menampilkan **semua gambar dan video** di perangkat, lalu memindahkannya ke folder tujuan dengan satu ketukan. Tidak perlu memilih folder sumber satu per satu.

## Fitur
- **Auto-Scan penyimpanan** lewat MediaStore (internal dan SD card), muncul langsung setelah izin media diberikan
- Filter Semua / Gambar / Video, urutkan: Terbaru, Terlama, Terbesar, Terkecil, Nama A-Z
- **Pencarian** berdasarkan nama file atau nama folder
- **Tampilan Album**: file dikelompokkan per folder asal. Ketuk untuk membuka, tekan lama untuk memilih seluruh album
- **Multi-select** dan **pindah satu klik** ke folder tujuan (nama bentrok otomatis diberi akhiran (1), (2), dst)
- **Pencari duplikat**: ukuran sama, 64 KB pertama sama, lalu SHA-256 seluruh isi sama. Bisa dihapus atau dipindahkan; pilihan otomatis menyimpan satu file tertua per grup
- **Viewer bawaan**: gambar layar penuh (cubit untuk zoom, ketuk dua kali), video diputar langsung, geser untuk file berikutnya, lalu pilih atau pindahkan dari sana
- **Ganti nama massal** dengan pola, mis. `Liburan_{n}` (token: `{n}` `{name}` `{date}` `{time}` `{folder}`)
- Dark mode mengikuti pengaturan HP, daftar dioptimalkan untuk ribuan file

## Cara kerja Auto-Scan
Android 10 ke atas membatasi akses file (Scoped Storage). Karena itu aplikasi bekerja dalam dua tingkat, dan **tidak ada yang wajib selain izin media biasa**:

1. **Mode MediaStore (default, aman)**: hanya perlu izin "Foto dan video". Seluruh gambar dan video yang diindeks sistem langsung tampil. Memindahkan file dilakukan dengan menyalin ke folder tujuan lalu menghapus file asal. Di Android 11+ penghapusan meminta **satu konfirmasi sistem untuk seluruh batch**. Di Android 9 ke bawah tanpa konfirmasi.
2. **Akses Semua File (opsional)**: aktifkan di Pengaturan aplikasi > "Aktifkan Akses Semua File" (Android 11+). Pemindahan menjadi instan (rename langsung) dan tanpa konfirmasi, serta membuka opsi **Pemindaian mendalam** yang juga menelusuri folder `.nomedia` dan tersembunyi (bisa memunculkan banyak file cache aplikasi, jadi default mati).

Aplikasi mendeteksi sendiri mode mana yang aktif dan menyesuaikan perilakunya. Jika izin dicabut, layar izin muncul lagi, tanpa crash.

### Mengapa tidak memakai Shizuku?
Shizuku **tidak digunakan**. Semua kebutuhan (scan seluruh penyimpanan, pindah, hapus, ganti nama) sudah tercakup oleh MediaStore dan Akses Semua File, tanpa dependensi tambahan, layanan AIDL, atau kewajiban menginstal aplikasi lain. Shizuku baru berguna untuk folder `Android/data` aplikasi lain, yang tidak dibutuhkan untuk mengelola foto dan video. Ini menjaga build GitHub Actions tetap sederhana dan stabil.

## Build di GitHub Actions (tidak berubah)
1. Upload/replace seluruh isi proyek ke repository (termasuk `.github/workflows/build.yml`) lalu commit ke `main` atau `master`.
2. Build berjalan otomatis (3-6 menit). Manual: tab **Actions** > **Build APK** > **Run workflow**.
3. Unduh APK dari **Actions** > run terbaru > **Artifacts** > `MediaMover-APK`, atau dari tab **Releases** > `MediaMover.apk`.
4. Install di HP (izinkan sumber tidak dikenal). Ini APK debug, cukup untuk pemakaian pribadi. Aplikasi v1 dengan package yang sama akan **diperbarui** langsung tanpa uninstall.

Versi 2.0 **tidak menambah dependency baru**. File `app/build.gradle.kts` hanya berubah di `versionCode` dan `versionName`.

## Cara pakai
1. Buka aplikasi lalu ketuk **Izinkan akses media**. Semua gambar dan video langsung tampil.
2. Ketuk file untuk membukanya di viewer. Tekan lama untuk mulai memilih.
3. Pilih folder tujuan lewat bar bawah (muncul saat ada yang dipilih) atau menu **Pengaturan**.
4. Ketuk **Pindahkan**.
5. Menu titik tiga: **Pencari duplikat**, **Pengaturan**, dan (saat memilih) **Ganti nama massal**.

## Catatan
- Folder tujuan dipilih lewat pemilih folder sistem. Android 11+ tidak mengizinkan memilih root penyimpanan, `Download`, atau `Android/data` langsung. Pilih atau buat subfolder, lalu ketuk "Gunakan folder ini".
- Setelah dipindahkan, file akan muncul di album folder tujuan, karena seluruh penyimpanan dipindai.
- Di mode MediaStore, file hasil salinan mendapat tanggal ubah baru. Di mode Akses Semua File tanggal asli dipertahankan.
- Di Android 10 (API 29) file milik aplikasi lain tidak bisa dihapus tanpa dialog per file. Aplikasi menyalin file lalu melaporkan bila file asal tidak bisa dihapus.
- Nama package `com.example.mediamover` tidak wajib diubah.
- Jika build gagal, buka run yang gagal di tab Actions lalu salin log langkah **Build debug APK**.
