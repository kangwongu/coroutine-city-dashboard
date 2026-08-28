package com.coroutine.city.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CountryResponse(
	val name: Name,
	val cca2: String,
	val currencies: Map<String, Currency>?,
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	data class Name(val common: String, val official: String)

	@JsonIgnoreProperties(ignoreUnknown = true)
	data class Currency(val name: String, val symbol: String?)
}
