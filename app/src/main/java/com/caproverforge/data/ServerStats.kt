package com.caproverforge.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One metric over time; [times] are epoch seconds, oldest first. */
data class Series(val times: List<Long>, val values: List<Double>) {
    val last: Double? get() = values.lastOrNull()
    val isEmpty: Boolean get() = values.isEmpty()

    companion object {
        val EMPTY = Series(emptyList(), emptyList())
    }
}

data class DiskUsage(val usedGiB: Double, val totalGiB: Double) {
    val fraction: Double get() = if (totalGiB <= 0) 0.0 else (usedGiB / totalGiB).coerceIn(0.0, 1.0)
}

enum class StatsRange(val label: String, val seconds: Int, val points: Int) {
    FiveMinutes("5 min", 300, 60),
    OneHour("1 h", 3_600, 120),
    SixHours("6 h", 21_600, 120),
    OneDay("24 h", 86_400, 144),
}

data class ServerStats(
    val hostname: String?,
    val os: String?,
    val cores: Int?,
    val netDataVersion: String?,
    val cpuPercent: Series,
    val memoryPercent: Series,
    val memoryUsedMiB: Double?,
    val memoryTotalMiB: Double?,
    val load1: Series,
    val load5: Double?,
    val load15: Double?,
    val netInKbps: Series,
    val netOutKbps: Series,
    val disk: DiskUsage?,
)

/** Reads NetData v1 `data` responses (`format=json`, options `seconds|flip|abs`). */
object NetDataParser {
    data class Table(val labels: List<String>, val times: List<Long>, val columns: List<List<Double>>) {
        fun column(name: String): List<Double>? = labels.indexOf(name).takeIf { it >= 0 }?.let { columns[it] }
    }

    fun table(json: JsonElement): Table {
        val obj = json as? JsonObject ?: return Table(emptyList(), emptyList(), emptyList())
        val labels = (obj["labels"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
        val rows = (obj["data"] as? JsonArray)?.mapNotNull { it as? JsonArray }.orEmpty()
            .sortedBy { (it.firstOrNull() as? JsonPrimitive)?.longOrNull ?: 0L }
        val dims = labels.drop(1) // first label is "time"
        val times = rows.map { (it.firstOrNull() as? JsonPrimitive)?.longOrNull ?: 0L }
        val columns = dims.indices.map { d ->
            rows.map { row -> (row.getOrNull(d + 1) as? JsonPrimitive)?.doubleOrNull ?: 0.0 }
        }
        return Table(dims, times, columns)
    }

    /** Sum of every dimension, e.g. total CPU % from user/system/iowait/... (idle excluded). */
    fun sum(json: JsonElement, exclude: Set<String> = setOf("idle")): Series {
        val t = table(json)
        val keep = t.labels.indices.filter { t.labels[it] !in exclude }
        return Series(t.times, t.times.indices.map { r -> keep.sumOf { t.columns[it][r] } })
    }

    fun dimension(json: JsonElement, vararg names: String): Series {
        val t = table(json)
        val col = names.firstNotNullOfOrNull { t.column(it) } ?: return Series.EMPTY
        return Series(t.times, col)
    }

    /** `system.ram` (MiB): used / (free + used + cached + buffers) as a percentage. */
    fun memory(json: JsonElement): Triple<Series, Double?, Double?> {
        val t = table(json)
        val used = t.column("used") ?: return Triple(Series.EMPTY, null, null)
        val totals = t.times.indices.map { r -> t.columns.sumOf { it[r] } }
        val pct = used.indices.map { r -> if (totals[r] > 0) used[r] / totals[r] * 100 else 0.0 }
        return Triple(Series(t.times, pct), used.lastOrNull(), totals.lastOrNull())
    }

    /** `disk_space._` (GiB) for the root mount: avail / used / reserved for root. */
    fun disk(json: JsonElement): DiskUsage? {
        val t = table(json)
        val used = t.column("used")?.lastOrNull() ?: return null
        val total = t.labels.indices.sumOf { t.columns[it].lastOrNull() ?: 0.0 }
        return if (total > 0) DiskUsage(used, total) else null
    }
}

suspend fun CapRoverRepository.serverStats(range: StatsRange = StatsRange.FiveMinutes): ServerStats = coroutineScope {
    fun chart(name: String) = mapOf(
        "chart" to name,
        "after" to "-${range.seconds}",
        "points" to "${range.points}",
        "group" to "average",
        "format" to "json",
        "options" to "seconds|flip|abs",
    )
    val info = async { runCatching { api.netData("/api/v1/info") as? JsonObject }.getOrNull() }
    val cpu = async { api.netData("/api/v1/data", chart("system.cpu")) }
    val ram = async { api.netData("/api/v1/data", chart("system.ram")) }
    val load = async { runCatching { api.netData("/api/v1/data", chart("system.load")) }.getOrNull() }
    val net = async { runCatching { api.netData("/api/v1/data", chart("system.net")) }.getOrNull() }
    val disk = async {
        runCatching {
            api.netData("/api/v1/data", chart("disk_space._") + ("points" to "1") + ("after" to "-60"))
        }.getOrNull()
    }

    val (memPct, memUsed, memTotal) = NetDataParser.memory(ram.await())
    val loadJson = load.await()
    val netJson = net.await()
    val infoJson = info.await()
    ServerStats(
        hostname = (infoJson?.get("mirrored_hosts") as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull,
        os = listOfNotNull(
            infoJson?.get("os_name")?.jsonPrimitive?.contentOrNull,
            infoJson?.get("os_version")?.jsonPrimitive?.contentOrNull,
        ).joinToString(" ").ifBlank { null },
        cores = infoJson?.get("cores_total")?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
        netDataVersion = infoJson?.get("version")?.jsonPrimitive?.contentOrNull,
        cpuPercent = NetDataParser.sum(cpu.await()),
        memoryPercent = memPct,
        memoryUsedMiB = memUsed,
        memoryTotalMiB = memTotal,
        load1 = loadJson?.let { NetDataParser.dimension(it, "load1") } ?: Series.EMPTY,
        load5 = loadJson?.let { NetDataParser.dimension(it, "load5").last },
        load15 = loadJson?.let { NetDataParser.dimension(it, "load15").last },
        netInKbps = netJson?.let { NetDataParser.dimension(it, "received", "InOctets") } ?: Series.EMPTY,
        netOutKbps = netJson?.let { NetDataParser.dimension(it, "sent", "OutOctets") } ?: Series.EMPTY,
        disk = disk.await()?.let { NetDataParser.disk(it) },
    )
}
