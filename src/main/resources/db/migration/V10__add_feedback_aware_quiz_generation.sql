ALTER TABLE ai_api_calls
    DROP CONSTRAINT IF EXISTS ai_api_calls_purpose_check;

ALTER TABLE ai_api_calls
    ADD CONSTRAINT ai_api_calls_purpose_check
        CHECK (purpose IN ('GENERATION', 'REPAIR', 'VALIDATION'));

INSERT INTO prompt_templates (
    prompt_type, version_number, content, content_hash, change_note
) VALUES (
    'GENERATION',
    3,
    BTRIM($generation$You create original Korean non-fiction reading quizzes for Korean high-school seniors.
Produce exactly 3 passages in this order: (1) humanities/social science,
(2) science/technology, and (3) economics/law/interdisciplinary. Each passage must have
exactly 3 questions and each passage content must contain 1,200 to 1,800 Korean characters.
Each question must have exactly 4 unique options. Use only information stated or logically
derivable from the passage. Ensure exactly one correct answer. Evidence must be an exact
excerpt copied from the passage as one contiguous substring: do not paraphrase, normalize
spacing, add ellipses, or change punctuation. Write original passages without copying or
closely imitating published material.

For every question, provide questionType as one of COMPREHENSION, INFERENCE, APPLICATION,
or ARGUMENT_STRUCTURE. The 3 questions in each passage must use at least 3 distinct types.
Also provide exactly 4 optionRationales aligned by position with the 4 options. Each rationale
must independently explain, using the passage, why that option is correct or incorrect.
Do not use generic statements such as "it is wrong" and do not rely on outside knowledge.$generation$),
    'bf777b6616e6d3e20f54ad975a11801167411ec1bc032e69685209a71380dc7b',
    'Add question-type diversity and option-level self-review metadata'
), (
    'VALIDATION',
    3,
    BTRIM($validation$You are an independent quality verifier for a Korean high-school senior reading quiz.
Verify every answer using only the supplied passage. Reject ambiguous questions, multiple
plausible answers, unsupported explanations, evidence that does not prove the answer,
internal factual or logical contradictions, and content below the requested difficulty.
Inspect questionType and all optionRationales. Reject a question when a rationale does not
actually distinguish its option from the correct answer, contradicts the passage, relies on
outside knowledge, or merely asserts that an option is wrong. Require at least 3 distinct
question types within each passage.
Return a strict score from 0 to 100. passed may be true only when there are no ERROR issues
and the score is at least 90. Do not trust the provided answer key without checking it.$validation$),
    'df1e7cf41bffba6ca9bdf71086393c22cb310b4862aec6cb126fb7d7c8bce985',
    'Validate option-level self-review and question-type diversity'
);

INSERT INTO prompt_activations (prompt_template_id)
SELECT id
FROM prompt_templates
WHERE version_number = 3
  AND prompt_type IN ('GENERATION', 'VALIDATION');
