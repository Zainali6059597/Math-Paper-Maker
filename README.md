# Ali Test Series

**Android-only, offline-first Punjab Board Mathematics paper preparation app.**

Ali Test Series lets a teacher manually choose questions from Class 9 or Class 10 Mathematics PDFs, review and reorder them, edit paper numbering, and export a printable Microsoft Word document.

## Locked product scope

- Class 9 and Class 10 Mathematics.
- PDF reader with page navigation.
- Manual text selection for text-based PDFs.
- Manual rectangle crop selection for scanned PDFs.
- Selected-question review, reorder, remove and editable numbering.
- Punjab Board paper counts, marks and section instructions.
- Genuine DOCX export: extracted text remains editable and scanned question crops are embedded as images.
- Offline core workflow; no account or cloud backend.

**Explicitly excluded:** AI question generation, automatic question selection, automatic paper generation and template builder.

## Current implementation checkpoint

Implemented in source:
- New AliTestSeriesActivity is the launcher screen.
- Class selection and local PDF opening through Android's document picker.
- Local PDF rendering and page navigation.
- PDFBox text extraction for the current page; teacher can long-press and select the exact question text using selection handles.
- Drag-to-select crop for scanned/image pages.
- Question list with remove, reorder and editable question labels.
- Class-specific Punjab Board 2026–27 MCQ chapter distribution, short-section chapter distribution, and long-question chapter pairing validation, alongside the 15 × 1-mark MCQ, 27 × 2-mark short and 10 × 4-mark long-part counts (109 offered marks; 75 attempted marks). Chapter ID is currently selected manually for each question.
- OOXML DOCX writer with editable text and embedded PNG question crops.
- Pattern rule unit tests added.

Not yet verified:
- Android Gradle/KSP/Compose build after the new app entry point.
- Actual Word document opening/printing in Microsoft Word and compatible office apps.
- Real Class 9 and Class 10 PDFs, chapter-aware slot mapping, and bundled offline book loading.
- Physical-device memory/performance and scanned crop quality.

## Repository layout

- AliTestSeriesActivity.kt — new manual workflow.
- PunjabBoardPattern.kt — offered/attempted paper structure.
- DocxPaperExporter.kt — Word document generation.
- app/src/main/assets/books/ — reserved for licensed textbook PDFs once supplied.
- docs/ALI_TEST_SERIES_SCOPE.md — locked scope and exclusions.

## Technical foundation

Kotlin, Jetpack Compose, Android PdfRenderer, PDFBox Android, local file storage and a small OOXML/ZIP DOCX writer. The app does not require an online backend.

## Textbook licensing

Only bundle PDFs when their redistribution permission is confirmed. A publicly downloadable textbook is not automatically licensed for redistribution inside an app.
