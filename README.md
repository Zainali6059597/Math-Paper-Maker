# Ali Test Series

**Native Android · Offline-first · Class 9 & 10 Punjab Board Mathematics**

Ali Test Series lets a teacher manually select questions from a local Mathematics textbook PDF, arrange them into a Punjab Board paper, and export a printable Word document.

## What is implemented

- Choose Class 9 or Class 10 and open a PDF from device storage.
- Open the official PECTAA-linked Class 9/10 textbook source from the app; download it in the external browser, then import it for offline use.
- Navigate PDF pages.
- Select text from text-based PDFs or draw a crop around a scanned question.
- Edit question text, numbering, chapter, question type and marks.
- Review, reorder and remove selected questions.
- Automatically save the working paper locally and restore it on the next launch.
- Validate Punjab Board 2026–27 counts, marks, chapter distribution, section membership and long-question pairings.
- Use the bundled offline question bank: 47 Grade 9 questions and 52 original Grade 10 practice questions (15 MCQs, 27 short, 10 long), with answers.
- Export DOCX with editable extracted text and embedded PNGs for scanned questions.
- No account, cloud backend or INTERNET permission.

## Locked scope

Manual question selection only. No AI question generation, automatic question selection, automatic paper generation or template builder.

## Repository layout

- `app/src/main/java/com/nitroza/mathpapermaker/AliTestSeriesActivity.kt` — primary app workflow and launcher.
- `paper/PunjabBoardPattern.kt` — paper structure and validation.
- `paper/PaperDraftStore.kt` — local draft save/restore.
- `docx/DocxPaperExporter.kt` — Word document export.
- `app/src/test/` — unit tests.
- `.github/workflows/unit-tests-only.yml` — CI tests without APK assembly.
- `.github/workflows/android.yml` — manual test + APK build workflow.

## Current verification

GitHub Actions run [#328](https://github.com/Zainali6059597/Math-Paper-Maker/actions/runs/44465311346) for commit `72dad0c9073746342b9fd6f4c01c0d03e84e65f9` passed: **145 unit tests, offline-only manifest verification, and debug APK assembly**. The APK artifact was uploaded successfully (APK size: 63,994,540 bytes).

This verifies compilation and automated tests, not physical-device acceptance. PDF rendering/cropping, large-file memory use, draft restoration, airplane-mode behaviour and DOCX visual layout still need hands-on testing on an Android phone and an office app.

## Build the debug APK

1. Open the repository's **Actions** tab.
2. Select **Test and Build Ali Test Series**.
3. Choose **Run workflow** on `main`.
4. Wait for tests and APK assembly to pass.
5. Download the `maths-paper-maker-debug-apk` artifact.

The workflow runs tests before assembling the APK. No signing key is needed for a debug APK.

## Textbook files and licensing

The official Class 9 and Class 10 PDFs are not bundled. Open the correct textbook PDF from the phone. Chapter labels are selected manually for imported PDFs. Automatic page-to-chapter mapping is enabled only when a licensed textbook and verified page manifest are supplied. Public availability does not automatically grant redistribution rights.

## Known limitations before release

- Real-phone testing is still needed for PDF rendering/cropping, large-file memory use, draft restoration and airplane-mode behaviour.
- DOCX must be opened and visually inspected in Word or a compatible office app.
- Complex Urdu shaping, embedded-font guarantees and advanced equation/diagram reconstruction are not guaranteed.
