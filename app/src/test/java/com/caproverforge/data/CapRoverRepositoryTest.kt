package com.caproverforge.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caproverforge.testing.FakeCapRover
import com.caproverforge.testing.PlainTokenCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CapRoverRepositoryTest {
    private val fake = FakeCapRover()
    private val server = MockWebServer()
    private lateinit var sessions: SessionStore
    private lateinit var api: CapRoverApi
    private lateinit var repo: CapRoverRepository
    private lateinit var baseUrl: String

    @Before fun setUp() {
        server.dispatcher = fake
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("session", 0).edit().clear().commit()
        sessions = SessionStore(context, PlainTokenCodec)
        api = CapRoverApi(sessions, OkHttpClient())
        repo = CapRoverRepository(api, sessions)
    }

    @After fun tearDown() = server.close()

    @Test fun loginStoresTokenAndSendsNamespace() = runTest {
        repo.login(baseUrl, fake.password, null)
        assertEquals(Session(baseUrl, fake.token), sessions.session.value)
        val info = repo.captainInfo()
        assertEquals("example.com", info.rootDomain)
        assertTrue(info.hasRootSsl)
    }

    @Test fun wrongPasswordIsReportedClearly() = runTest {
        try {
            repo.login(baseUrl, "nope", null)
            fail("expected failure")
        } catch (e: CapRoverException) {
            assertEquals(CapRoverException.WRONG_PASSWORD, e.status)
            assertEquals("Incorrect password.", e.message)
        }
        assertNull(sessions.session.value)
    }

    @Test fun expiredTokenClearsSessionAndNotifies() = runTest {
        repo.login(baseUrl, fake.password, null)
        fake.expireToken = true
        val expired = launch { api.sessionExpired.first() }
        try {
            repo.apps()
            fail("expected failure")
        } catch (e: CapRoverException) {
            assertEquals(CapRoverException.AUTH_TOKEN_INVALID, e.status)
        }
        expired.join()
        assertNull(sessions.session.value)
    }

    @Test fun parsesAppsAndKeepsRawJson() = runTest {
        repo.login(baseUrl, fake.password, null)
        val data = repo.apps()
        assertEquals("example.com", data.rootDomain)
        val api = data.apps.first { it.def.appName == "api" }
        assertEquals(3, api.def.instanceCount)
        assertEquals("img-captain-api:5", api.def.deployedImage)
        assertTrue("unknown fields are kept", api.raw.containsKey("networks"))
    }

    @Test fun saveAppPostsFullDefinitionPreservingUnknownFields() = runTest {
        repo.login(baseUrl, fake.password, null)
        val app = repo.apps().apps.first { it.def.appName == "api" }
        val edited = app.def.copy(
            instanceCount = 5,
            envVars = app.def.envVars + EnvVar("NEW", "1"),
            httpAuth = null,
        )
        repo.saveApp(app, edited)

        val (_, body) = fake.requests.last { it.first == "POST /user/apps/appDefinitions/update" }
        val json = ApiJson.parseToJsonElement(body).jsonObject
        assertEquals("api", json["appName"]!!.jsonPrimitive.content)
        assertEquals(5, json["instanceCount"]!!.jsonPrimitive.content.toInt())
        assertTrue(json["envVars"].toString().contains("NEW"))
        assertTrue("unknown server fields survive", json.containsKey("networks"))
        assertEquals(JsonNull, json["httpAuth"])
        assertFalse("read-only fields are not echoed", json.containsKey("versions"))
        assertTrue("git settings are preserved", json["appPushWebhook"].toString().contains("github.com/example/api"))
    }

    @Test fun removingRepositorySendsNullWebhook() = runTest {
        repo.login(baseUrl, fake.password, null)
        val app = repo.apps().apps.first { it.def.appName == "api" }
        repo.saveApp(app, app.def.copy(appPushWebhook = null))
        val body = fake.requests.last { it.first == "POST /user/apps/appDefinitions/update" }.second
        assertEquals(JsonNull, ApiJson.parseToJsonElement(body).jsonObject["appPushWebhook"])
    }

    @Test fun decodesHexAppLogs() = runTest {
        repo.login(baseUrl, fake.password, null)
        val logs = repo.appLogs("api")
        assertTrue(logs.contains("Server listening on :3000"))
        assertTrue(logs.contains("WARN slow query"))
        assertFalse("no docker frame headers leak through", logs.contains('\u0001'))
    }

    @Test fun deployImageSendsCaptainDefinition() = runTest {
        repo.login(baseUrl, fake.password, null)
        repo.deployImage("api", " nginx:alpine ")
        val (req, body) = fake.requests.last()
        assertEquals("POST /user/apps/appData/api", req)
        val definition = ApiJson.parseToJsonElement(
            ApiJson.parseToJsonElement(body).jsonObject["captainDefinitionContent"]!!.jsonPrimitive.content
        ).jsonObject
        assertEquals("nginx:alpine", definition["imageName"]!!.jsonPrimitive.content)
        assertEquals(2, definition["schemaVersion"]!!.jsonPrimitive.content.toInt())
    }

    @Test fun deleteSingleAppUsesAppNameField() = runTest {
        repo.login(baseUrl, fake.password, null)
        repo.deleteApps(listOf("worker"), listOf("data"))
        val body = ApiJson.parseToJsonElement(fake.requests.last().second).jsonObject
        assertEquals("worker", body["appName"]!!.jsonPrimitive.content)
        assertTrue(body["volumes"].toString().contains("data"))
        assertFalse(body.containsKey("appNames"))
    }

    @Test fun backupUrlPointsAtDownloadEndpoint() = runTest {
        repo.login(baseUrl, fake.password, null)
        assertEquals(
            "$baseUrl/api/v2/downloads/?namespace=captain&downloadToken=tok%2Fen%2B1",
            repo.createBackupUrl(),
        )
    }
}
