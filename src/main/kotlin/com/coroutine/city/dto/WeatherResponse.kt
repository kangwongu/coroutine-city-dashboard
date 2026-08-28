package com.coroutine.city.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class WeatherResponse(
	val name: String,
	val main: Main,
	val weather: List<WeatherDetail>,
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	data class Main(val temp: Double)

	@JsonIgnoreProperties(ignoreUnknown = true)
	data class WeatherDetail(val description: String)
}
