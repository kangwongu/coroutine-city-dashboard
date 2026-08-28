package com.coroutine.city.client

import com.coroutine.city.dto.ExchangeRateResponse
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class ExchangeRateClient(
	private val exchangeRateWebClient: WebClient,
) {

	suspend fun getRates(baseCurrency: String): ExchangeRateResponse =
		exchangeRateWebClient.get()
			.uri { uriBuilder ->
				uriBuilder.path("/latest")
					.queryParam("from", baseCurrency)
					.build()
			}
			.retrieve()
			.bodyToMono(ExchangeRateResponse::class.java)
			.awaitSingle()
}
