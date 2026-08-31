package com.coroutine.city.controller

import com.coroutine.city.dto.DashboardResilientResponse
import com.coroutine.city.dto.DashboardResponse
import com.coroutine.city.service.DashboardService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 모든 엔드포인트를 suspend fun으로 선언한 이유:
//
// - suspend fun은 다른 suspend fun이나 코루틴 빌더(runBlocking, launch, async) 안에서만 호출 가능하다.
//   dashboardService.fetchParallel() 등도 suspend fun이라, 컨트롤러가 일반 fun이었다면 이걸 호출하기
//   위해 runBlocking { }으로 감싸야 했다.
//
// - fun + runBlocking으로 했다면: 이 요청을 받은 톰캣 워커 스레드가 runBlocking 블록이 끝날 때까지
//   (=4개 외부 API 중 가장 느린 응답이 올 때까지) 그 자리에서 반납되지 않고 계속 붙잡혀 있는다.
//   외부 API 호출 자체(WebClient/Netty)는 원래도 논블로킹이라 응답을 실제로 기다리는 건 Netty의
//   이벤트 루프 스레드(reactor-http-nio-*)가 담당하지만, runBlocking을 호출한 톰캣 스레드는 그 결과가
//   나올 때까지 그냥 옆에서 같이 대기하며 다른 요청을 처리하러 가지 못한다 — 블로킹 클라이언트를 쓴
//   것과 다를 바 없어져 WebClient(논블로킹)를 쓰는 의미가 사라진다.
//
// - suspend fun으로 했을 때 실제 흐름: 톰캣 스레드가 컨트롤러→서비스를 타고 들어가 4개 async로 외부
//   API 요청을 던지고 첫 suspend 지점(awaitSingle 등)에 도달하는 순간 스레드는 곧바로 풀에 반납되어
//   다른 요청을 처리하러 간다. 이후 각 API 응답은 Netty 이벤트 루프 스레드가 수신하며 그 코루틴을
//   재개시키고, 4개가 모두 끝나면 그 결과로 응답이 조립되어 서블릿 컨테이너의 비동기 디스패치를 통해
//   (원래와 다른 톰캣 스레드가 이어받을 수도 있음) 최종 HTTP 응답이 나간다.
//
// - 정리하면 두 방식 모두 "4개 API가 병렬로 호출되는 것" 자체는 동일하다(Netty가 논블로킹으로 처리).
//   차이는 오직 "요청을 받은 톰캣 스레드가 그 대기 시간 동안 반납되어 다른 요청을 처리할 수 있는가"이며,
//   이게 NFR2("호출 대기 중 서버 스레드를 점유하지 않아야 한다")가 요구하는 지점이다.
//
// 스레드 흐름 비교 (4개 API 중 가장 느린 응답이 180ms 걸린다고 가정):
//
//   [suspend fun]                                 [fun + runBlocking]
//   t0      tomcat-handler-1 → 4개 요청 전송        t0      tomcat-handler-1 → 4개 요청 전송
//   t0+1ms  await 진입 → 스레드 반납, 풀로 복귀       t0+1ms  await 진입 → runBlocking이 스레드를 안 놔줌
//           (다른 요청을 받으러 감)                          → tomcat-handler-1은 여기서 그냥 park(대기)
//   ~180ms  reactor-http-nio-* 들이 각 응답 수신              (이 시간 동안 다른 요청 못 받음)
//           하고 코루틴을 그때그때 재개               ~180ms  reactor-http-nio-* 들이 응답 수신 (동일)
//   t0+180ms 마지막 응답 도착 → 결과 조립               t0+180ms 마지막 응답 도착 → runBlocking 깨어남
//           (재개시킨 Netty 스레드 위에서 실행)                 → tomcat-handler-1이 이어서 실행
//
//   → tomcat-handler-1의 실제 점유 시간: suspend fun은 ~1ms, runBlocking은 ~180ms(요청 전체 구간).
//   → 블로킹이 발생하는 지점은 오직 "톰캣 스레드가 await 이후에도 반납되지 않고 그대로 대기하는가"이며,
//     외부 API 응답을 실제로 기다리는 지점(Netty 스레드)은 두 방식에서 동일하다.
@RestController
@RequestMapping("/api/dashboard")
class DashboardController(
	private val dashboardService: DashboardService,
) {

	@GetMapping("/parallel")
	suspend fun getParallel(
		@RequestParam city: String,
		@RequestParam countryName: String,
		@RequestParam countryCode: String,
		@RequestParam baseCurrency: String,
	): DashboardResponse = dashboardService.fetchParallel(city, countryName, countryCode, baseCurrency)

	@GetMapping("/sequential")
	suspend fun getSequential(
		@RequestParam city: String,
		@RequestParam countryName: String,
		@RequestParam countryCode: String,
		@RequestParam baseCurrency: String,
	): DashboardResponse = dashboardService.fetchSequential(city, countryName, countryCode, baseCurrency)

	@GetMapping("/parallel-resilient")
	suspend fun getParallelResilient(
		@RequestParam city: String,
		@RequestParam countryName: String,
		@RequestParam countryCode: String,
		@RequestParam baseCurrency: String,
	): DashboardResilientResponse = dashboardService.fetchParallelResilient(city, countryName, countryCode, baseCurrency)

	@GetMapping("/parallel-chained")
	suspend fun getParallelChained(
		@RequestParam city: String,
		@RequestParam countryName: String,
	): DashboardResponse = dashboardService.fetchParallelChained(city, countryName)

	@GetMapping("/sequential-chained")
	suspend fun getSequentialChained(
		@RequestParam city: String,
		@RequestParam countryName: String,
	): DashboardResponse = dashboardService.fetchSequentialChained(city, countryName)
}
