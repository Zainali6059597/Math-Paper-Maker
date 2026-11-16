# Ali Test Series — source handover

**Platform:** Native Android  
**Application ID:** `com.nitroza.mathpapermaker`  
**Minimum Android:** 6.0 (API 23)  
**Current source version:** development build  
**Network:** No INTERNET permission; core workflow is offline  
**APK:** Not attached or built in this handover

## What the app does

A teacher prepares a Class 9 or Class 10 Punjab Board Mathematics paper by manually selecting questions from a local textbook PDF.

1. Choose the class and open a PDF from device storage.
2. Navigate to a page.
3. Select text from a text-based PDF, or draw a crop around a question on a scanned page.
4. Set question number, chapter, type and marks.
5. Review the paper, edit extracted text, change chapters and numbering, reorder or remove questions.
6. The working draft is stored locally and restored on the next launch.
7. Export a Word DOCX. Extracted text remains editable; scanned question crops are embedded images.

The export gate checks the 2026–27 Punjab Board structure: 15 one-mark MCQs, 27 two-mark short questions, and 10 four-mark long-question parts, with chapter distribution, section membership and long-question pairings.

## Source map

- `AliTestSeriesActivity.kt` — user-facing manual PDF selection and paper workflow.
- `PunjabBoardPattern.kt` — board counts, marks, chapter distribution and validation.
- `PaperDraftStore.kt` — local draft persistence and input validation.
- `DocxPaperExporter.kt` — editable-text and scanned-image DOCX generation.
- `app/src/test/` — domain, pattern, draft and importer tests.
- `.github/workflows/unit-tests-only.yml` — tests without APK assembly.
- `.github/workflows/android.yml` — manually triggered test + debug APK build.

## Verification

The latest test-only GitHub Actions run for commit `e3ff9412d4c65aa38d7abcb252cf64e353159113` completed successfully. The offline manifest check and unit-test task passed. The test suite has 102 tests in the current verified checkpoint.

This is not a claim that a physical-device test or Word visual inspection has passed. No APK was built in this handover.

## Build an APK

Open GitHub → Actions → **Test and Build Ali Test Series** → **Run workflow** on `main`. The workflow runs tests first, then `assembleDebug`, then uploads `maths-paper-maker-debug-apk`. Download that artifact from the completed run.

## Known limitations

- Official textbooks are not bundled. Import the correct PDF from the device.
- For imported PDFs, choose chapters manually; automatic chapter mapping requires a verified manifest and a permitted bundled textbook.
- Physical-device testing is still required for PDF rendering/cropping, draft restoration, DOCX export, memory use and airplane-mode behaviour.
- Complex Urdu shaping, embedded fonts and advanced mathematical diagram reconstruction are not guaranteed.

## Locked exclusions

No AI paper generation, automatic question selection, automatic paper generation, or template builder.
