package com.coroutine.city.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class CountryResponse(
	val names: Names,
	val codes: Codes,
	val currencies: List<Currency>?,
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	data class Names(val common: String, val official: String)

	@JsonIgnoreProperties(ignoreUnknown = true)
	data class Codes(@JsonProperty("alpha_2") val alpha2: String)

	@JsonIgnoreProperties(ignoreUnknown = true)
	data class Currency(val code: String, val name: String, val symbol: String?)
}
