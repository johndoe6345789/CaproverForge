package com.caproverforge.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caproverforge.AppContainer
import com.caproverforge.data.SessionStore
import com.caproverforge.data.ThemeMode
import com.caproverforge.testing.FakeCapRover
import com.caproverforge.testing.PlainTokenCodec
import com.caproverforge.ui.components.LocalPollingEnabled
import com.caproverforge.ui.navigation.AppRoot
import com.caproverforge.ui.theme.CaproverForgeTheme
import com.github.takahirom.roborazzi.captureRoboImage
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app (repository → ViewModels → Compose) against [FakeCapRover] and records
 * screenshots with Roborazzi (`./gradlew recordRoborazziDebug`). Without the record flag these
 * still run as UI flow tests.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class AppScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val fake = FakeCapRover()
    private val server = MockWebServer()
    private lateinit var container: AppContainer

    @Before fun setUp() {
        server.dispatcher = fake
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("session", 0).edit().clear().commit()
        context.getSharedPreferences("settings", 0).edit().clear().commit()
        container = AppContainer(context, SessionStore(context, PlainTokenCodec))
    }

    @After fun tearDown() = server.close()

    private fun signedIn() = container.sessionStore.save(server.url("/").toString().trimEnd('/'), fake.token)

    private fun launch(theme: ThemeMode = ThemeMode.Light) {
        compose.setContent {
            CompositionLocalProvider(LocalPollingEnabled provides false) {
                CaproverForgeTheme(themeMode = theme) { AppRoot(container) }
            }
        }
    }

    private fun waitForText(text: String, timeoutMs: Long = 10_000) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = true), timeoutMs)

    private fun tab(label: String) =
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()

    private fun shot(name: String) = compose.onRoot().captureRoboImage("screenshots/$name.png")

    @Test fun signIn() {
        launch()
        waitForText("Sign in")
        shot("01_login")
        val address = server.url("/").toString().trimEnd('/')
        compose.onNode(hasText("Dashboard address") and hasSetTextAction()).performTextReplacement(address)
        compose.onNode(hasText("Password") and hasSetTextAction()).performTextInput(fake.password)
        compose.onNode(hasText("Sign in") and hasClickAction() and !hasSetTextAction()).performClick()
        waitForText("Connections")
        assertTrue(container.sessionStore.session.value?.token == fake.token)
    }

    @Test fun loginFailureShowsMessage() {
        container.sessionStore.save(server.url("/").toString().trimEnd('/'), "stale")
        container.sessionStore.clear()
        launch()
        waitForText("Sign in")
        compose.onNode(hasText("Password") and hasSetTextAction()).performTextInput("wrong")
        compose.onNode(hasText("Sign in") and hasClickAction() and !hasSetTextAction()).performClick()
        waitForText("Incorrect password")
        shot("02_login_error")
    }

    @Test fun dashboard() {
        signedIn()
        launch()
        waitForText("Connections")
        waitForText("CapRover 1.15.0 is available")
        shot("03_dashboard")
    }

    @Test fun dashboardDark() {
        signedIn()
        launch(ThemeMode.Dark)
        waitForText("Connections")
        shot("04_dashboard_dark")
    }

    @Test fun appsList() {
        signedIn()
        launch()
        tab("Apps")
        waitForText("api-db")
        waitForText("Running ×3")
        shot("05_apps")
    }

    @Test fun appDetailTabs() {
        signedIn()
        launch()
        tab("Apps")
        waitForText("api-db")
        compose.onAllNodesWithText("api").onFirst().performClick()
        waitForText("Deployed version")
        shot("06_app_overview")

        tab("HTTP")
        waitForText("api.acme.io")
        shot("07_app_http")

        tab("Config")
        waitForText("DATABASE_URL")
        shot("08_app_config")

        tab("Deploy")
        waitForText("Build has finished successfully!")
        shot("09_app_deploy")

        tab("Logs")
        waitForText("Server listening on :3000")
        shot("10_app_logs")
    }

    @Test fun unsavedChangesBar() {
        signedIn()
        launch()
        tab("Apps")
        waitForText("api-db")
        compose.onAllNodesWithText("api").onFirst().performClick()
        waitForText("Deployed version")
        compose.onNodeWithContentDescriptionSafe("More instances")
        waitForText("Unsaved changes")
        shot("11_unsaved_changes")
        compose.onNodeWithText("Save & restart").performClick()
        compose.waitUntil(10_000) { fake.requests.any { it.first == "POST /user/apps/appDefinitions/update" } }
        val body = fake.requests.last { it.first == "POST /user/apps/appDefinitions/update" }.second
        assertTrue(body, body.contains("\"instanceCount\":4"))
    }

    @Test fun oneClickFlow() {
        signedIn()
        launch()
        tab("One-Click")
        waitForText("Uptime Kuma")
        shot("12_oneclick_list")
        compose.onNodeWithText("WordPress").performClick()
        waitForText("WordPress version")
        shot("13_oneclick_form")
    }

    @Test fun serverMenu() {
        signedIn()
        launch()
        tab("Server")
        waitForText("Cluster nodes")
        shot("14_server")
        compose.onNodeWithText("Cluster nodes").performClick()
        waitForText("captain-01")
        shot("15_nodes")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionSafe(desc: String) {
        waitUntilAtLeastOneExists(androidx.compose.ui.test.hasContentDescription(desc), 10_000)
        onNode(androidx.compose.ui.test.hasContentDescription(desc)).performClick()
    }
}
