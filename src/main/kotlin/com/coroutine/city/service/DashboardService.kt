package com.coroutine.city.service

import com.coroutine.city.client.CountryClient
import com.coroutine.city.client.ExchangeRateClient
import com.coroutine.city.client.HolidayClient
import com.coroutine.city.client.WeatherClient
import com.coroutine.city.dto.DashboardResponse
import com.coroutine.city.dto.TimingMs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service

@Service
class DashboardService(
	private val weatherClient: WeatherClient,
	private val countryClient: CountryClient,
	private val holidayClient: HolidayClient,
	private val exchangeRateClient: ExchangeRateClient,
) {

	// async 4개를 동시에 띄우고 await으로 모은다 → 총 소요시간은 4개의 합이 아니라 가장 느린 것 하나.
	// coroutineScope는 fail-fast: 하나가 예외를 던지면 나머지 전부 취소되고(이미 끝난 것도 버려짐)
	// 그 예외만 호출자로 전파된다 — 지금은 잡는 핸들러가 없어 HTTP 500. Phase 2 supervisorScope와 대조군.
	suspend fun fetchParallel(
		city: String,
		countryName: String,
		countryCode: String,
		baseCurrency: String,
	): DashboardResponse = coroutineScope {
		val totalStart = System.currentTimeMillis()

		// async는 새 스레드를 만들지 않고 호출한 스레드에서 그대로 실행을 시작한다(non-blocking) →
		// 이 4줄이 끝날 때쯤 4개의 HTTP 요청이 이미 다 나간 상태. 각 호출이 내부의 awaitSingle()에서
		// suspend되는 순간 스레드는 완전히 반납되어 다른 코루틴이 이어받는다 — 이 async 전용으로
		// 스레드가 붙잡혀 대기하는 게 아니다(@Async와의 차이점).
		val weatherDeferred = async { timed { weatherClient.getWeather(city) } }
		val countryDeferred = async { timed { countryClient.getCountry(countryName) } }
		val holidaysDeferred = async { timed { holidayClient.getHolidays(countryCode) } }
		val exchangeRateDeferred = async { timed { exchangeRateClient.getRates(baseCurrency) } }

		// await도 결과가 준비될 때까지 스레드가 아니라 코루틴만 멈춘다. 응답이 오면 그때 비어있는
		// 아무 스레드나 이어받아 재개한다(원래 스레드로 돌아간다는 보장 없음). 이미 4개가 병렬로
		// 진행 중이므로 순서대로 await해도 총 대기 시간은 가장 늦게 끝나는 것 하나에 수렴한다.
		// 타입이 서로 달라 List<Deferred<T>>로 못 묶어서 awaitAll() 대신 개별 await로 fan-in.
		val (weather, weatherMs) = weatherDeferred.await()
		val (country, countryMs) = countryDeferred.await()
		val (holidays, holidaysMs) = holidaysDeferred.await()
		val (exchangeRate, exchangeRateMs) = exchangeRateDeferred.await()

		DashboardResponse(
			weather = weather,
			country = country,
			holidays = holidays,
			exchangeRate = exchangeRate,
			timingMs = TimingMs(
				weather = weatherMs,
				country = countryMs,
				holidays = holidaysMs,
				exchangeRate = exchangeRateMs,
				total = System.currentTimeMillis() - totalStart,
			),
		)
	}

	// async 없이 suspend fun을 순서대로 호출만 한다 → 자연스럽게 순차 실행, 총 소요시간은 4개의 합.
	suspend fun fetchSequential(
		city: String,
		countryName: String,
		countryCode: String,
		baseCurrency: String,
	): DashboardResponse {
		val totalStart = System.currentTimeMillis()

		val (weather, weatherMs) = timed { weatherClient.getWeather(city) }
		val (country, countryMs) = timed { countryClient.getCountry(countryName) }
		val (holidays, holidaysMs) = timed { holidayClient.getHolidays(countryCode) }
		val (exchangeRate, exchangeRateMs) = timed { exchangeRateClient.getRates(baseCurrency) }

		return DashboardResponse(
			weather = weather,
			country = country,
			holidays = holidays,
			exchangeRate = exchangeRate,
			timingMs = TimingMs(
				weather = weatherMs,
				country = countryMs,
				holidays = holidaysMs,
				exchangeRate = exchangeRateMs,
				total = System.currentTimeMillis() - totalStart,
			),
		)
	}

	// suspend 블록을 실행하고 (결과, 소요시간ms)를 함께 반환하는 헬퍼. 8곳의 반복 측정 로직을 재사용.
	private suspend fun <T> timed(block: suspend () -> T): Pair<T, Long> {
		val start = System.currentTimeMillis()
		val result = block()
		return result to System.currentTimeMillis() - start
	}
}
