package com.coroutine.city.client

import com.coroutine.city.dto.CountryResponse
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class CountryClient(
	private val countryWebClient: WebClient,
) {

	suspend fun getCountry(name: String): CountryResponse =
		countryWebClient.get()
			.uri("/name/{name}", name)
			.retrieve()
			.bodyToMono(object : ParameterizedTypeReference<List<CountryResponse>>() {})
			.awaitSingle()
			.first()
}
