package com.bgf.unityhc

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.reflect.KClass

/**
 * Stateless helpers around [HealthConnectClient] used by [HealthConnectPlugin].
 *
 * Every public method returns a JSON string ready to be sent to Unity via
 * `UnityPlayer.UnitySendMessage`.
 */
internal object HealthDataManager {

    // ---------- tracking ----------

    private var trackingJob: Job? = null
    private val trackingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startTracking(
        client: HealthConnectClient,
        intervalMillis: Long,
        onUpdate: (String) -> Unit
    ) {
        stopTracking()
        trackingJob = trackingScope.launch {
            while (isActive) {
                onUpdate(todaySummary(client))
                delay(intervalMillis.coerceAtLeast(500L))
            }
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    // ---------- aggregate read (today) ----------

    suspend fun todaySummary(client: HealthConnectClient): String {
        return runCatching {
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val now = Instant.now()
            val filter = TimeRangeFilter.between(start, now)

            val agg: AggregationResult = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                        HeartRateRecord.BPM_AVG,
                        HeartRateRecord.BPM_MIN,
                        HeartRateRecord.BPM_MAX,
                    ),
                    timeRangeFilter = filter,
                )
            )

            JSONObject().apply {
                put("ok", true)
                put("startMillis", start.toEpochMilli())
                put("endMillis", now.toEpochMilli())
                put("steps", agg[StepsRecord.COUNT_TOTAL] ?: 0L)
                put("distanceMeters", agg[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0)
                put(
                    "activeKcal",
                    agg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
                )
                put(
                    "totalKcal",
                    agg[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
                )
                put("hrAvg", agg[HeartRateRecord.BPM_AVG] ?: JSONObject.NULL)
                put("hrMin", agg[HeartRateRecord.BPM_MIN] ?: JSONObject.NULL)
                put("hrMax", agg[HeartRateRecord.BPM_MAX] ?: JSONObject.NULL)
            }.toString()
        }.getOrElse { errorJson(it.message) }
    }

    // ---------- raw record read ----------

    /** Supported [recordType] values: "Steps", "HeartRate", "Distance",
     *  "ActiveCaloriesBurned", "TotalCaloriesBurned" (case-insensitive). */
    suspend fun readRecords(
        client: HealthConnectClient,
        recordType: String,
        start: Instant,
        end: Instant
    ): String {
        val type: KClass<out Record> = when (recordType.lowercase()) {
            "steps" -> StepsRecord::class
            "heartrate", "heart_rate" -> HeartRateRecord::class
            "distance" -> DistanceRecord::class
            "activecaloriesburned", "active_calories_burned", "activekcal" -> ActiveCaloriesBurnedRecord::class
            "totalcaloriesburned", "total_calories_burned", "totalkcal" -> TotalCaloriesBurnedRecord::class
            else -> return errorJson("Unsupported recordType: $recordType")
        }

        return runCatching {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            )
            val arr = JSONArray()
            for (record in response.records) {
                arr.put(recordToJson(record))
            }
            JSONObject()
                .put("ok", true)
                .put("recordType", recordType)
                .put("count", response.records.size)
                .put("records", arr)
                .toString()
        }.getOrElse { errorJson(it.message) }
    }

    private fun recordToJson(record: Record): JSONObject {
        val o = JSONObject()
        when (record) {
            is StepsRecord -> {
                o.put("type", "Steps")
                    .put("startMillis", record.startTime.toEpochMilli())
                    .put("endMillis", record.endTime.toEpochMilli())
                    .put("count", record.count)
            }
            is HeartRateRecord -> {
                val samples = JSONArray()
                for (s in record.samples) {
                    samples.put(
                        JSONObject()
                            .put("timeMillis", s.time.toEpochMilli())
                            .put("bpm", s.beatsPerMinute)
                    )
                }
                o.put("type", "HeartRate")
                    .put("startMillis", record.startTime.toEpochMilli())
                    .put("endMillis", record.endTime.toEpochMilli())
                    .put("samples", samples)
            }
            is DistanceRecord -> {
                o.put("type", "Distance")
                    .put("startMillis", record.startTime.toEpochMilli())
                    .put("endMillis", record.endTime.toEpochMilli())
                    .put("meters", record.distance.inMeters)
            }
            is ActiveCaloriesBurnedRecord -> {
                o.put("type", "ActiveCaloriesBurned")
                    .put("startMillis", record.startTime.toEpochMilli())
                    .put("endMillis", record.endTime.toEpochMilli())
                    .put("kcal", record.energy.inKilocalories)
            }
            is TotalCaloriesBurnedRecord -> {
                o.put("type", "TotalCaloriesBurned")
                    .put("startMillis", record.startTime.toEpochMilli())
                    .put("endMillis", record.endTime.toEpochMilli())
                    .put("kcal", record.energy.inKilocalories)
            }
            else -> o.put("type", record::class.simpleName ?: "Unknown")
        }
        return o
    }

