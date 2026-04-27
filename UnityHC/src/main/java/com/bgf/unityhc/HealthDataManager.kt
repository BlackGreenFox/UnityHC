package com.example.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.*
import java.time.Instant
import java.time.temporal.ChronoUnit

object HealthDataManager {

    fun readSteps(client: HealthConnectClient, callback: (String) -> Unit) {

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val end = Instant.now()
                val start = end.minus(1, ChronoUnit.DAYS)

                val response = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                )

                val steps = response[StepsRecord.COUNT_TOTAL] ?: 0

                withContext(Dispatchers.Main) {
                    callback("{\"steps\":$steps}")
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback("{\"error\":\"${e.message}\"}")
                }
            }
        }
    }
}