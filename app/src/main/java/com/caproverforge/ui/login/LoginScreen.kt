package com.caproverforge.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.caproverforge.R
import com.caproverforge.data.CapRoverException
import com.caproverforge.data.CapRoverRepository
import com.caproverforge.data.ServerAddress
import com.caproverforge.ui.common.userMessage
import com.caproverforge.ui.components.PasswordField
import com.caproverforge.ui.navigation.containerViewModel
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: CapRoverRepository, initialServer: String) :
    androidx.lifecycle.ViewModel() {
    var server by mutableStateOf(initialServer)
    var password by mutableStateOf("")
    var otp by mutableStateOf("")
    var showOtp by mutableStateOf(false)
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val insecure: Boolean
        get() = runCatching { ServerAddress.isInsecure(ServerAddress.normalize(server)) }.getOrDefault(false)

    fun signIn(onSuccess: () -> Unit) {
        if (loading || password.isBlank() || server.isBlank()) return
        viewModelScope.launch {
            loading = true
            error = null
            try {
                repository.login(server, password, otp.takeIf { showOtp })
                password = ""
                onSuccess()
            } catch (e: Exception) {
                if (e is CapRoverException && e.status == CapRoverException.OTP_REQUIRED) {
                    showOtp = true
                }
                error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val vm = containerViewModel { LoginViewModel(it.repository, it.sessionStore.lastServer) }
    val focus = LocalFocusManager.current
    val brand = MaterialTheme.colorScheme

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to brand.primaryContainer,
                    0.36f to brand.primaryContainer,
                    0.6f to brand.surface,
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(88.dp).shadow(16.dp, RoundedCornerShape(26.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "CaproverForge",
                style = MaterialTheme.typography.headlineMedium,
                color = brand.onPrimaryContainer,
            )
            Text(
                "Manage your CapRover server on the go",
                style = MaterialTheme.typography.bodyMedium,
                color = brand.onPrimaryContainer.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = brand.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sign in", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = vm.server,
                        onValueChange = { vm.server = it },
                        label = { Text("Dashboard address") },
                        placeholder = { Text("captain.example.com") },
                        leadingIcon = { Icon(Icons.Outlined.Dns, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(vm.insecure) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Warning, null, tint = brand.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "This connection isn't encrypted. Your password will be sent in plain text.",
                                style = MaterialTheme.typography.bodySmall,
                                color = brand.error,
                            )
                        }
                    }
                    PasswordField(
                        value = vm.password,
                        onValueChange = { vm.password = it },
                        label = "Password",
                        imeAction = if (vm.showOtp) ImeAction.Next else ImeAction.Done,
                        onImeAction = {
                            if (vm.showOtp) focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                            else vm.signIn(onSignedIn)
                        },
                    )
                    AnimatedVisibility(vm.showOtp) {
                        OutlinedTextField(
                            value = vm.otp,
                            onValueChange = { v -> vm.otp = v.filter { it.isDigit() }.take(8) },
                            label = { Text("Two-factor code") },
                            leadingIcon = { Icon(Icons.Outlined.Pin, null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { vm.signIn(onSignedIn) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AnimatedVisibility(vm.error != null) {
                        Text(
                            vm.error.orEmpty(),
                            color = brand.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(
                        onClick = { focus.clearFocus(); vm.signIn(onSignedIn) },
                        enabled = !vm.loading && vm.password.isNotBlank() && vm.server.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (vm.loading) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = brand.onPrimary)
                        } else {
                            Text("Sign in")
                        }
                    }
                    if (!vm.showOtp) {
                        TextButton(onClick = { vm.showOtp = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("I use two-factor authentication")
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Your password is only used to sign in and is never stored on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = brand.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
    }
}
