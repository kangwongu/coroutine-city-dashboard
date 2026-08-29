package com.coroutine.city.client

import com.coroutine.city.dto.CountryResponse
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class CountryClient(
	private val countryWebClient: WebClient,
) {

	suspend fun getCountry(name: String): CountryResponse =
		countryWebClient.get()
			.uri { uriBuilder ->
				uriBuilder.path("/name")
					.queryParam("q", name)
					.build()
			}
			.retrieve()
			.bodyToMono(SearchResponse::class.java)
			.awaitSingle()
			.data.objects.first()

	// REST Countries v5는 검색 결과를 {"data": {"objects": [...]}}로 감싸서 반환한다.
	@JsonIgnoreProperties(ignoreUnknown = true)
	private data class SearchResponse(val data: Data) {
		@JsonIgnoreProperties(ignoreUnknown = true)
		data class Data(val objects: List<CountryResponse>)
	}
}
