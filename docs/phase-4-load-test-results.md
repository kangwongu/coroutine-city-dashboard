# Phase 4 부하 테스트 결과 정리

`scripts/load-test-phase4.sh`로 실제 부하를 걸어본 결과와 해석을 정리한 문서다. Phase 4
구현 자체는 `docs/phase-4-todo.md`에 체크리스트로 남아있고, 이 문서는 그 결과물을
"목적 → 관찰 대상 → 테스트 설계 → 예상 결과 → 실제 결과 → 원인 분석" 순서로 정리한다.

## 1. 목적

`docs/constitution.md`의 목표 중 하나는 "코틀린 코루틴의 구조적 동시성을 실제 네트워크
I/O가 섞인 코드에서 직접 써보고 체감한다"이다. Phase 4는 그중에서도 `docs/specification.md`
FR6/NFR2 — **블로킹 클라이언트(RestTemplate)를 코루틴으로 잘못 감싸면 논블로킹(WebClient)의
이점이 사라진다**는 것을 이론이 아니라 실측으로 확인하는 단계다.

## 2. 무엇을 보고 싶었는지

톰캣의 요청 처리 스레드 풀을 `server.tomcat.threads.max: 10`으로 의도적으로 작게 잡아두고,
각 구현 방식이 이 좁은 자원을 얼마나 오래 점유하는지를 보고 싶었다.

테스트 도중, `tomcat.threads.busy`(Micrometer/Actuator 지표)가 **톰캣 커넥터 워커 풀만
보는 지표**이고 코루틴의 `Dispatchers.IO` 풀은 전혀 잡히지 않는다는 걸 발견했다. 그래서
"블로킹이 실제로 어딘가에서 일어나고 있는가"를 놓치지 않기 위해 `jvm.threads.live`(JVM
전체 살아있는 스레드 수)도 같이 관찰하게 됐다.

## 3. 테스트 설계

같은 대시보드 조회 기능(날씨+국가+공휴일+환율 4개 API 병렬 조회)을 세 가지 방식으로 구현한
엔드포인트를 비교한다.

| 엔드포인트 | 구현 | 이론상 톰캣 스레드 점유 |
|---|---|---|
| `/parallel` | WebClient(논블로킹) + `suspend fun` | 즉시 반납 |
| `/parallel-blocking` | RestTemplate(블로킹)을 `withContext(Dispatchers.IO)`로 감쌈 + `suspend fun` | 즉시 반납 (Dispatchers.IO가 대신 점유) |
| `/parallel-blocking-naive` | RestTemplate(블로킹)을 `runBlocking`으로 감쌈 + plain `fun` | 응답 끝날 때까지 점유 (안티패턴) |

`scripts/load-test-phase4.sh`가 세 엔드포인트에 각각 동시 15건씩 순서대로 쏘고, 0.1초
간격으로 `tomcat.threads.busy`와 `jvm.threads.live`를 함께 폴링한다. 배치 사이에는
쿨다운(기본 10~12초)을 둔다 — 이유는 7절 참고.

## 4. 예상 결과

- `/parallel`, `/parallel-blocking`: busy 스레드가 낮게 유지되고, 15건의 응답시간이
  서로 비슷하게 고르다.
- `/parallel-blocking-naive`: 톰캣 스레드(max=10)가 금방 꽉 차서, 나머지 5건이 앞 10건이
  끝날 때까지 대기 → 응답시간이 2단계로 갈라지고, busy 스레드가 max 근처에서 한동안
  유지된다.

## 5. 실제 결과

세션 중 가장 깨끗하게 나온 실행(서버 재기동 직후, 쿨다운 10~12초) 기준.

**톰캣 busy 스레드 시계열 (0.1초 간격)**

```
parallel          : 1.0 1.0 1.0 1.0 ... (전 구간 1.0)
parallel-blocking : 1.0 1.0 1.0 1.0 ... (전 구간 1.0)
parallel-blocking-naive : 1.0 10.0 6.0 6.0 6.0 6.0 6.0 6.0 6.0 6.0 6.0 6.0 6.0 6.0 6.0 6.0 6.0 6.0 6.0 6.0
```

**개별 응답시간 (초, 오름차순, 동시 15건)**

```
parallel          : 3.13 ~ 3.19 (스프레드 60ms 내외, 15건 균일)
parallel-blocking : 3.04 ~ 3.09 (스프레드 50ms 내외, 15건 균일)
parallel-blocking-naive :
  앞 10건: 3.05 ~ 3.07
  뒤  5건: 6.09 ~ 6.15   ← 정확히 앞 10건 + 3초 대기만큼 늦게 끝남
```

**JVM 전체 스레드 수 (서버 재기동 직후 첫 실행 기준)**

```
parallel          : 47  (WebClient만 씀, 내내 고정)
parallel-blocking : 106 (Dispatchers.IO 사용 시작 → 47에서 59개 급증, 내내 고정)
parallel-blocking-naive : 106 (parallel-blocking과 동일한 내부 로직 재사용 → 동일)
```

## 6. 왜 그런 결과가 나왔는지

- **`suspend fun`은 첫 suspend 지점에서 톰캣 스레드를 즉시 반납한다.** Spring MVC가
  `suspend fun` 컨트롤러를 비동기 디스패치로 처리하기 때문에, `parallel`과
  `parallel-blocking` 둘 다 요청을 접수한 톰캣 스레드가 곧바로 풀에 돌아가 다른 요청을
  받으러 간다. 그래서 둘 다 busy가 낮고 응답시간도 균일하다.
