package com.coroutine.city.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExchangeRateResponse(
	val base: String,
	val date: String,
	val rates: Map<String, Double>,
)
