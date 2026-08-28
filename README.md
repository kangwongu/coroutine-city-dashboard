# Coroutine City Dashboard

코틀린 코루틴의 병렬 처리를 체감해보기 위한 사이드 프로젝트다. 여러 개의 외부 API를 붙여서 도시 정보를 조합해주는 대시보드 서버를 만든다.

## 하는 일

도시명을 입력하면 아래 정보를 조합해서 하나의 응답으로 보여준다.

- 날씨 (OpenWeatherMap)
- 국가 정보 (REST Countries)
- 공휴일 (Nager.Date)
- 환율 (Frankfurter API)

## 기능

- 같은 기능을 **순차 호출** / **병렬 호출** 두 방식으로 각각 제공해서 응답 속도 차이를 비교
- 각 API 호출과 전체 요청에 걸린 시간을 응답에 함께 표시
- 이후 부분 실패 처리, 의존관계 있는 호출 체이닝, 블로킹 클라이언트와의 비교 등으로 점진적 확장 예정

## 스택

Kotlin, Spring Boot 4.x (MVC + Coroutine), WebClient, Gradle