- **`runBlocking`은 그걸 호출한 스레드 자체를 코루틴이 끝날 때까지 붙잡는다.**
  `parallel-blocking-naive`는 plain `fun` 컨트롤러가 `runBlocking { }`으로 결과를 기다리기
  때문에, 톰캣 스레드가 가장 느린 API(`Thread.sleep(3000)`이 있는 exchangeRate) 응답이 올
  때까지 그 자리에서 대기한다. 동시 15건 중 10건(=max)만 즉시 스레드를 받고, 나머지 5건은
  줄을 서서 "앞 10건이 끝나야 시작"하는 처지가 된다 — 그래서 응답시간이 정확히 2배 근처로
  뛰는 계단이 생긴다.
- **`tomcat.threads.busy`만 보면 `parallel-blocking`이 "완전히 논블로킹"인 것처럼
  보이지만, 그렇지 않다.** RestTemplate 호출 자체는 여전히 블로킹 I/O이고, 그 블로킹은
  `Dispatchers.IO` 스레드에서 일어난다. 이 지표는 톰캣 커넥터 풀만 보므로 그 사실이 안 잡힐
  뿐이다. `jvm.threads.live`로 보완해서 보니, `parallel`(WebClient만 사용) 대비
  `parallel-blocking`에서 JVM 전체 스레드 수가 47 → 106으로 급증한 걸 확인했다 — 즉
  **블로킹이 사라진 게 아니라, 좁고 귀한 톰캣 풀에서 크고 탄력적인 `Dispatchers.IO` 풀로
  점유 위치만 옮겨간 것**이다. 이번 부하(동시 15건)로는 `Dispatchers.IO`가 병목이 되지
  않았을 뿐, 동시성을 훨씬 더 올리면 이쪽도 언젠가 한계에 부딪힐 수 있다.

## 7. 테스트 중 발견한 부가 이슈

- **콜드스타트**: 서버 기동 직후 첫 배치(주로 `parallel`)는 외부 API 호스트로의
  DNS/TCP/TLS 핸드셰이크가 처음 일어나느라 응답시간이 한 번 더 걸릴 수 있다(예: 3.1s
  대신 5.1s). 스레드 점유 문제가 아니라 커넥션 워밍업 비용이므로, 같은 배치를 한 번 더
  돌려서 재현되지 않으면 콜드스타트로 판단하면 된다.
- **외부 API 레이트리밋**: 3배치(총 45건)를 쉬지 않고 연달아 쏘면 country API(REST
  Countries, Cloudflare)가 짧은 시간에 몰린 요청을 429로 차단하고, 그게 애플리케이션에서는
  500으로 전파되어 "톰캣 스레드 풀 소진"과 겉보기엔 비슷한 실패로 섞여 보일 수 있다.
  배치 사이 쿨다운(기본 10~12초)으로 완화했다 — 그래도 실패가 보이면 톰캣 문제가 아니라
  외부 API 레이트리밋일 가능성이 높으니 서버 로그에서 `TooManyRequests`를 먼저 확인할 것.
  REST Countries 무료 플랜은 월 1,000요청 한도라, 반복 실행 빈도에 유의해야 한다.
- **`jvm.threads.live`의 baseline 누적**: 이 지표는 "지금 이 배치가 스레드를 얼마나
  쓰는지"가 아니라 "JVM이 지금까지 누적으로 얼마나 스레드를 만들어봤는지"에 가깝다.
  `Dispatchers.IO`가 한 번 스레드를 늘려두면 유휴 상태로 한동안 살려두기 때문에, 서버를
  재기동하지 않고 스크립트를 반복 실행하면 이전 실행의 잔재가 다음 실행의 baseline이 되어
  "47 → 106" 같은 깨끗한 대비가 흐려진다. 깨끗한 before/after 대비를 다시 보려면 서버를
  재기동한 직후 바로 측정해야 한다.

## 8. 결론

Phase 4의 완료 기준(FR6 — RestTemplate을 코루틴으로 감싼 블로킹 구현 제공, NFR2 — 논블로킹
구현은 호출 대기 중 서버 스레드를 점유하지 않아야 함)이 실측으로 확인됐다.

- `withContext(Dispatchers.IO)`로 제대로 오프로딩한 블로킹 클라이언트(`parallel-blocking`)는
  WebClient(`parallel`)와 마찬가지로 톰캣 스레드를 거의 점유하지 않는다.
- 반대로 `runBlocking`으로 감싼 안티패턴(`parallel-blocking-naive`)만 톰캣 스레드 풀을
  실제로 소진시키고, 풀 크기를 넘는 요청은 큐에서 대기하며 응답시간이 계단식으로 늘어난다.
- 다만 `withContext(Dispatchers.IO)` 방식도 "블로킹이 사라진 것"은 아니고 "점유 위치가
  톰캣에서 Dispatchers.IO로 옮겨간 것"이라는 점을 `jvm.threads.live` 관찰로 확인했다 —
  이는 WebClient(진짜 논블로킹, 이벤트 루프 기반)와 RestTemplate+Dispatchers.IO(블로킹을
  다른 풀로 옮긴 것) 사이의 근본적인 구조 차이이며, 동시성이 매우 커지면 다시 드러날 수
  있는 지점이다.
