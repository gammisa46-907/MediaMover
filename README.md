# Media Mover

Aplikasi Android untuk menampilkan semua gambar dan video dari satu atau beberapa folder, memilih banyak file sekaligus, lalu memindahkannya ke folder tujuan dengan satu ketukan.

**Fitur**
- Folder sumber (boleh lebih dari satu) dan folder tujuan, tersimpan otomatis
- Filter: Semua / Gambar / Video
- Urutkan: Terbaru, Terlama, Terbesar, Terkecil, Nama A-Z
- Pilih banyak file (tekan lama untuk mulai memilih), lalu satu tombol "Pindahkan"
- Nama file yang bentrok otomatis diberi akhiran (1), (2), dst
- Dark mode mengikuti pengaturan HP
- Tanpa izin storage khusus: memakai Storage Access Framework (aman untuk Play Store)

## 1. Upload ke GitHub
1. Buat repository baru di github.com.
2. Ekstrak ZIP, lalu upload **semua isi folder** ke repository (Add file > Upload files). Drag and drop folder lebih aman daripada memilih file satu per satu.
3. Pastikan folder tersembunyi `.github/workflows/build.yml` ikut terunggah. Jika tidak: Add file > Create new file, ketik nama `.github/workflows/build.yml`, lalu tempel isi file tersebut.
4. Commit ke branch `main` (atau `master`).

## 2. Build
Build berjalan otomatis setiap kali ada push ke `main`/`master`. Untuk menjalankan manual: tab **Actions** > **Build APK** > **Run workflow**. Proses sekitar 3-6 menit.

## 3. Download APK
- Tab **Actions** > pilih run terbaru yang berhasil > bagian **Artifacts** > `MediaMover-APK` (berupa ZIP berisi `MediaMover.apk`), atau
- Tab **Releases** di halaman utama repository > `Media Mover build N` > `MediaMover.apk`.

## 4. Install di HP
1. Kirim/unduh `MediaMover.apk` ke HP lalu buka file-nya.
2. Jika diminta, izinkan "Install aplikasi dari sumber ini" untuk browser/file manager yang dipakai.
3. Ketuk Install. Ini APK debug (ditandatangani kunci debug), cukup untuk pemakaian pribadi.

## Cara pakai
1. Saat pertama dibuka, tambahkan folder sumber, lalu ketuk **Gunakan folder ini** di layar pemilih folder.
2. Pilih folder tujuan lewat menu pengaturan (ikon roda gigi) atau saat tombol pindah pertama kali muncul.
3. Tekan lama sebuah gambar/video untuk mulai memilih, ketuk file lain untuk menambah, lalu ketuk **Pindahkan**.

## Catatan
- Android 11 ke atas tidak mengizinkan memilih root penyimpanan, folder `Download`, atau `Android/data` langsung. Pilih subfolder (mis. `DCIM/Camera`, `Pictures`, `Movies`).
- Pemindahan di penyimpanan yang sama instan. Jika beda penyimpanan (mis. internal ke SD card), file disalin lalu file asal dihapus; file asal tidak dihapus jika penyalinan gagal.
- Nama package `com.example.mediamover` **tidak wajib diubah**. Jika ingin mengganti, ubah `namespace` dan `applicationId` di `app/build.gradle.kts`, serta nama package di semua file Kotlin dan folder `app/src/main/java/...`.
- Jika build gagal, buka run yang gagal di tab Actions, lalu salin log langkah **Build debug APK**.
