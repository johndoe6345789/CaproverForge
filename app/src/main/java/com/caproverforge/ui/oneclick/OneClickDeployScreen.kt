package com.caproverforge.ui.oneclick

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.caproverforge.data.ApiJson
import com.caproverforge.data.CapRoverException
import com.caproverforge.data.CapRoverRepository
import com.caproverforge.data.OneClick
import com.caproverforge.data.OneClickDeployState
import com.caproverforge.data.OneClickVariable
import com.caproverforge.ui.common.LoadingViewModel
import com.caproverforge.ui.common.userMessage
import com.caproverforge.ui.components.BackTopBar
import com.caproverforge.ui.components.CollectMessages
import com.caproverforge.ui.components.LoadContent
import com.caproverforge.ui.components.LocalSnackbar
import com.caproverforge.ui.components.SectionCard
import com.caproverforge.ui.navigation.containerViewModel
import com.caproverforge.ui.theme.LocalExtendedColors
import com.caproverforge.ui.theme.MonoStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class OneClickTemplate(
    val raw: JsonObject,
    val displayName: String,
    val description: String,
    val startInstructions: String,
    val endInstructions: String,
    val variables: List<OneClickVariable>,
    val services: List<Pair<String, String>>, // service name template → image or "Dockerfile"
    val rootDomain: String,
)

class OneClickDeployViewModel(
    private val repo: CapRoverRepository,
    private val name: String,
    private val baseUrl: String,
) : LoadingViewModel<OneClickTemplate>() {
    val values = mutableStateMapOf<String, String>()
    var showErrors by mutableStateOf(false)
    var deployState by mutableStateOf<OneClickDeployState?>(null)
        private set
    var deploying by mutableStateOf(false)
        private set

    init { refresh() }

    override suspend fun load(): OneClickTemplate {
        val rawTemplate = repo.oneClickTemplate(name, baseUrl)
        // Random secrets are generated once per screen, like the web dashboard does.
        val template = ApiJson.parseToJsonElement(OneClick.replaceRandomHex(rawTemplate.toString())).jsonObject
        val version = template["captainVersion"]?.jsonPrimitive?.contentOrNull
        if (version != "4") {
            throw CapRoverException(CapRoverException.HTTP, "This template uses format v$version; only v4 is supported. Update CapRover to deploy it.")
        }
        val meta = template["caproverOneClickApp"]?.jsonObject ?: JsonObject(emptyMap())
        val instructions = meta["instructions"] as? JsonObject
        val variables = listOf(OneClick.appNameVariable) +
            ((meta["variables"] as? JsonArray)?.map { ApiJson.decodeFromJsonElement<OneClickVariable>(it) }.orEmpty())
        val services = (template["services"] as? JsonObject)?.map { (svc, body) ->
            val o = body as? JsonObject
            svc to (o?.get("image")?.jsonPrimitive?.contentOrNull ?: "Dockerfile build")
        }.orEmpty()
        val rootDomain = runCatching { repo.captainInfo().rootDomain }.getOrDefault("")

        if (values.isEmpty()) {
            variables.forEach { v -> values[v.id] = v.defaultValue.orEmpty() }
            values[OneClick.APP_NAME_VAR] = name.lowercase().replace(Regex("[^a-z0-9-]"), "-").trim('-')
        }
        return OneClickTemplate(
            raw = template,
            displayName = meta["displayName"]?.jsonPrimitive?.contentOrNull ?: name,
            description = meta["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            startInstructions = instructions?.get("start")?.jsonPrimitive?.contentOrNull.orEmpty(),
            endInstructions = instructions?.get("end")?.jsonPrimitive?.contentOrNull.orEmpty(),
            variables = variables,
            services = services,
            rootDomain = rootDomain,
        )
    }

    fun errorFor(variable: OneClickVariable): String? {
        val value = values[variable.id].orEmpty()
        return when {
            variable.id == OneClick.APP_NAME_VAR && value.isBlank() -> "Required"
            !OneClick.isValid(variable, value) -> "Doesn't match ${variable.validRegex}"
            else -> null
        }
    }

    fun substitute(text: String): String {
        var out = text
        values.forEach { (k, v) -> out = out.replace(k, v) }
        data?.rootDomain?.let { out = out.replace(OneClick.ROOT_DOMAIN_VAR, it) }
        return out
    }

    val appName: String get() = values[OneClick.APP_NAME_VAR].orEmpty()

    fun deploy() {
        val template = data ?: return
        if (template.variables.any { errorFor(it) != null }) {
            showErrors = true
            message("Fix the highlighted fields first.")
            return
        }
        viewModelScope.launch {
            deploying = true
            deployState = OneClickDeployState(steps = listOf("Starting deployment"), currentStep = 0)
            try {
                // Like the dashboard: the app-name variable is declared up front and the root domain is appended.
                val meta = template.raw["caproverOneClickApp"]?.jsonObject ?: JsonObject(emptyMap())
                val declared = (meta["variables"] as? JsonArray).orEmpty()
                val vars = JsonArray(
                    listOf(ApiJson.encodeToJsonElement(OneClick.appNameVariable)) + declared +
                        ApiJson.encodeToJsonElement(OneClickVariable(id = OneClick.ROOT_DOMAIN_VAR, label = "CapRover root domain"))
                )
                val finalTemplate = JsonObject(template.raw + ("caproverOneClickApp" to JsonObject(meta + ("variables" to vars))))
                val finalValues = values.toMap() + (OneClick.ROOT_DOMAIN_VAR to template.rootDomain)
                val jobId = repo.startOneClickDeploy(finalTemplate, finalValues, name)
                while (true) {
                    delay(1_500)
                    val state = repo.oneClickProgress(jobId)
                    deployState = state
                    if (state.error != null || state.successMessage != null) break
                }
            } catch (e: Exception) {
                deployState = (deployState ?: OneClickDeployState()).copy(error = e.userMessage())
            } finally {
                deploying = false
            }
        }
    }

    fun reset() {
        deployState = null
    }
}

@Composable
fun OneClickDeployScreen(
    name: String,
    baseUrl: String,
    displayName: String,
    onBack: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val vm = containerViewModel(key = "oneclick:$baseUrl/$name") { OneClickDeployViewModel(it.repository, name, baseUrl) }
    CollectMessages(vm.messages)

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        topBar = { BackTopBar(title = displayName.ifBlank { name }, subtitle = "One-click app", onBack = onBack) },
    ) { padding ->
        LoadContent(vm.state, vm.refreshing, { vm.refresh(true) }, Modifier.padding(padding)) { template ->
            AnimatedContent(vm.deployState != null, label = "deploy") { deploying ->
                if (deploying) {
                    DeployProgress(vm, template, onOpenApp = { onOpenApp(vm.appName) })
                } else {
                    VariablesForm(vm, template)
                }
            }
        }
    }
}

