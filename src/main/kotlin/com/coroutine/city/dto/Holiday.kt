package com.coroutine.city.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Holiday(
	val date: String,
	val localName: String,
	val name: String,
)
