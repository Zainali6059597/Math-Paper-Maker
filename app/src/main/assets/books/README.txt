BUNDLED MATHEMATICS BOOKS

Place legally distributable textbook PDF files directly in this folder:
app/src/main/assets/books/

The Android app reads these files locally from its APK. No network access is used.

Important:
- Use text-based PDFs where possible. Image-only/scanned pages are detected and marked as needing OCR.
- Keep the original PDF page numbers; generated question references must point back to the book and page.
- Do not assume extracted paragraphs are exam questions. A separate question-identification and review stage must turn textbook material into candidate questions.
- Copy book-manifest.example.json to book-manifest.json and fill in the exact board, grade, stream, subject, session, edition, language and source details. Chapter entries include chapterId, title, startPage and endPage. The app only exposes manifest chapters when the referenced PDF is present in this same folder and the selected paper identity matches exactly.
- Only include PDFs you are allowed to redistribute inside the app.

No textbook PDFs or active book-manifest.json have been bundled yet. The example manifest is documentation only and is ignored by the app.
