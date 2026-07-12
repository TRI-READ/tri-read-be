DO $seed$
DECLARE
    quiz_set_id BIGINT;
    passage_id BIGINT;
    question_id BIGINT;
    option_id BIGINT;
    correct_option_id BIGINT;
    passage_position SMALLINT := 0;
    question_position SMALLINT;
    option_position SMALLINT;
    passage_data JSONB;
    question_data JSONB;
    option_data JSONB;
    quiz_data JSONB := $quiz$
    [
      {
        "title": "뇌는 감각을 어떻게 예측하는가",
        "topic": "과학",
        "content": "전통적인 감각 이론에서는 외부 자극이 감각 기관을 거쳐 뇌에 전달되고, 뇌가 그 정보를 분석하여 대상을 인식한다고 본다. 그러나 예측 처리 이론은 인식의 출발점을 다르게 설명한다. 이 이론에 따르면 뇌는 이미 가지고 있는 지식과 직전의 경험을 바탕으로 외부 세계의 상태를 끊임없이 예측한다. 감각 기관에서 들어오는 신호는 예측 자체를 만드는 재료라기보다 예측과 실제 입력 사이의 차이, 즉 예측 오차를 계산하는 데 사용된다. 예측 오차가 작으면 기존의 내부 모형이 유지되고, 오차가 크면 뇌는 내부 모형을 수정한다. 이 과정에서 모든 오차가 동일하게 취급되는 것은 아니다. 뇌는 신호가 얼마나 믿을 만한지에 따라 오차에 서로 다른 가중치를 부여한다. 안개 속 시각 정보처럼 불확실한 신호에는 낮은 가중치를, 선명하고 반복되는 신호에는 높은 가중치를 부여하는 것이다. 따라서 인식은 외부 정보를 수동적으로 복사한 결과가 아니라 예측과 감각 입력을 조정하는 능동적 과정으로 이해된다. 다만 예측 처리 이론이 모든 인지 현상을 하나의 원리로 충분히 설명하는지에 대해서는 논쟁이 계속되고 있다.",
        "questions": [
          {
            "content": "윗글의 예측 처리 이론에 대한 설명으로 가장 적절한 것은?",
            "options": [
              "감각 입력이 축적된 뒤에야 뇌의 예측이 처음 형성된다.",
              "뇌는 기존 예측과 감각 입력의 차이를 이용해 내부 모형을 조정한다.",
              "예측 오차가 작을수록 내부 모형을 전면적으로 교체한다.",
              "외부 자극의 객관적 복사가 인식의 최종 목표라고 본다."
            ],
            "correct": 2,
            "explanation": "예측 처리 이론에서 뇌는 먼저 예측하고, 감각 입력과의 차이인 예측 오차를 통해 내부 모형을 유지하거나 수정한다.",
            "evidence": "감각 기관에서 들어오는 신호는 예측과 실제 입력 사이의 차이를 계산하는 데 사용된다."
          },
          {
            "content": "안개 속 시각 정보의 사례가 보여 주는 내용은?",
            "options": [
              "예측 오차의 크기는 감각 기관의 종류에 의해서만 결정된다.",
              "불확실한 신호일수록 기존 내부 모형을 반드시 폐기해야 한다.",
              "뇌는 신호의 신뢰도에 따라 예측 오차의 영향력을 달리한다.",
              "시각 정보는 다른 감각 정보보다 항상 낮은 가중치를 갖는다."
            ],
            "correct": 3,
            "explanation": "안개 속 정보는 신뢰도가 낮으므로 뇌가 해당 오차에 낮은 가중치를 부여하는 사례다.",
            "evidence": "뇌는 신호가 얼마나 믿을 만한지에 따라 오차에 서로 다른 가중치를 부여한다."
          },
          {
            "content": "윗글을 바탕으로 추론한 내용으로 적절하지 않은 것은?",
            "options": [
              "동일한 감각 입력도 기존 경험에 따라 다르게 인식될 수 있다.",
              "반복적이고 선명한 신호는 내부 모형 수정에 더 큰 영향을 줄 수 있다.",
              "인식 과정에는 상향식 감각 입력과 하향식 예측이 함께 관여한다.",
              "예측 처리 이론은 모든 인지 현상을 완전히 설명한다는 합의에 도달했다."
            ],
            "correct": 4,
            "explanation": "글의 마지막 문장은 예측 처리 이론의 설명 범위를 둘러싼 논쟁이 계속되고 있다고 밝힌다.",
            "evidence": "모든 인지 현상을 하나의 원리로 충분히 설명하는지에 대해서는 논쟁이 계속되고 있다."
          }
        ]
      },
      {
        "title": "탄소 가격은 무엇을 바꾸는가",
        "topic": "경제",
        "content": "기업이 상품을 생산하면서 온실가스를 배출하면 기후 변화로 인한 피해가 사회 전체에 발생하지만, 그 비용이 상품 가격에 모두 반영되지는 않는다. 경제학에서는 이처럼 거래 당사자가 아닌 제삼자에게 발생하는 비용을 부정적 외부 효과라고 부른다. 탄소세와 배출권 거래제는 배출에 가격을 부여하여 외부 비용을 기업의 의사 결정 안으로 끌어들이려는 제도다. 탄소세는 정부가 배출량 한 단위당 세율을 정하므로 기업이 부담할 가격은 비교적 명확하지만, 전체 배출량을 정확히 예측하기 어렵다. 반면 배출권 거래제는 정부가 허용할 총배출량을 먼저 정하고 배출권의 거래를 허용한다. 따라서 전체 배출량은 통제하기 쉽지만 배출권 가격은 시장 상황에 따라 변동할 수 있다. 두 제도 모두 기업이 감축 비용과 탄소 가격을 비교하도록 만든다. 감축 비용이 탄소 가격보다 낮은 기업은 배출을 줄이고, 높은 기업은 세금을 내거나 배출권을 구매하는 편을 택할 수 있다. 그러나 탄소 가격만 도입한다고 기술 혁신과 산업 전환이 자동으로 이루어지는 것은 아니다. 정보 부족, 초기 투자 위험, 전력망과 같은 기반 시설의 제약이 존재하기 때문에 연구 지원과 제도 보완이 함께 논의된다.",
        "questions": [
          {
            "content": "윗글에서 탄소세와 배출권 거래제의 공통 목적으로 제시한 것은?",
            "options": [
              "모든 기업의 감축 비용을 동일하게 만드는 것",
              "온실가스 배출의 외부 비용을 기업의 의사 결정에 반영하는 것",
              "정부가 기업별 생산량을 직접 결정하는 것",
              "배출과 관계없이 상품 가격을 일정하게 유지하는 것"
            ],
            "correct": 2,
            "explanation": "두 제도는 배출에 가격을 부여해 사회적 외부 비용을 기업이 고려하도록 만든다.",
            "evidence": "배출에 가격을 부여하여 외부 비용을 기업의 의사 결정 안으로 끌어들이려는 제도다."
          },
          {
            "content": "탄소세와 배출권 거래제를 비교한 설명으로 가장 적절한 것은?",
            "options": [
              "탄소세는 총배출량이 확정되지만 세율은 시장에서 결정된다.",
              "배출권 거래제는 배출권 가격과 총배출량이 모두 정부에 의해 고정된다.",
              "탄소세는 배출 가격의 예측 가능성이, 배출권 거래제는 총량 통제 가능성이 상대적으로 높다.",
              "배출권 거래제에서는 기업이 감축 비용을 고려할 필요가 없다."
            ],
            "correct": 3,
            "explanation": "탄소세는 단위당 가격이 정해지고, 배출권 거래제는 총량이 먼저 정해진다는 차이가 있다.",
            "evidence": "탄소세의 가격은 비교적 명확하지만 총량은 불확실하고, 배출권 거래제는 총량을 통제하기 쉽지만 가격이 변동할 수 있다."
          },
          {
            "content": "윗글에 따르면 탄소 가격 외에 제도 보완이 필요한 이유는?",
            "options": [
              "기업은 탄소 가격과 감축 비용을 비교할 수 없기 때문에",
              "탄소 가격이 높아질수록 온실가스 배출이 반드시 증가하기 때문에",
              "외부 효과는 어떠한 정책으로도 줄일 수 없기 때문에",
              "정보·투자 위험·기반 시설 등 가격만으로 해결하기 어려운 제약이 있기 때문에"
            ],
            "correct": 4,
            "explanation": "글은 가격 신호 외에도 정보 부족, 초기 투자 위험, 기반 시설 제약이 있다고 설명한다.",
            "evidence": "정보 부족, 초기 투자 위험, 전력망과 같은 기반 시설의 제약이 존재한다."
          }
        ]
      },
      {
        "title": "알고리즘 행정과 이유 제시",
        "topic": "법",
        "content": "행정 기관이 국민에게 불리한 처분을 내릴 때에는 일반적으로 그 근거와 이유를 제시해야 한다. 이유 제시는 처분을 받은 사람이 결정의 타당성을 검토하고 불복 여부를 판단하게 하며, 행정 기관 스스로도 자의적인 판단을 피하도록 만든다. 최근에는 복지 대상 선정이나 위험도 평가에 알고리즘을 활용하는 사례가 늘고 있다. 알고리즘이 많은 변수를 결합해 결과를 산출하더라도 최종 처분의 책임은 행정 기관에 있다. 따라서 기관이 단지 '시스템이 그렇게 판단했다'고 답하는 것만으로는 이유 제시 의무를 다했다고 보기 어렵다. 그렇다고 알고리즘의 소스 코드 전체를 언제나 공개해야 하는 것은 아니다. 영업 비밀, 보안, 제도의 악용 가능성도 함께 고려해야 하기 때문이다. 중요한 것은 당사자가 자신의 처분에 영향을 준 주요 사실과 판단 기준을 이해하고, 잘못된 정보가 사용되었다면 이를 다툴 수 있을 정도의 설명을 제공하는 것이다. 설명의 범위는 처분의 중대성, 알고리즘의 영향력, 공개로 침해될 이익을 비교하여 정할 수 있다. 결국 알고리즘 활용 여부는 이유 제시 의무를 없애는 사유가 아니라, 어떤 설명이 실질적으로 필요한지를 새롭게 검토하게 하는 조건이다.",
        "questions": [
          {
            "content": "윗글에서 이유 제시 제도의 기능으로 언급되지 않은 것은?",
            "options": [
              "처분 상대방이 불복 여부를 판단하도록 돕는다.",
              "행정 기관의 자의적 판단을 억제한다.",
              "알고리즘 개발 기업의 수익을 보장한다.",
              "결정의 타당성을 검토할 수 있게 한다."
            ],
            "correct": 3,
            "explanation": "기업의 수익 보장은 이유 제시 제도의 기능으로 제시되지 않았다.",
            "evidence": "이유 제시는 결정의 타당성 검토와 불복 판단을 돕고 행정 기관의 자의적 판단을 피하도록 만든다."
          },
          {
            "content": "알고리즘을 활용한 행정 처분에 대한 글의 관점으로 가장 적절한 것은?",
            "options": [
              "알고리즘이 판단했다면 행정 기관은 처분 책임에서 벗어난다.",
              "소스 코드를 공개하지 않으면 어떠한 이유 제시도 인정될 수 없다.",
              "알고리즘을 활용한 모든 처분은 법적으로 금지해야 한다.",
              "알고리즘을 사용하더라도 기관은 당사자가 다툴 수 있는 수준의 설명을 제공해야 한다."
            ],
            "correct": 4,
            "explanation": "최종 책임은 행정 기관에 있으며, 당사자가 주요 사실과 기준을 이해하고 오류를 다툴 수 있어야 한다.",
            "evidence": "잘못된 정보가 사용되었다면 이를 다툴 수 있을 정도의 설명을 제공해야 한다."
          },
          {
            "content": "윗글에 따라 설명의 범위를 정할 때 고려할 요소로 적절하지 않은 것은?",
            "options": [
              "처분이 당사자에게 미치는 중대성",
              "알고리즘이 최종 결정에 미친 영향",
              "공개로 인해 침해될 수 있는 보안상 이익",
              "담당 공무원이 선호하는 설명문의 문체"
            ],
            "correct": 4,
            "explanation": "설명문의 문체에 대한 개인적 선호는 비교 형량 요소로 제시되지 않았다.",
            "evidence": "처분의 중대성, 알고리즘의 영향력, 공개로 침해될 이익을 비교하여 정할 수 있다."
          }
        ]
      }
    ]
    $quiz$::JSONB;
