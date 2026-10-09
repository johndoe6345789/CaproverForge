package com.caproverforge.data

import com.caproverforge.ui.common.AppStatus
import com.caproverforge.ui.common.status
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ServerAddressTest {
    @Test fun addsHttpsToBareHost() =
        assertEquals("https://captain.wardcrew.com", ServerAddress.normalize("captain.wardcrew.com"))

    @Test fun stripsDashboardPathsAndTrailingSlash() {
        assertEquals("https://captain.example.com", ServerAddress.normalize(" https://Captain.Example.com/#/apps "))
        assertEquals("https://captain.example.com", ServerAddress.normalize("https://captain.example.com/api/v2/"))
    }

    @Test fun keepsExplicitHttpAndPort() {
        val url = ServerAddress.normalize("http://203.0.113.7:3000")
        assertEquals("http://203.0.113.7:3000", url)
        assertTrue(ServerAddress.isInsecure(url))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherSchemes() {
        ServerAddress.normalize("ftp://example.com")
    }
}

class DockerLogParserTest {
    private fun frame(stream: Int, text: String): ByteArray {
        val payload = text.toByteArray()
        val size = payload.size
        return byteArrayOf(stream.toByte(), 0, 0, 0, (size shr 24).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte()) + payload
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    @Test fun demultiplexesStdoutAndStderrFrames() {
        val bytes = frame(1, "hello\n") + frame(2, "oops\n") + frame(1, "bye\n")
        assertEquals("hello\noops\nbye\n", DockerLogParser.parseHex(bytes.hex()))
    }

    @Test fun passesThroughTtyOutput() {
        assertEquals("plain line\n", DockerLogParser.parseHex("plain line\n".toByteArray().hex()))
    }

    @Test fun keepsUtf8AndStripsAnsiColours() {
        val bytes = frame(1, "\u001B[32mgrün ✓\u001B[0m\n")
        assertEquals("grün ✓\n", DockerLogParser.parseHex(bytes.hex()))
    }

    @Test fun toleratesTruncatedFrame() {
        val full = frame(1, "complete\n")
        val cut = frame(1, "partial line that was cut").copyOf(14)
        assertTrue(DockerLogParser.parseHex((full + cut).hex()).startsWith("complete\n"))
    }

    @Test fun stripsDockerTimestamps() {
        val text = "2026-10-08T23:39:49.123456789Z server started\n2026-10-08T23:39:50Z ready"
        assertEquals("server started\nready", DockerLogParser.stripTimestamps(text))
    }
}

class EnvVarsTextTest {
    @Test fun parsesBulkText() {
        val vars = EnvVarsText.parse("# comment\nDB_URL=postgres://u:p@h/db?x=1\n\nEMPTY=\nNO_EQUALS\n")
        assertEquals(
            listOf(EnvVar("DB_URL", "postgres://u:p@h/db?x=1"), EnvVar("EMPTY", ""), EnvVar("NO_EQUALS", "")),
            vars,
        )
    }

    @Test fun roundTrips() {
        val vars = listOf(EnvVar("A", "1"), EnvVar("B", "x=y"))
        assertEquals(vars, EnvVarsText.parse(EnvVarsText.format(vars)))
    }
}

class OneClickTest {
    @Test fun replacesEachRandomHexWithFreshValue() {
        val out = OneClick.replaceRandomHex("a=\$\$cap_gen_random_hex(16) b=\$\$cap_gen_random_hex(16)")
        val (a, b) = Regex("a=([0-9a-f]+) b=([0-9a-f]+)").find(out)!!.destructured
        assertEquals(16, a.length)
        assertEquals(16, b.length)
        assertNotEquals(a, b)
    }

    @Test fun capsOversizedRandomValues() {
        assertEquals("x=", OneClick.replaceRandomHex("x=\$\$cap_gen_random_hex(9999)"))
    }

    @Test fun compilesJavascriptRegexLiterals() {
        val variable = OneClickVariable(id = "v", validRegex = "/^\\d+$/")
        assertTrue(OneClick.isValid(variable, "123"))
        assertFalse(OneClick.isValid(variable, "12a"))
        assertTrue(OneClick.isValid(OneClickVariable(id = "v", validRegex = "/^abc$/i"), "ABC"))
    }

    @Test fun appNameRule() {
        assertTrue(OneClick.isValid(OneClick.appNameVariable, "my-app-2"))
        assertFalse(OneClick.isValid(OneClick.appNameVariable, "My_App"))
        assertFalse(OneClick.isValid(OneClick.appNameVariable, "-app"))
    }

    @Test fun invalidRegexIsIgnored() {
        assertNull(OneClick.regexFor("/([/"))
        assertTrue(OneClick.isValid(OneClickVariable(id = "v", validRegex = "/([/"), "anything"))
    }
}

class FormatTest {
    @Test fun bytes() {
        assertEquals("0 B", Format.bytes(0))
        assertEquals("512 B", Format.bytes(512))
        assertTrue(Format.bytes(8L * 1024 * 1024 * 1024).startsWith("8"))
    }

    @Test fun relativeTimes() {
        val now = Instant.parse("2026-10-09T12:00:00Z")
        assertEquals("just now", Format.relative("2026-10-09T11:59:30Z", now))
        assertEquals("5 min ago", Format.relative("2026-10-09T11:55:00Z", now))
        assertEquals("3 h ago", Format.relative("2026-10-09T09:00:00Z", now))
        assertEquals("2 d ago", Format.relative("2026-10-07T12:00:00Z", now))
        assertEquals("not a date", Format.relative("not a date", now))
    }
}

class ModelsTest {
    private fun app(json: String): AppDefinition = ApiJson.decodeFromJsonElement(ApiJson.parseToJsonElement(json))

    @Test fun decodesLenientServerData() {
        val def = app(
            """{"appName":"web","instanceCount":"2","containerHttpPort":"3000","deployedVersion":3,
               "someFutureField":{"x":1},"envVars":[{"key":"A","value":"1"}],
               "versions":[{"version":3,"deployedImageName":"img:3","timeStamp":"2026-10-01T00:00:00Z"}]}"""
        )
        assertEquals(2, def.instanceCount)
        assertEquals(3000, def.containerHttpPort)
        assertEquals("img:3", def.deployedImage)
        assertEquals(AppStatus.Running, def.status())
    }

    @Test fun statusReflectsBuildAndScale() {
        assertEquals(AppStatus.Building, app("""{"appName":"a","isAppBuilding":true}""").status())
        assertEquals(AppStatus.Stopped, app("""{"appName":"a","instanceCount":0,"versions":[{"version":0,"deployedImageName":"x"}]}""").status())
        assertEquals(
            AppStatus.NotDeployed,
            app("""{"appName":"a","versions":[{"version":0,"deployedImageName":"caprover/caprover-placeholder-app:latest"}]}""").status(),
        )
        assertEquals(
            AppStatus.BuildFailed,
            app("""{"appName":"a","deployedVersion":1,"versions":[{"version":1,"deployedImageName":"x:1"},{"version":2}]}""").status(),
        )
    }

    @Test fun appNames() {
        assertTrue(AppNames.isValid("api"))
        assertTrue(AppNames.isValid("my-api-2"))
        assertFalse(AppNames.isValid("my--api"))
        assertFalse(AppNames.isValid("Api"))
        assertFalse(AppNames.isValid("api-"))
    }
}

class NetDataParserTest {
    private fun json(s: String) = ApiJson.parseToJsonElement(s)

    @Test fun sumsCpuDimensionsExceptIdleAndSortsByTime() {
        val series = NetDataParser.sum(json("""{"labels":["time","user","system","idle"],"data":[[20,3,1,96],[10,1,1,98]]}"""))
        assertEquals(listOf(10L, 20L), series.times)
        assertEquals(listOf(2.0, 4.0), series.values)
    }

    @Test fun memoryPercentOfAllDimensions() {
        val (pct, used, total) = NetDataParser.memory(json("""{"labels":["time","free","used","cached","buffers"],"data":[[1,1000,2000,800,200]]}"""))
        assertEquals(50.0, pct.last!!, 0.001)
        assertEquals(2000.0, used!!, 0.001)
        assertEquals(4000.0, total!!, 0.001)
    }

    @Test fun diskUsage() {
        val disk = NetDataParser.disk(json("""{"labels":["time","avail","used","reserved_for_root"],"data":[[1,30,60,10]]}"""))!!
        assertEquals(0.6, disk.fraction, 0.001)
    }

    @Test fun missingDimensionsAreEmptyNotErrors() {
        assertTrue(NetDataParser.dimension(json("""{"labels":["time","a"],"data":[[1,2]]}"""), "load1").isEmpty)
        assertNull(NetDataParser.disk(json("""{"labels":["time"],"data":[]}""")))
        assertTrue(NetDataParser.sum(json("{}")).isEmpty)
    }
}
