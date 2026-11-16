# Paper generation rules

These rules are product invariants, not optional generation preferences.

## 1. One selected board pattern per paper

A generated paper is pinned to exactly one `PaperPatternEntity` matching the selected identity:

- board
- grade
- stream
- subject
- session

The app must never combine slots, choice groups, marks, chapter allocations, or duration from different board patterns. A missing exact match blocks generation. There is no cross-board fallback.

The pattern record is the source of truth for:
- total attempted marks and duration
- question types and per-question marks
- fixed slots and section order
- offered/attempted choice counts
- required paired parts
- chapter/SLO restrictions and any declared quotas

A new board pattern is a separate versioned content package. Do not copy another board's template and relabel it.

## 2. User-selected chapters

Before generation, show the chapters present in the local question bank for the exact selected identity. The user can select all, none, or any subset.

Generation candidates must satisfy BOTH:
- the exact board/grade/stream/subject/session identity and the chosen pattern's eligibility rules
- membership in the user's selected chapter set

The selected pattern still controls its original slots and marks. Chapter selection narrows the candidate pool; it does not rewrite the paper template. If a selected chapter subset cannot fill every required slot, block generation and explain which requirements cannot be met. Never borrow questions from an unselected chapter or another board.

## 3. Randomness and weighting

Random selection must happen inside the eligible candidate pool for the pinned pattern and selected chapters. Any probability weighting (importance, recurrence, learning outcomes, or teacher-provided weights) must be explicit, explainable and local. Weighting can influence which matching question is chosen; it cannot change marks, question type, choice count, board identity or the pattern's structure.

## 4. Review before PDF

Generation creates an editable draft first. The user can inspect, edit, replace, remove or reorder questions. The final A4 PDF is produced only after the user confirms the draft. A modified paper must be clearly identified as custom if it no longer matches the pinned template.

## 5. Offline books

Textbook PDFs belong under `app/src/main/assets/books/` and are read on-device. Extracted text must retain book and page references. Extracted paragraphs are source material, not automatically approved exam questions. Question candidates need chapter/topic mapping and a reviewable provenance reference before they enter generation.
