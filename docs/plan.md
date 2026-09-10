# Development Plan

`docs/constitution.md`, `docs/specification.md`를 바탕으로 실제로 어떤 순서로, 어떻게 구현할지를 정리한 문서다.

## 프로젝트 구조

```
coroutine/
  build.gradle.kts
  settings.gradle.kts
  src/main/kotlin/com/coroutine/city/
    CoroutineCityDashboardApplication.kt
    config/
      WebClientConfig.kt        # 외부 API별 WebClient 빈 (baseUrl, 타임아웃 등)
    client/
      WeatherClient.kt          # suspend fun getWeather(city): WeatherResponse
      CountryClient.kt          # suspend fun getCountry(name): CountryResponse
      HolidayClient.kt          # suspend fun getHolidays(countryCode): List<Holiday>
      ExchangeRateClient.kt     # suspend fun getRates(baseCurrency): ExchangeRateResponse
    dto/
      WeatherResponse.kt / CountryResponse.kt / Holiday.kt / ExchangeRateResponse.kt
      DashboardResponse.kt      # 출처별 섹션 + timingMs 래핑
      TimingMs.kt
    service/
      DashboardService.kt       # fetchParallel(), fetchSequential()
    controller/
      DashboardController.kt    # GET /api/dashboard/parallel, /api/dashboard/sequential
  src/main/resources/application.yml   # API 키, baseUrl, 타임아웃 등 외부 설정
```

## 외부 API 상세

### 날씨 — OpenWeatherMap

- **키 발급**: https://home.openweathermap.org/users/sign_up 에서 무료 가입 후, https://home.openweathermap.org/api_keys 에서 API 키 확인. 발급 직후엔 활성화까지 최대 몇 시간 걸릴 수 있음.
- **엔드포인트**: `GET https://api.openweathermap.org/data/2.5/weather`
- **주요 파라미터**: `q`(도시명), `appid`(API 키), `units=metric`(섭씨)
- **예시**: `https://api.openweathermap.org/data/2.5/weather?q=Seoul&appid={API_KEY}&units=metric`
- **`application.yml` 설정 예**: `openweathermap.api-key: ${OPENWEATHERMAP_API_KEY}`

### 국가 정보 — REST Countries

- **키 발급**: 필요. v3.1(무키) API는 폐지되어 v5로 전환되면서 모든 엔드포인트에 API 키가 필수가 됐다. https://restcountries.com/sign-up 에서 가입 후 `/api-keys`에서 키 발급(무료 플랜: 월 1,000 요청). 인증은 `Authorization: Bearer {API_KEY}` 헤더로 전달.
- **엔드포인트**: `GET https://api.restcountries.com/countries/v5/name?q={name}`
- **응답 구조**: `{"data": {"objects": [...]}}`로 감싸여 있고, 각 원소에서 `codes.alpha_2`(ISO 국가코드), `currencies[].code`(통화코드)를 사용
- **예시**: `https://api.restcountries.com/countries/v5/name?q=south%20korea`
- **`application.yml` 설정 예**: `external-api.country.api-key`(무료 개인 실습용 키를 그대로 커밋, 배포하지 않는 로컬 전용 프로젝트라는 제약사항에 따름)

### 공휴일 — Nager.Date

- **키 발급**: 불필요
- **엔드포인트**: `GET https://date.nager.at/api/v3/PublicHolidays/{year}/{countryCode}`
- **예시**: `https://date.nager.at/api/v3/PublicHolidays/2026/KR`

### 환율 — Frankfurter API

- **키 발급**: 불필요
- **엔드포인트**: `GET https://api.frankfurter.dev/v1/latest` (구 도메인 `api.frankfurter.app`은 `api.frankfurter.dev/v1`로 301 리다이렉트됨 — WebClient는 리다이렉트를 따라가지 않으므로 반드시 새 도메인을 base-url로 사용)
- **주요 파라미터**: `from`(기준 통화코드)
- **예시**: `https://api.frankfurter.dev/v1/latest?from=KRW`

