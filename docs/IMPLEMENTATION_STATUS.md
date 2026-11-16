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
- PDFBox Android extracts text from the current page. Teacher trims the extracted text manually to retain only the intended question.
- Scanned-page selection rectangle and crop-to-PNG workflow.
- Selected question list supports reorder, remove and manually editable question labels.
- Class-specific Punjab Board 2026–27 validation for MCQ chapter distribution, all three short-question chapter groups, and the five long-question chapter pairings; also enforces 15 one-mark MCQs, 27 two-mark short questions and 10 four-mark long-question parts (109 offered marks, 75 attempted marks, 150 minutes). Teachers currently enter/select each question's chapter ID manually.
- DOCX exporter writes a minimal OOXML Word package with editable text, A4 page settings, section instructions and embedded PNG question crops.
- Unit tests added for paper counts, marks, numbering uniqueness and section-choice instructions.
- Locked scope documented in docs/ALI_TEST_SERIES_SCOPE.md.

## Still required

1. Use the supplied textbook PDFs to map page ranges to chapters so the app can prefill chapter IDs instead of requiring manual chapter entry.
2. Improve selection handling for multi-column pages, math symbols and questions spanning multiple pages.
3. Persist selections/drafts across app restarts.
4. Integrate licensed Class 9 and Class 10 Mathematics PDFs under app/src/main/assets/books/, with book metadata and page-to-chapter mapping.
5. Verify scanned crop coordinates on different screen sizes and PDF page aspect ratios.
6. Verify DOCX structure and image embedding by opening exported files in Microsoft Word and another office application; inspect A4 printing.
7. Run unit tests and Android Gradle/KSP/Compose build after the new activity/exporter changes.
8. Test on a physical low-end Android phone, including offline mode, large PDFs, memory use and export.
9. Produce the APK only after the above checks pass.

## Honest build status

No APK has been compiled for this new Ali Test Series workflow. The earlier Math Paper Maker APK does not contain this implementation. New source files and tests are committed, but they have not yet been compiled or executed. Do not describe the current source as a verified working APK.

## Scope guard

No AI question generation, no automatic paper generation, no automatic question selection, and no template builder.
