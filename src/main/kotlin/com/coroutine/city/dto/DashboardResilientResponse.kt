package com.coroutine.city.dto

data class DashboardResilientResponse(
	val weather: WeatherResponse?,
	val weatherError: ApiError?,
	val country: CountryResponse?,
	val countryError: ApiError?,
	val holidays: List<Holiday>?,
	val holidaysError: ApiError?,
	val exchangeRate: ExchangeRateResponse?,
	val exchangeRateError: ApiError?,
	val timingMs: TimingMs,
)
