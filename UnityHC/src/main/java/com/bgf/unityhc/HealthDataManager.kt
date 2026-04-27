package com.bgf.unityhc

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object HealthDataManager {

    private var tracking = false
    private var scope: CoroutineScope? = null

    fun startTracking(client: HealthConnectClient) {
        if (tracking) return

        tracking = true
        scope = CoroutineScope(Dispatchers.IO)

        scope?.launch {
            while (tracking) {

                val json = read(client)

                withContext(Dispatchers.Main) {
                    HealthConnectPlugin.sendUnity(
                        "OnHealthSummaryReceived",
                        json
                    )
                }

                delay(3000)
            }
        }
    }

    fun stopTracking() {
        tracking = false
        scope?.cancel()
    }

    private suspend fun read(client: HealthConnectClient): String {
        return runCatching {

            val zone = ZoneId.systemDefault()
            val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val now = Instant.now()

            val res = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL
                    ),
                    timeRangeFilter = TimeRangeFilter.between(start, now)
                )
            )

            JSONObject()
                .put("ok", true)
                .put("steps", res[StepsRecord.COUNT_TOTAL] ?: 0)
                .put("distanceMeters", res[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0)
                .toString()

        }.getOrElse {
            JSONObject()
                .put("ok", false)
                .put("error", it.message)
                .toString()
        }
    }
}