package com.coroutine.city.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig(
	private val properties: ExternalApiProperties,
) {

	@Bean
	fun weatherWebClient(): WebClient =
		WebClient.builder().baseUrl(properties.weather.baseUrl).build()

	@Bean
	fun countryWebClient(): WebClient =
		WebClient.builder().baseUrl(properties.country.baseUrl).build()

	@Bean
	fun holidayWebClient(): WebClient =
		WebClient.builder().baseUrl(properties.holiday.baseUrl).build()

	@Bean
	fun exchangeRateWebClient(): WebClient =
		WebClient.builder().baseUrl(properties.exchangeRate.baseUrl).build()
}