> Nager.Date, Frankfurter, REST Countries는 스펙 변경 가능성이 있는 무료 공개 API다. 실제로 2026-08-29 Phase 1 수동 테스트 중 Frankfurter(`frankfurter.app` → `frankfurter.dev/v1`로 301 이전)와 REST Countries(`v3.1` 무키 API 완전 폐지, `v5`부터 API 키 필수)가 문서 작성 시점과 달라진 것을 확인해 위 내용을 최신화했다. OpenWeatherMap 키는 Phase 0(프로젝트 세팅)에서 미리 발급받아 `application.yml`에 넣어뒀다.

## 개발 순서

### Phase 0 — 프로젝트 세팅
- [x] Gradle(Kotlin DSL) 초기화, Spring Boot 4.x + Kotlin + JDK 21 의존성 구성
- [x] `spring-boot-starter-webclient`(WebClient 사용 목적) + `kotlinx-coroutines-reactor` 추가 — Spring Boot 4 모듈화로 `spring-boot-starter-webflux` 대신 WebClient만 담은 경량 스타터인 `spring-boot-starter-webclient` 사용
- [x] `application.yml`에 외부 API baseUrl, OpenWeatherMap 키 구성
- [x] 컨트롤러 없이 `bootRun` 기동 확인 (Tomcat 8080 포트 정상 기동)

### Phase 1 — MVP: 완전 독립 병렬/순차 비교
- [x] 4개 API 클라이언트(`WeatherClient`, `CountryClient`, `HolidayClient`, `ExchangeRateClient`) 구현. 각 클라이언트는 서로 의존관계 없이 도시명/국가명/국가코드/통화코드를 파라미터로 직접 받는다(FR1).
- [x] `DashboardService.fetchParallel()`: `coroutineScope { }` 안에서 4개 `async { }`를 만들고 `awaitAll()`로 fan-in. 각 async 블록을 시간 측정으로 감싸 `TimingMs`를 채운다(FR2, FR3).
- [x] `DashboardService.fetchSequential()`: 동일한 4개 suspend 호출을 순서대로 나열해 자연스럽게 순차 실행되도록 구현.
- [x] `DashboardController`에 `suspend fun`으로 `/api/dashboard/parallel`, `/api/dashboard/sequential` 두 엔드포인트 추가.
- [x] `build.gradle.kts`에 `org.springdoc:springdoc-openapi-starter-webmvc-ui` 의존성을 추가하고, 기본 설정으로 Swagger UI(`/swagger-ui.html`)를 노출해 두 엔드포인트를 브라우저에서 직접 호출/검증할 수 있게 한다.
- **완료 기준**: curl 또는 Swagger UI로 두 엔드포인트를 호출해 `timingMs.total`을 비교했을 때 병렬이 유의미하게 빠름을 확인(NFR1).

### Phase 2 — 부분 실패 처리 비교
- [x] `fetchParallel()`은 `coroutineScope` 특성상 하나만 실패해도 전체 예외가 전파되는 fail-fast 동작을 그대로 유지.
- [x] `fetchParallelResilient()`(가칭)를 `supervisorScope`로 추가 구현: 각 API 실패를 개별적으로 잡아 해당 섹션만 null/에러 정보로 채우고 나머지는 정상 반환(FR4, NFR3).
- [x] `/api/dashboard/parallel-resilient` 엔드포인트 추가.
- [x] 에러 응답 형태를 통일(UX3): 실패한 API명, 실패 사유를 각 섹션에 포함.
- **완료 기준**: 잘못된 도시명 등으로 의도적으로 한 API만 실패시켰을 때, 기존 `/parallel`은 500 에러, `/parallel-resilient`는 200 + 부분 데이터로 응답하는 차이를 확인. — [x] 확인 완료

