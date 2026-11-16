# Ali Test Series — implementation status

Last updated: 16 November 2026  
Repository: Zainali6059597/Math-Paper-Maker  
Status: active development; not a verified release.

## Implemented in source

- App display name and launcher entry point changed to **Ali Test Series**.
- New Android-native Compose workflow launched from AliTestSeriesActivity; previous Math Paper Maker activity is no longer the launcher.
- Class 9 / Class 10 choice.
- Android document picker opens a locally stored Mathematics PDF; selected file is copied into app cache for stable page rendering.
- Android PdfRenderer displays PDF pages with previous/next navigation.
- PDFBox Android extracts text from the current page; Compose text-selection handles let the teacher select the exact question text.
- Scanned-page selection rectangle and crop-to-PNG workflow.
- Selected question list supports reorder, remove and manually editable question labels. Current paper metadata and selections are saved locally across app restarts; scanned crops are stored in app-private files.
- Class-specific Punjab Board 2026–27 validation for MCQ chapter distribution, all three short-question chapter groups, and the five long-question chapter pairings; also enforces 15 one-mark MCQs, 27 two-mark short questions and 10 four-mark long-question parts (109 offered marks, 75 attempted marks, 150 minutes). Teachers choose chapter IDs from a class-specific picker for external PDFs; a bundled PDF can prefill chapter IDs from its verified page-range manifest.
- DOCX exporter writes a minimal OOXML Word package with editable text, A4 page settings, section instructions and embedded PNG question crops.
- Unit tests added for paper counts, marks, numbering uniqueness, section membership independent of manual labels, and class-specific chapter limits.
- Locked scope documented in docs/ALI_TEST_SERIES_SCOPE.md.

## Still required

1. Supply the Class 9 and Class 10 PDFs, confirm redistribution rights before bundling, and fill the real SHA-256, page count and chapter page ranges in the textbook manifest.
2. Improve selection handling for multi-column pages, math symbols and questions spanning multiple pages.
3. Verify scanned crop coordinates on different screen sizes and PDF page aspect ratios; cancelled crops are now cleaned up.
4. Verify DOCX structure and image embedding by opening exported files in Microsoft Word and another office application; inspect A4 printing. Duplicate identical questions now retain their own corresponding crop images.
5. Run unit tests and Android Gradle/KSP/Compose build after the new activity/exporter changes.
6. Test on a physical low-end Android phone, including offline mode, large PDFs, memory use and export.
7. Produce the APK only after the above checks pass.

## Honest build status

No APK has been compiled for this new Ali Test Series workflow. The earlier Math Paper Maker APK does not contain this implementation. New source files and tests are committed, but they have not yet been compiled or executed. Do not describe the current source as a verified working APK.

## Scope guard

No AI question generation, no automatic paper generation, no automatic question selection, and no template builder.
