package com.coroutine.city.client

import com.coroutine.city.dto.ExchangeRateResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class ExchangeRateClient(
	private val exchangeRateWebClient: WebClient,
) {

	suspend fun getRates(baseCurrency: String): ExchangeRateResponse {
		// 순차/병렬 응답 속도 차이를 체감하기 위해 느린 외부 API를 흉내낸 인위적 지연.
		// 실제 API 응답과 무관 — 비교 실험이 끝나면 제거할 것.
		delay(3000)
		return exchangeRateWebClient.get()
			.uri { uriBuilder ->
				uriBuilder.path("/latest")
					.queryParam("from", baseCurrency)
					.build()
			}
			.retrieve()
			.bodyToMono(ExchangeRateResponse::class.java)
			.awaitSingle()
	}
}
