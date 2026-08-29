# CLAUDE.md

이 저장소에서 작업할 때 참고할 문서와 원칙을 정의한다. 아래 문서들이 각 영역의 SSOT(Single Source of Truth)다 — 내용을 여기 다시 옮겨 적지 않고 항상 원문을 참조한다.

@README.md
@docs/constitution.md
@docs/specification.md
@docs/plan.md
@.claude/rules/commit.md

## 문서 역할

- `docs/constitution.md` — 프로젝트 목적, 성공 기준, 제약사항의 SSOT
- `docs/specification.md` — 기능/비기능/UI-UX/기술 요구사항의 SSOT
- `docs/plan.md` — 프로젝트 구조, 개발 순서(Phase), 외부 API 상세의 SSOT
- `.claude/rules/commit.md` — 커밋 메시지 컨벤션의 SSOT

## 해야 할 것

- 작업 전에 `docs/plan.md`에서 현재 Phase가 어디까지 진행됐는지 확인하고, 그 Phase 범위 안에서만 작업한다.
- 모호하거나 문서에 근거가 없는 결정은 임의로 추측하지 말고 반드시 사용자에게 먼저 물어본다.
- 실제 코드와 `docs/` 문서 내용이 서로 어긋난 것을 발견하면, 임의로 한쪽을 다른 쪽에 맞춰 고치지 말고 그 자리에서 사용자에게 알린다. 어느 쪽이 맞는지 확인받은 뒤에만 반영한다.
- 커밋 메시지는 `.claude/rules/commit.md`를 그대로 따른다.

## 하지 말아야 할 것

- 위 문서들의 내용을 CLAUDE.md에 요약하거나 복사해서 중복 기재하지 않는다. 문서가 바뀔 때마다 이 파일도 같이 고쳐야 하는 유지보수 포인트를 만들지 않는다.
- `docs/constitution.md`의 제약사항이나 `docs/plan.md`의 Phase 순서를 벗어나는 작업을 사용자 확인 없이 진행하지 않는다.
- 문서와 코드 간 불일치를 발견했을 때, 사용자에게 알리지 않고 조용히 한쪽만 고쳐서 맞추지 않는다.
