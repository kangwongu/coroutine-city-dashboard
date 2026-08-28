package com.coroutine.city.client

import com.coroutine.city.config.ExternalApiProperties
import com.coroutine.city.dto.WeatherResponse
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class WeatherClient(
	private val weatherWebClient: WebClient,
	private val properties: ExternalApiProperties,
) {

	suspend fun getWeather(city: String): WeatherResponse =
		weatherWebClient.get()
			.uri { uriBuilder ->
				uriBuilder.path("/data/2.5/weather")
					.queryParam("q", city)
					.queryParam("appid", properties.weather.apiKey)
					.queryParam("units", "metric")
					.build()
			}
			.retrieve()
			.bodyToMono(WeatherResponse::class.java)
			.awaitSingle()
}
