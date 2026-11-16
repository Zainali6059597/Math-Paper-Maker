# Ali Test Series — Locked Scope

## Product
Android-only, offline-first application for preparing Punjab Board Class 9 and Class 10 Mathematics papers.

## Agreed workflow
1. Teacher chooses Class 9 or Class 10.
2. Teacher opens the corresponding Mathematics PDF inside the app.
3. Teacher manually selects each question.
4. Text-based PDFs: select/extract question text and retain editable text for Word export.
5. Scanned PDFs: draw a selection rectangle and retain the cropped original question as an image.
6. Teacher reviews, removes and reorders selected questions.
7. App organizes the manually selected questions using the relevant Punjab Board paper structure, marks and sections.
8. Teacher can edit question numbering.
9. App exports a genuine printable Microsoft Word DOCX.

## Explicitly out of scope
- AI question generation.
- Automatic question selection.
- Automatic paper generation.
- Template builder.
- Cloud backend, accounts or mandatory internet.
- Unrequested extra features.

## Locked technical choices
- Android native Kotlin + Jetpack Compose.
- Local PDF rendering and extraction.
- Room/SQLite only if persistent drafts/selection metadata are needed.
- DOCX export must support editable text and embedded cropped question images.
- No APK build until a meaningful implementation checkpoint is ready for testing.

## Development notes
- The textbook PDFs will be supplied later by the user.
- Do not invent textbook content.
- Verify redistribution permission before bundling PDFs in a distributable APK.
- Scanned-page OCR is not required for initial selection; manual crop selection is the agreed fallback.
