# Textbook PDF assets

Place the licensed textbook files here using these exact names:

- Punjab_Grade9_Mathematics.pdf
- Punjab_Grade10_Mathematics.pdf

The app only exposes a bundled book when books/book-manifest.json contains matching board/class/session metadata, the real page count, valid chapter page ranges, the actual SHA-256, and confirmed redistribution permission.

Copy book-manifest.example.json to book-manifest.json only after replacing every placeholder with verified details. Do not set licenseOrPermission to REDISTRIBUTION_CONFIRMED unless you have permission to redistribute that PDF inside the app. Public download availability is not redistribution permission.

If redistribution permission is not available, do not bundle the PDF in the APK; teachers can open a locally stored copy through the Android document picker instead.
