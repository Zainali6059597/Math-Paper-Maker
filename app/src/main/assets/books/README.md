# Textbook PDF assets

Place the licensed textbook files here using these exact names:

- Punjab_Grade9_Mathematics.pdf
- Punjab_Grade10_Mathematics.pdf

The app only exposes a bundled book when books/book-manifest.json contains matching board/class/session metadata, the real page count, valid chapter page ranges, the actual SHA-256, and confirmed redistribution permission.

Copy book-manifest.example.json to book-manifest.json only after replacing every placeholder with verified details. Do not set licenseOrPermission to REDISTRIBUTION_CONFIRMED unless you have permission to redistribute that PDF inside the app. Public download availability is not redistribution permission.

If redistribution permission is not available, do not bundle the PDF in the APK; teachers can open a locally stored copy through the Android document picker instead.

## Official textbook sources (checked 17 November 2026)

The current English-medium Mathematics e-books are listed by PECTAA on its official Curriculum & Compliance page:

- PECTAA E-Books listing: https://pectaa.edu.pk/curriculum-compliance/
- Class 9 Mathematics (English Medium): https://drive.google.com/file/d/1qJF9YsdVpOKk-HzRUwftZZzINLmVkJv3/view?usp=sharing
- Class 10 Mathematics (English Medium): https://drive.google.com/file/d/1MKYcHg2WdNDIjqeD2JFKpcs_79oZ_3fq/view?usp=drive_link

The Class 9 Google Drive preview reports a 121 MB PDF. Both source PDFs exceed the current remote PDF inspection limit, so their complete page counts, chapter ranges, and SHA-256 values still need local verification. Do not fill the manifest with guessed values. Public access to these links does not establish permission to redistribute the books inside the APK. Until redistribution permission is confirmed, teachers should download the official PDFs themselves and open them with the app's **Choose PDF from device** option.