    // ---------- writes ----------

    suspend fun insertSteps(
        client: HealthConnectClient,
        count: Long, startMillis: Long, endMillis: Long
    ): String = insertOne(client) {
        StepsRecord(
            count = count,
            startTime = Instant.ofEpochMilli(startMillis),
            startZoneOffset = currentZoneOffset(),
            endTime = Instant.ofEpochMilli(endMillis),
            endZoneOffset = currentZoneOffset(),
            metadata = Metadata.manualEntry(),
        )
    }

    suspend fun insertHeartRate(
        client: HealthConnectClient,
        bpm: Long, timestampMillis: Long
    ): String = insertOne(client) {
        val time = Instant.ofEpochMilli(timestampMillis)
        HeartRateRecord(
            startTime = time,
            startZoneOffset = currentZoneOffset(),
            endTime = time.plusSeconds(1),
            endZoneOffset = currentZoneOffset(),
            samples = listOf(
                HeartRateRecord.Sample(time = time, beatsPerMinute = bpm)
            ),
            metadata = Metadata.manualEntry(),
        )
    }

    suspend fun insertDistance(
        client: HealthConnectClient,
        meters: Double, startMillis: Long, endMillis: Long
    ): String = insertOne(client) {
        DistanceRecord(
            startTime = Instant.ofEpochMilli(startMillis),
            startZoneOffset = currentZoneOffset(),
            endTime = Instant.ofEpochMilli(endMillis),
            endZoneOffset = currentZoneOffset(),
            distance = Length.meters(meters),
            metadata = Metadata.manualEntry(),
        )
    }

    suspend fun insertActiveCalories(
        client: HealthConnectClient,
        kcal: Double, startMillis: Long, endMillis: Long
    ): String = insertOne(client) {
        ActiveCaloriesBurnedRecord(
            startTime = Instant.ofEpochMilli(startMillis),
            startZoneOffset = currentZoneOffset(),
            endTime = Instant.ofEpochMilli(endMillis),
            endZoneOffset = currentZoneOffset(),
            energy = Energy.kilocalories(kcal),
            metadata = Metadata.manualEntry(),
        )
    }

    suspend fun insertTotalCalories(
        client: HealthConnectClient,
        kcal: Double, startMillis: Long, endMillis: Long
    ): String = insertOne(client) {
        TotalCaloriesBurnedRecord(
            startTime = Instant.ofEpochMilli(startMillis),
            startZoneOffset = currentZoneOffset(),
            endTime = Instant.ofEpochMilli(endMillis),
            endZoneOffset = currentZoneOffset(),
            energy = Energy.kilocalories(kcal),
            metadata = Metadata.manualEntry(),
        )
    }

    private suspend inline fun insertOne(
        client: HealthConnectClient,
        crossinline build: () -> Record
    ): String = runCatching {
        val response = client.insertRecords(listOf(build()))
        JSONObject()
            .put("ok", true)
            .put("ids", JSONArray(response.recordIdsList))
            .toString()
    }.getOrElse { errorJson(it.message) }

    // ---------- helpers ----------

    private fun currentZoneOffset(): ZoneOffset =
        ZoneId.systemDefault().rules.getOffset(Instant.now())

    private fun errorJson(message: String?): String =
        JSONObject().put("ok", false).put("error", message ?: "unknown").toString()
}