@Composable
private fun VariablesForm(vm: OneClickDeployViewModel, template: OneClickTemplate) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppLogo(null, Modifier.size(56.dp), fallbackName = template.displayName)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(template.displayName, style = MaterialTheme.typography.headlineSmall)
                if (template.description.isNotBlank()) {
                    Text(template.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (template.startInstructions.isNotBlank()) {
            SectionCard("Before you start") {
                SelectionContainer { Text(cleanMarkdown(template.startInstructions), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        if (template.services.isNotEmpty()) {
            SectionCard("Creates ${template.services.size} app${if (template.services.size == 1) "" else "s"}", icon = Icons.Outlined.Inventory2) {
                template.services.forEach { (svc, image) ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(vm.substitute(svc), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(vm.substitute(image), style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        SectionCard("Settings", icon = Icons.Outlined.Tune) {
            template.variables.forEach { variable ->
                val error = if (vm.showErrors || vm.values[variable.id].orEmpty().isNotEmpty()) vm.errorFor(variable) else null
                OutlinedTextField(
                    value = vm.values[variable.id].orEmpty(),
                    onValueChange = { vm.values[variable.id] = it },
                    label = { Text(variable.label.ifBlank { variable.id }) },
                    supportingText = (error ?: variable.description)?.let { { Text(it) } },
                    isError = error != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            }
        }
        Button(onClick = vm::deploy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Outlined.RocketLaunch, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Deploy")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DeployProgress(vm: OneClickDeployViewModel, template: OneClickTemplate, onOpenApp: () -> Unit) {
    val state = vm.deployState ?: return
    val ext = LocalExtendedColors.current
    val done = state.successMessage != null
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(
            when {
                state.error != null -> "Deployment failed"
                done -> "Deployed ${template.displayName}"
                else -> "Deploying ${template.displayName}…"
            },
        ) {
            state.steps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    when {
                        state.error != null && i == state.currentStep ->
                            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        done || i < state.currentStep -> Icon(Icons.Outlined.CheckCircle, null, tint = ext.running)
                        i == state.currentStep && vm.deploying -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        else -> Icon(Icons.Outlined.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(step, style = MaterialTheme.typography.bodyLarge)
                }
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (done) {
            val end = template.endInstructions.ifBlank { state.successMessage.orEmpty() }
            if (end.isNotBlank()) {
                SectionCard("Next steps") {
                    SelectionContainer { Text(cleanMarkdown(vm.substitute(end)), style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Button(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) { Text("Open ${vm.appName}") }
        }
        if (state.error != null) {
            OutlinedButton(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) { Text("Back to settings") }
        }
    }
}

/** Templates use light Markdown; strip the syntax that reads badly as plain text. */
internal fun cleanMarkdown(text: String): String = text
    .replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "$1 ($2)")
    .replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2")
    .replace(Regex("(?m)^#{1,6}\\s*"), "")
    .replace("`", "")
    .trim()