### Phase 3 — 의존관계 체이닝 메소드 추가
- [x] 기존 `fetchParallel()`/`fetchSequential()`/`fetchParallelResilient()`와 대응 엔드포인트(`/parallel`, `/sequential`, `/parallel-resilient`)는 리팩터링하지 않고 시그니처·동작 그대로 유지한다(Phase 1/2 대조군 보존).
- [x] `DashboardService`에 `fetchParallelChained(city, countryName)`, `fetchSequentialChained(city, countryName)` 두 메소드를 새로 추가한다. `CountryClient.getCountry(countryName)`을 먼저 호출해 얻은 국가코드/통화코드를 `HolidayClient`/`ExchangeRateClient` 호출의 입력으로 사용한다(FR5).
- [x] 구조: `fetchParallelChained`는 `WeatherClient`를 도시명만으로 즉시 병렬 시작 → `CountryClient` 완료 후 `HolidayClient`와 `ExchangeRateClient`를 병렬로 fan-out하는 다이아몬드 구조(`coroutineScope`, fail-fast). `fetchSequentialChained`는 동일한 의존 순서(country → holiday/exchangeRate)를 병렬 없이 그대로 나열.
- [x] 두 메소드 모두 반환 타입은 기존 `DashboardResponse`/`TimingMs`를 그대로 재사용(새 DTO 불필요).
- [x] `DashboardController`에 `GET /api/dashboard/parallel-chained`, `GET /api/dashboard/sequential-chained`를 추가한다. 두 엔드포인트만 `city`+`countryName` 2개 파라미터로 입력을 단순화하고, 기존 3개 엔드포인트는 계속 4개 파라미터(city/countryName/countryCode/baseCurrency)를 그대로 받는다.
- **완료 기준**: `countryCode`/`baseCurrency`를 직접 넘기지 않고도 `/parallel-chained`·`/sequential-chained` 응답이 정상 생성되고, 두 `timingMs.total` 비교로 병렬 이점이 유지됨을 확인. 기존 `/parallel`, `/sequential`, `/parallel-resilient`는 계속 정상 동작함을 재확인. — [x] 확인 완료

