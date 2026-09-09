package com.coroutine.city.client

import com.coroutine.city.dto.ExchangeRateResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class ExchangeRateClientBlocking(
	private val exchangeRateRestTemplate: RestTemplate,
) {

	suspend fun getRates(baseCurrency: String): ExchangeRateResponse =
		// RestTemplate은 WebClient(awaitSingle)와 달리 응답이 올 때까지 호출 스레드를 그대로
		// 점유하는 동기 블로킹 호출이라, withContext(Dispatchers.IO)로 감싸 그 블로킹을
		// 코루틴 디스패처가 아닌 별도 IO 스레드 풀로 격리한다.
		withContext(Dispatchers.IO) {
			// ExchangeRateClient의 delay(3000)과 동일한 인위적 지연을 블로킹 클라이언트에서는
			// Thread.sleep으로 재현 — 이 스레드는 Dispatchers.IO 풀 소속이라 톰캣 스레드는 아니다.
			Thread.sleep(3000)
			exchangeRateRestTemplate.getForObject(
				"/latest?from={baseCurrency}",
				ExchangeRateResponse::class.java,
				baseCurrency,
			)!!
		}
}