BEGIN
    SELECT id
    INTO quiz_set_id
    FROM quiz_sets
    WHERE challenge_date = CURRENT_DATE
      AND status = 'PUBLISHED';

    IF quiz_set_id IS NOT NULL THEN
        RAISE NOTICE 'A published quiz already exists for %.', CURRENT_DATE;
        RETURN;
    END IF;

    INSERT INTO quiz_sets (
        challenge_date,
        difficulty,
        status,
        ai_provider,
        ai_model,
        prompt_version,
        published_at
    )
    VALUES (
        CURRENT_DATE,
        'HIGH_SCHOOL_GRADE_3',
        'PUBLISHED',
        'LOCAL',
        'HANDWRITTEN',
        'dev-seed-v1',
        CURRENT_TIMESTAMP
    )
    RETURNING id INTO quiz_set_id;

    FOR passage_data IN
        SELECT value FROM jsonb_array_elements(quiz_data)
    LOOP
        passage_position := passage_position + 1;

        INSERT INTO passages (
            quiz_set_id,
            position,
            title,
            content,
            topic
        )
        VALUES (
            quiz_set_id,
            passage_position,
            passage_data ->> 'title',
            passage_data ->> 'content',
            passage_data ->> 'topic'
        )
        RETURNING id INTO passage_id;

        question_position := 0;
        FOR question_data IN
            SELECT value FROM jsonb_array_elements(passage_data -> 'questions')
        LOOP
            question_position := question_position + 1;

            INSERT INTO questions (
                passage_id,
                position,
                content
            )
            VALUES (
                passage_id,
                question_position,
                question_data ->> 'content'
            )
            RETURNING id INTO question_id;

            option_position := 0;
            correct_option_id := NULL;
            FOR option_data IN
                SELECT value FROM jsonb_array_elements(question_data -> 'options')
            LOOP
                option_position := option_position + 1;

                INSERT INTO question_options (
                    question_id,
                    position,
                    content
                )
                VALUES (
                    question_id,
                    option_position,
                    option_data #>> '{}'
                )
                RETURNING id INTO option_id;

                IF option_position = (question_data ->> 'correct')::SMALLINT THEN
                    correct_option_id := option_id;
                END IF;
            END LOOP;

            INSERT INTO question_keys (
                question_id,
                correct_option_id,
                explanation,
                evidence
            )
            VALUES (
                question_id,
                correct_option_id,
                question_data ->> 'explanation',
                question_data ->> 'evidence'
            );
        END LOOP;
    END LOOP;

    RAISE NOTICE 'Created development quiz set % for %.', quiz_set_id, CURRENT_DATE;
END
$seed$;
