package com.coroutine.city.client

import com.coroutine.city.dto.CountryResponse
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class CountryClientBlocking(
	private val countryRestTemplate: RestTemplate,
) {

	suspend fun getCountry(name: String): CountryResponse =
		// RestTemplate은 WebClient(awaitSingle)와 달리 응답이 올 때까지 호출 스레드를 그대로
		// 점유하는 동기 블로킹 호출이라, withContext(Dispatchers.IO)로 감싸 그 블로킹을
		// 코루틴 디스패처가 아닌 별도 IO 스레드 풀로 격리한다.
		withContext(Dispatchers.IO) {
			countryRestTemplate.getForObject(
				"/name?q={name}",
				SearchResponse::class.java,
				name,
			)!!.data.objects.first()
		}

	// REST Countries v5는 검색 결과를 {"data": {"objects": [...]}}로 감싸서 반환한다.
	@JsonIgnoreProperties(ignoreUnknown = true)
	private data class SearchResponse(val data: Data) {
		@JsonIgnoreProperties(ignoreUnknown = true)
		data class Data(val objects: List<CountryResponse>)
	}
}
