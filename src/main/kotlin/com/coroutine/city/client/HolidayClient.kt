package com.coroutine.city.client

import com.coroutine.city.dto.Holiday
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Year

@Component
class HolidayClient(
	private val holidayWebClient: WebClient,
) {

	suspend fun getHolidays(countryCode: String): List<Holiday> {
		// 순차/병렬 응답 속도 차이를 체감하기 위해 느린 외부 API를 흉내낸 인위적 지연.
		// 실제 API 응답과 무관 — 비교 실험이 끝나면 제거할 것.
		delay(2000)
		return holidayWebClient.get()
			.uri("/PublicHolidays/{year}/{countryCode}", Year.now().value, countryCode)
			.retrieve()
			.bodyToMono(object : ParameterizedTypeReference<List<Holiday>>() {})
			.awaitSingle()
	}
}
