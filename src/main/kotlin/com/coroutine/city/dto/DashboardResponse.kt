package com.coroutine.city.dto

data class DashboardResponse(
	val weather: WeatherResponse,
	val country: CountryResponse,
	val holidays: List<Holiday>,
	val exchangeRate: ExchangeRateResponse,
	val timingMs: TimingMs,
)