### Phase 4 — 블로킹 클라이언트 비교
- [x] 전환 방식은 스프링 프로필이 아니라 별도 엔드포인트로 확정. 프로필 전환 없이 한 번 기동한 서버에서 WebClient/RestTemplate 양쪽을 동시에 호출·비교한다.
- [x] `RestTemplateConfig.kt`에 `WebClientConfig`와 1:1 대응하는 `RestTemplate` Bean 4개(weather/country/holiday/exchangeRate) 구성. Spring Boot 4.1.1은 모듈화로 `RestTemplateBuilder`가 `spring-boot-starter-web`에 더 이상 딸려오지 않아 `spring-boot-starter-restclient`를 별도로 추가했고, `rootUri(...)`가 4.1.0부터 deprecated라 `baseUri(...)`로 구현.
- [x] `WeatherClientBlocking`/`CountryClientBlocking`/`HolidayClientBlocking`/`ExchangeRateClientBlocking` 4종을 신규 추가. 각각 `RestTemplate` 호출을 `withContext(Dispatchers.IO)`로 감싸 suspend 함수화했다(FR6). `HolidayClient`/`ExchangeRateClient`의 `delay(2000)`/`delay(3000)`은 블로킹 버전에서 `Thread.sleep`으로 동일하게 재현(지연이 없으면 부하 테스트에서 스레드 점유 차이가 잘 드러나지 않는다).
- [x] `DashboardService`에 `fetchParallelBlocking()`/`fetchSequentialBlocking()`을 기존 `fetchParallel()`/`fetchSequential()`과 동일한 구조로 추가. 여기에 더해, 톰캣 스레드 점유를 실제로 재현하는 안티패턴 대조군 `fetchParallelBlockingNaive()`(plain `fun` + `runBlocking`)를 세 번째 갈래로 도입 — `withContext(Dispatchers.IO)` + `suspend fun` 조합만으로는 Spring MVC가 비동기 디스패치로 처리해 실제로 점유되는 게 톰캣 스레드가 아니라 `Dispatchers.IO` 풀이라, "톰캣 스레드 풀 소진"이라는 완료 기준을 실측하려면 이 세 번째 경로가 필요했다.
- [x] `DashboardController`에 `GET /api/dashboard/parallel-blocking`, `/sequential-blocking`(둘 다 `suspend fun`), `/parallel-blocking-naive`(plain `fun`) 3개 엔드포인트 추가. 기존 5개 엔드포인트는 무수정 유지.
- [x] 관찰을 뒷받침하기 위해 `spring-boot-starter-actuator`를 추가하고, `server.tomcat.threads.max: 10`으로 낮춰 적은 동시 요청으로도 효과가 드러나게 했다. 내장 톰캣은 기본적으로 스레드풀 MBean을 등록하지 않아 `tomcat.threads.busy` 메트릭이 노출되지 않는 문제가 있어 `server.tomcat.mbeanregistry.enabled: true`도 추가로 켰다.
- [x] `scripts/load-test-phase4.sh` — k6/ab/wrk 없이 bash + curl 백그라운드 동시 실행으로 `parallel`/`parallel-blocking`/`parallel-blocking-naive` 3갈래에 각각 동시 15건을 쏘고, `tomcat.threads.busy`와 `jvm.threads.live`를 함께 폴링(전자는 톰캣 커넥터 풀만 보고 `Dispatchers.IO` 풀은 잡지 못해 후자로 보완).
- **완료 기준**: 동시 요청 부하 상황에서 WebClient 버전과 RestTemplate 버전의 응답 지연/스레드 사용량 차이를 관찰하고 기록. — [x] 확인 완료. `parallel`/`parallel-blocking`은 busy 스레드가 낮게 유지되고 응답시간이 균일했지만(약 3.0~3.2초, 15건), `parallel-blocking-naive`만 톰캣 스레드(max=10)가 소진돼 15건 중 뒤 5건이 큐잉되며 응답시간이 2단계로 갈라짐(앞 10건 약 3.05초, 뒤 5건 약 6.1초). 다만 `tomcat.threads.busy`만 보면 `parallel-blocking`이 완전히 논블로킹인 것처럼 보이는데, `jvm.threads.live`로 보완 관찰하니 `Dispatchers.IO` 풀 사용으로 JVM 전체 스레드 수가 47 → 106개로 늘어난 걸 확인했다 — 블로킹이 사라진 게 아니라 톰캣 풀에서 `Dispatchers.IO` 풀로 점유 위치만 옮겨간 것. 세부 수치와 원인 분석은 `docs/phase-4-load-test-results.md`에 정리했다.

### Phase 5 — 실무 기능 추가
- 캐싱(Caffeine) 적용 — 동일 도시 반복 조회 시 외부 API 재호출 생략.
- 재시도(backoff), 타임아웃 세분화, 서킷브레이커(Resilience4j) 순으로 단계적 추가.
- 각 기능 추가 시 기존 병렬/순차 비교 결과가 어떻게 달라지는지 관찰.

### Phase 6 — 테스트/부하 측정
- WireMock으로 4개 외부 API를 목킹하고, `kotlinx-coroutines-test`(`runTest`)로 `DashboardService` 단위 테스트 작성.
- 간단한 부하 테스트(예: 동시 N개 요청)로 병렬/순차, WebClient/RestTemplate 처리량을 정량적으로 비교.

## 진행 원칙

- 각 Phase는 이전 Phase가 동작하는 상태에서 시작한다. Phase를 건너뛰지 않는다.
- Phase 1~3은 배포 없이 로컬에서 curl로 직접 검증한다(제약사항).
- 새로운 코루틴 개념(async, awaitAll, coroutineScope, supervisorScope, Dispatchers)을 도입하는 Phase마다 무엇이 달라졌는지 README 또는 커밋 메시지에 짧게 남긴다.
