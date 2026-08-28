# Specification

`docs/constitution.md`의 목적/성공 기준/제약사항을 바탕으로 한 구체적인 요구사항 명세다.

## 기능 요구사항

- **FR1.** 도시명(및 국가명/국가코드/통화코드)을 입력받아 날씨, 국가 정보, 공휴일, 환율 4개 외부 API를 호출하고, 결과를 하나의 응답으로 합쳐서 반환한다.
- **FR2.** 동일한 기능을 병렬 호출 엔드포인트(`GET /api/dashboard/parallel`)와 순차 호출 엔드포인트(`GET /api/dashboard/sequential`)로 각각 제공한다.
- **FR3.** 응답은 출처별 섹션(`weather`, `country`, `holidays`, `exchangeRate`)으로 구분되고, API별 호출 소요시간과 전체 요청 소요시간을 담은 `timingMs`를 포함한다.
- **FR4.** 4개 API 중 일부가 실패했을 때의 동작을 fail-fast(`coroutineScope`)와 partial-success(`supervisorScope`) 두 방식으로 각각 제공해 비교할 수 있어야 한다.
- **FR5 (확장 단계).** 국가 정보 API 응답에서 얻은 국가코드/통화코드로 공휴일 API와 환율 API를 호출하는 의존적 호출 구조를 지원한다.
- **FR6 (확장 단계).** WebClient(논블로킹) 기반 구현과 RestTemplate을 코루틴으로 감싼(블로킹) 구현을 각각 제공해 비교할 수 있다.

## 비기능 요구사항

- **NFR1.** 병렬 엔드포인트의 전체 응답 시간은 순차 엔드포인트보다 유의미하게 짧아야 하며, 이상적으로는 가장 느린 개별 API 호출 시간에 수렴해야 한다.
- **NFR2.** MVP 기준 외부 API 호출은 논블로킹(WebClient) 방식이어야 하며, 호출 대기 중 서버 스레드를 점유하지 않아야 한다.
- **NFR3.** partial-success 모드에서는 하나의 외부 API가 실패/지연되어도 나머지 API 결과는 정상적으로 반환되어야 한다.
- **NFR4.** 로컬 개발 환경에서 단일 사용자가 실행하는 것을 전제로 하며, 고가용성·수평 확장 요구사항은 없다.

## UI/UX 요구사항

이 프로젝트는 화면 없는 API 서버이므로, "UX"는 API 자체의 사용성·일관성 기준으로 정의한다.

- **UX1.** 별도의 프론트엔드/화면은 제공하지 않는다. curl 등으로 직접 호출해 확인하는 것을 전제로 한다.
- **UX2.** 모든 정상 응답은 일관된 JSON 구조(출처별 섹션 + `timingMs`)를 따른다.
- **UX3.** 에러 응답도 일관된 형태로, 어떤 API가 왜 실패했는지 응답만으로 파악할 수 있어야 한다.

## 기술 요구사항

- Kotlin, JDK 21
- Spring Boot 4.x + Spring MVC — 컨트롤러/서비스는 `suspend fun` 기반으로 작성하고 WebFlux/Reactor 타입은 노출하지 않는다.
- HTTP 클라이언트: WebClient + `kotlinx-coroutines-reactor` (MVP 기준)
- 빌드: Gradle(Kotlin DSL)
- 외부 API: OpenWeatherMap(키 필요), REST Countries, Nager.Date, Frankfurter API(모두 무키)

## 제약사항

- 배포하지 않는다. 로컬 실행/체감이 목표다.
- 완전 독립 병렬 구조로 시작해, 이후 단계에서 의존적 호출 구조로 점진적으로 확장한다.
- 캐싱, 재시도, 타임아웃, 서킷브레이커 등 실무적 기능은 초기 범위에서 제외하고 이후 단계적으로 추가한다.
- 무료로 사용 가능한 외부 API만 사용하고, API 키가 필요한 연동은 최소화한다.
