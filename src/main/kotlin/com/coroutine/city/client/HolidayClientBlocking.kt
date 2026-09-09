package com.coroutine.city.client

import com.coroutine.city.dto.Holiday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Year

@Component
class HolidayClientBlocking(
	private val holidayRestTemplate: RestTemplate,
) {

	suspend fun getHolidays(countryCode: String): List<Holiday> =
		// RestTemplate은 WebClient(awaitSingle)와 달리 응답이 올 때까지 호출 스레드를 그대로
		// 점유하는 동기 블로킹 호출이라, withContext(Dispatchers.IO)로 감싸 그 블로킹을
		// 코루틴 디스패처가 아닌 별도 IO 스레드 풀로 격리한다.
		withContext(Dispatchers.IO) {
			// HolidayClient의 delay(2000)과 동일한 인위적 지연을 블로킹 클라이언트에서는
			// Thread.sleep으로 재현 — 이 스레드는 Dispatchers.IO 풀 소속이라 톰캣 스레드는 아니다.
			Thread.sleep(2000)
			holidayRestTemplate.exchange(
				"/PublicHolidays/{year}/{countryCode}",
				HttpMethod.GET,
				null,
				object : ParameterizedTypeReference<List<Holiday>>() {},
				Year.now().value,
				countryCode,
			).body!!
		}
}
