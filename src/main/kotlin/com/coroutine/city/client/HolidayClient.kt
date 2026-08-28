package com.coroutine.city.client

import com.coroutine.city.dto.Holiday
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Year

@Component
class HolidayClient(
	private val holidayWebClient: WebClient,
) {

	suspend fun getHolidays(countryCode: String): List<Holiday> =
		holidayWebClient.get()
			.uri("/PublicHolidays/{year}/{countryCode}", Year.now().value, countryCode)
			.retrieve()
			.bodyToMono(object : ParameterizedTypeReference<List<Holiday>>() {})
			.awaitSingle()
}
