package com.caproverforge.data

import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object ServerAddress {
    /**
     * Accepts what people paste: `captain.example.com`, `https://captain.example.com/#/apps`,
     * `http://1.2.3.4:3000/api/v2` … and returns the scheme + host (+ port) base URL.
     */
    fun normalize(input: String): String {
        var s = input.trim()
        require(s.isNotEmpty()) { "Enter your CapRover dashboard address." }
        if (!s.contains("://")) s = "https://$s"
        val scheme = s.substringBefore("://").lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") { "Address must start with http:// or https://" }
        val rest = s.substringAfter("://")
        val hostPort = rest.takeWhile { it != '/' && it != '#' && it != '?' }
        require(hostPort.isNotEmpty() && !hostPort.contains(' ')) { "“$input” is not a valid address." }
        return "$scheme://${hostPort.lowercase(Locale.ROOT)}"
    }

    fun isInsecure(baseUrl: String): Boolean = baseUrl.startsWith("http://", ignoreCase = true)

    fun displayHost(baseUrl: String): String = baseUrl.substringAfter("://")
}

/** Decodes the hex-encoded output of Docker's service logs endpoint. */
object DockerLogParser {
    private val ansi = Regex("[\\u001B\\u009B][\\[\\]()#;?]*(?:(?:(?:[a-zA-Z\\d]*(?:;[a-zA-Z\\d]*)*)?\\u0007)|(?:(?:\\d{1,4}(?:;\\d{0,4})*)?[\\dA-PR-TZcf-ntqry=><~]))")

    fun parseHex(hex: String): String {
        val clean = hex.filterNot { it.isWhitespace() }
        if (clean.isEmpty()) return ""
        val bytes = ByteArray(clean.length / 2) { i ->
            ((Character.digit(clean[i * 2], 16) shl 4) or Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return stripAnsi(demultiplex(bytes))
    }

    /**
     * Non-TTY services return a multiplexed stream: an 8-byte header per frame
     * ([stream, 0, 0, 0, size(4 bytes, big-endian)]) followed by the payload.
     * TTY services return raw text, which is passed through.
     */
    fun demultiplex(bytes: ByteArray): String {
        val out = java.io.ByteArrayOutputStream(bytes.size)
        var i = 0
        while (i + 8 <= bytes.size && isHeader(bytes, i)) {
            val size = ((bytes[i + 4].toInt() and 0xFF) shl 24) or
                ((bytes[i + 5].toInt() and 0xFF) shl 16) or
                ((bytes[i + 6].toInt() and 0xFF) shl 8) or
                (bytes[i + 7].toInt() and 0xFF)
            val start = i + 8
            val end = minOf(bytes.size, start + size)
            out.write(bytes, start, end - start)
            i = end
        }
        // TTY output (no headers) or a truncated stream: keep the remainder as plain text.
        if (i < bytes.size) out.write(bytes, i, bytes.size - i)
        return out.toString(Charsets.UTF_8.name())
    }

    private fun isHeader(b: ByteArray, i: Int): Boolean =
        b[i].toInt() in 0..3 && b[i + 1].toInt() == 0 && b[i + 2].toInt() == 0 && b[i + 3].toInt() == 0

    fun stripAnsi(text: String): String = text.replace(ansi, "")

    private val timestampPrefix = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})\\s?")

    fun stripTimestamps(text: String): String =
        text.lineSequence().joinToString("\n") { it.replaceFirst(timestampPrefix, "") }
}

object EnvVarsText {
    /** `KEY=value` per line; blank lines and `#` comments are ignored. Values may contain `=`. */
    fun parse(text: String): List<EnvVar> = text.lines()
        .map { it.trimEnd('\r') }
        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        .map { line ->
            val idx = line.indexOf('=')
            if (idx < 0) EnvVar(line.trim(), "") else EnvVar(line.substring(0, idx).trim(), line.substring(idx + 1))
        }

    fun format(vars: List<EnvVar>): String = vars.joinToString("\n") { "${it.key}=${it.value}" }
}

object OneClick {
    const val APP_NAME_VAR = "\$\$cap_appname"
    const val ROOT_DOMAIN_VAR = "\$\$cap_root_domain"
    private val randomHex = Regex("""\${'$'}\${'$'}cap_gen_random_hex\((\d+)\)""")
    private val rng = SecureRandom()

    /** Same behaviour as the dashboard: each `$$cap_gen_random_hex(n)` gets a fresh value. */
    fun replaceRandomHex(input: String): String = randomHex.replace(input) { m ->
        val n = m.groupValues[1].toInt()
        if (n > 256) "" else buildString { repeat(n) { append("0123456789abcdef"[rng.nextInt(16)]) } }
    }

    val appNameVariable = OneClickVariable(
        id = APP_NAME_VAR,
        label = "App name",
        description = "Used as the prefix for every service this template creates. Lowercase letters, digits and dashes.",
        validRegex = "/^([a-z0-9]+\\-)*[a-z0-9]+\$/",
    )

    /** `validRegex` arrives as a JS literal like `/^\d+$/i`. Returns null if it can't be compiled. */
    fun regexFor(validRegex: String?): Regex? {
        if (validRegex.isNullOrBlank()) return null
        val m = Regex("^/(.*)/([a-z]*)$", RegexOption.DOT_MATCHES_ALL).find(validRegex)
        val (pattern, flags) = if (m != null) m.groupValues[1] to m.groupValues[2] else validRegex to ""
        val options = buildSet {
            if ('i' in flags) add(RegexOption.IGNORE_CASE)
            if ('m' in flags) add(RegexOption.MULTILINE)
        }
        return runCatching { Regex(pattern, options) }.getOrNull()
    }

    fun isValid(variable: OneClickVariable, value: String): Boolean =
        regexFor(variable.validRegex)?.containsMatchIn(value) ?: true
}

object AppNames {
    private val valid = Regex("^([a-z0-9]+-)*[a-z0-9]+$")
    fun isValid(name: String) = valid.matches(name)
}

object Format {
    fun bytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024; unit++
        }
        return if (unit == 0) "$bytes B" else String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
    }

    fun cpus(nanoCpu: Long): String {
        val cores = nanoCpu / 1_000_000_000.0
        return if (cores % 1.0 == 0.0) "${cores.toInt()} vCPU" else String.format(Locale.getDefault(), "%.1f vCPU", cores)
    }

    fun parseInstant(timestamp: String): Instant? = runCatching { Instant.parse(timestamp) }.getOrNull()

    fun relative(timestamp: String, now: Instant = Instant.now()): String {
        val instant = parseInstant(timestamp) ?: return timestamp
        val d = Duration.between(instant, now)
        return when {
            d.isNegative || d.seconds < 60 -> "just now"
            d.toMinutes() < 60 -> "${d.toMinutes()} min ago"
            d.toHours() < 24 -> "${d.toHours()} h ago"
            d.toDays() < 30 -> "${d.toDays()} d ago"
            else -> absolute(timestamp)
        }
    }

    fun absolute(timestamp: String): String {
        val instant = parseInstant(timestamp) ?: return timestamp
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }

    fun number(n: Long): String = String.format(Locale.getDefault(), "%,d", n)
}
