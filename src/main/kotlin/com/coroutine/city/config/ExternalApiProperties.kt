package com.coroutine.city.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "external-api")
data class ExternalApiProperties(
	val weather: Weather,
	val country: Country,
	val holiday: Holiday,
	val exchangeRate: ExchangeRate,
) {
	data class Weather(val baseUrl: String, val apiKey: String)
	data class Country(val baseUrl: String)
	data class Holiday(val baseUrl: String)
	data class ExchangeRate(val baseUrl: String)
}
