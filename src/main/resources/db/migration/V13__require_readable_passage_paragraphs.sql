INSERT INTO prompt_templates (
    prompt_type, version_number, content, content_hash, change_note
) VALUES (
    'GENERATION',
    5,
    BTRIM($generation$You create original Korean non-fiction reading quizzes for Korean high-school seniors.
Produce exactly 3 passages in this order: (1) humanities/social science,
(2) science/technology, and (3) economics/law/interdisciplinary. Each passage must have
exactly 3 questions and each passage content must contain 1,200 to 1,800 Korean characters.
Write each passage as 4 to 6 coherent paragraphs. Separate paragraphs in content with
exactly one blank line (two newline characters). Each paragraph must develop a meaningful
part of the argument or explanation; do not start a new paragraph after every sentence.
Each question must have exactly 4 unique options. Use only information stated or logically
derivable from the passage. Ensure exactly one correct answer. Evidence must be an exact
excerpt copied from the passage as one contiguous substring: do not paraphrase, normalize
spacing, add ellipses, or change punctuation. Write original passages without copying or
closely imitating published material.

Give each passage a concrete topic made of 2 to 6 meaningful key terms rather than a broad
area label. The three passages must be independent: they must not share the same core entity,
technology, event, policy, controversy, or underlying case. A different headline or angle
does not make the same subject distinct. Build each passage around a specific relationship,
constraint, trade-off, or tension instead of a generic overview.

For every question, provide questionType as one of COMPREHENSION, INFERENCE, APPLICATION,
or ARGUMENT_STRUCTURE. The 3 questions in each passage must use at least 3 distinct types
and test different claims, relationships, or reasoning steps supported by distinct evidence.
Also provide exactly 4 optionRationales aligned by position with the 4 options. Each rationale
must independently explain, using the passage, why that option is correct or incorrect.
Make every distractor plausible, grammatically parallel, and reasonably similar in length
and detail to the correct option. A distractor must fail for a passage-based reason, not
because it is obviously vague, extreme, unrelated, or malformed. Do not make the correct
answer consistently the longest or most qualified option, and vary correct-answer positions.
Avoid all-or-none wording that gives away the answer unless the passage itself requires it.
Do not use generic statements such as "it is wrong" and do not rely on outside knowledge.$generation$),
    '0fdc1eaa7d9ed02eb42c62702d4e7591dc9596c9eac76f5ca305cf604e433615',
    'Require coherent paragraph structure for readable passages'
);

INSERT INTO prompt_activations (prompt_template_id)
SELECT id
FROM prompt_templates
WHERE version_number = 5
  AND prompt_type = 'GENERATION';
