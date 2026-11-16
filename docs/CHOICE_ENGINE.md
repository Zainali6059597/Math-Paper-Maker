# Offered vs attempted choice rules

The domain layer now has a separate `ChoicePatternEngine` for common board-paper choice structures.

## Rule shape

A `ChoiceGroupRule` defines:
- `offeredCount`: number of questions printed in the paper;
- `attemptCount`: number of questions the student must attempt;
- `marksPerQuestion`: marks attached to each offered question;
- `slots`: exactly one constraint slot for every offered position.

For example, 12 short questions offered, any 9 attempted, each worth 2 marks:
- offered questions: 12;
- offered marks: 24;
- attempted questions: 9;
- attempted marks: 18.

The paper's official total is calculated from attempted marks, not the sum of all printed questions. The engine rejects a pattern whose declared attempted total does not match its choice rules.

## Safety properties

- Board, grade, stream, subject and session must match exactly.
- One question cannot fill two offered positions.
- Official mode requires verified, summative questions.
- Conflicted content is never eligible.
- No fallback to a different stream, board, or session.
- Invalid choice counts and duplicate slot/group IDs are rejected before selection.

## Current limitations

The equal-mark choice engine is integrated with Room/import/UI/PDF. Required paired bundles with independently marked parts are supported separately through schema v5 and Room v8. Every question in an optional choice group must still carry equal marks. Mixed-mark optional choices, nested alternatives, and percentage-weighted ToS optimization remain unsupported. Such patterns must remain blocked until those rule types are implemented and tested. Do not infer attempt counts from a model paper's offered marks or curriculum weight percentages.

## Safe replacement lookup

`QuestionReplacementEngine` provides compatible candidates for a particular offered slot. It excludes the question currently occupying that slot, any question already used elsewhere in the paper, mismatched identity/slot metadata, conflicted content, and unverified or non-summative questions in official mode. The engine returns options; it does not silently change the paper. A teacher-facing picker and draft persistence still need to be connected in the Android UI.

## Current app integration
- Schema v2 persists choice groups in Room and links every offered slot to a group.
- Import validation enforces group membership, offered count, equal marks within a group, and attempted total before import.
- Schema v1 remains supported for required-slot patterns.
- Generation uses the choice-aware engine when groups exist; PDF export prints the generated attempt instruction (for example, “Attempt any 9 of 12”).
- PDF header reports the attempted maximum marks, while the editor separately reports offered marks and attempted marks.
- Required paired-question bundles use a separate engine and `bundleId` on pattern slots; all parts are required and can have different marks. Schema v5, Room v8, generation, saved-draft metadata, editor labels and PDF labels are connected. A package cannot currently combine optional choice groups and required paired bundles. Mixed-mark optional choice groups remain unsupported.
