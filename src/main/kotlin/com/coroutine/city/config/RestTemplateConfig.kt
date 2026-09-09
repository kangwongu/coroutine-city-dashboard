package com.coroutine.city.config

import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class RestTemplateConfig(
	private val properties: ExternalApiProperties,
) {

	@Bean
	fun weatherRestTemplate(builder: RestTemplateBuilder): RestTemplate =
		builder.baseUri(properties.weather.baseUrl).build()

	@Bean
	fun countryRestTemplate(builder: RestTemplateBuilder): RestTemplate =
		builder.baseUri(properties.country.baseUrl)
			.defaultHeader("Authorization", "Bearer ${properties.country.apiKey}")
			.build()

	@Bean
	fun holidayRestTemplate(builder: RestTemplateBuilder): RestTemplate =
		builder.baseUri(properties.holiday.baseUrl).build()

	@Bean
	fun exchangeRateRestTemplate(builder: RestTemplateBuilder): RestTemplate =
		builder.baseUri(properties.exchangeRate.baseUrl).build()
}
