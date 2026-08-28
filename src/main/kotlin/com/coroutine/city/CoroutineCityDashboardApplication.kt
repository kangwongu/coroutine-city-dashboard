package com.coroutine.city

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CoroutineCityDashboardApplication

fun main(args: Array<String>) {
	runApplication<CoroutineCityDashboardApplication>(*args)
}
