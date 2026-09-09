package com.coroutine.city.client

import com.coroutine.city.config.ExternalApiProperties
import com.coroutine.city.dto.WeatherResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class WeatherClientBlocking(
	private val weatherRestTemplate: RestTemplate,
	private val properties: ExternalApiProperties,
) {

	suspend fun getWeather(city: String): WeatherResponse =
		// WebClient는 논블로킹이라 awaitSingle()이 스레드를 점유하지 않고 코루틴을 매달아 두는 반면,
		// RestTemplate은 응답이 올 때까지 호출 스레드를 그대로 붙잡는 동기 블로킹 호출이다.
		// withContext(Dispatchers.IO)로 감싸 그 블로킹이 코루틴 디스패처가 아닌 별도 IO 스레드
		// 풀에서만 일어나게 격리한다.
		withContext(Dispatchers.IO) {
			weatherRestTemplate.getForObject(
				"/data/2.5/weather?q={city}&appid={appid}&units=metric",
				WeatherResponse::class.java,
				city,
				properties.weather.apiKey,
			)!!
		}
}
