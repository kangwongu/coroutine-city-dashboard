package com.coroutine.city.controller

import com.coroutine.city.dto.DashboardResponse
import com.coroutine.city.service.DashboardService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(
	private val dashboardService: DashboardService,
) {

	@GetMapping("/parallel")
	suspend fun getParallel(
		@RequestParam city: String,
		@RequestParam countryName: String,
		@RequestParam countryCode: String,
		@RequestParam baseCurrency: String,
	): DashboardResponse = dashboardService.fetchParallel(city, countryName, countryCode, baseCurrency)

	@GetMapping("/sequential")
	suspend fun getSequential(
		@RequestParam city: String,
		@RequestParam countryName: String,
		@RequestParam countryCode: String,
		@RequestParam baseCurrency: String,
	): DashboardResponse = dashboardService.fetchSequential(city, countryName, countryCode, baseCurrency)
}
