# Ali Test Series — release readiness

## Product delivered in source

Native Android app for Class 9 and Class 10 Punjab Board Mathematics. The teacher manually selects questions from a textbook PDF and exports a printable DOCX.

- Open a PDF from device storage using Android's document picker.
- Navigate pages; extract selectable PDF text or draw a crop around scanned questions.
- Edit question text, paper numbering, chapter, type and marks.
- Review, reorder and remove questions.
- Draft is saved locally and restored after app restart.
- Enforce Punjab Board 2026–27 question counts, marks, chapter distribution and long-question pairings before export.
- Export DOCX with editable extracted text and embedded PNG crops for scanned questions.
- Offline-only app manifest; no account or cloud service.

No AI generation, automatic question selection, automatic paper generation or template builder is included.

## Verified in GitHub Actions

Latest source commit: `e3ff9412d4c65aa38d7abcb252cf64e353159113`

- Unit-test workflow completed successfully.
- 102 unit tests passed in the preceding verified run; latest run also completed successfully.
- Offline manifest guard passed.
- `testDebugUnitTest` compiles the Android app's Kotlin source as part of the test task.
- No APK was assembled as part of the test-only workflow.

## Remaining release acceptance checks

These require a real Android device and actual textbook files; they cannot honestly be marked complete from source review alone.

1. Run **Actions → Test and Build Ali Test Series → Run workflow**. It runs unit tests, assembles the debug APK and uploads `maths-paper-maker-debug-apk`.
2. Install that APK on Android 9 or newer.
3. Test in airplane mode: launch, open a local PDF, navigate pages, select text, crop a scanned question, add/edit/reorder/remove questions, close and reopen the app, and confirm the draft restores.
4. Build a complete valid Class 9 paper and a complete valid Class 10 paper; confirm invalid counts, marks, chapter distributions and pairings block export.
5. Export both DOCX variants. Open them in Microsoft Word or another office app; inspect text editing, embedded scanned images, section instructions, page breaks and print layout.
6. Test a large textbook PDF and a low-memory phone for import, rendering, cancellation and cache cleanup.
7. Confirm the official textbook edition and manually verify chapter labels against the PDF. Imported PDFs are not bundled; teachers choose them from device storage.

## Current limitations

- The actual Class 9 and Class 10 textbooks are not bundled. This avoids distributing copyrighted material without confirmed permission and avoids shipping unverified page mappings.
- Chapter labels for imported PDFs are selected manually; automatic chapter mapping is only enabled when a verified bundled-book manifest and licensed PDF exist.
- DOCX output has not yet been visually inspected in Word on a physical device.
- Urdu shaping, embedded-font guarantees, complex equation layout and diagram reconstruction are not claimed.
- Unit tests are not a substitute for Android installation and device acceptance testing.

## Build the APK yourself

1. Open the repository's **Actions** tab.
2. Select **Test and Build Ali Test Series**.
3. Tap **Run workflow** on the `main` branch.
4. Wait for the workflow to pass.
5. Download the `maths-paper-maker-debug-apk` artifact from the completed run.

The workflow builds only after unit tests pass. No signing key or store account is required for the debug APK.
