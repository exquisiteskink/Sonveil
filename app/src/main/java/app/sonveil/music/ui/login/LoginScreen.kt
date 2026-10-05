package app.sonveil.music.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonveil.music.data.auth.AuthMode
import app.sonveil.music.data.auth.StoredCredentials
import app.sonveil.music.data.remote.SubsonicException
import app.sonveil.music.ui.components.GlassSurface
import app.sonveil.music.ui.theme.LocalContainer
import app.sonveil.music.ui.theme.LocalPalette
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val p = LocalPalette.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var httpWarning by remember { mutableStateOf(false) }
    var allowInsecureLanHttp by remember { mutableStateOf(false) }

    // Retry a prior transient restore failure and prefill saved account fields.
    LaunchedEffect(Unit) {
        runCatching { container.restoreSession() }
        val stored = container.credentials.load() ?: return@LaunchedEffect
        if (url.isBlank()) url = stored.serverUrl
        httpWarning = url.trim().startsWith("http://", ignoreCase = true)
        allowInsecureLanHttp = stored.allowInsecureLanHttp
        when (stored.authMode) {
            AuthMode.ApiKey -> {
                showApiKey = true
                if (apiKey.isBlank()) apiKey = stored.apiKey
            }
            else -> {
                if (username.isBlank()) username = stored.username
                if (password.isBlank()) password = stored.password
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = p.onBackground.copy(alpha = 0.45f),
        unfocusedBorderColor = p.onBackground.copy(alpha = 0.18f),
        focusedLabelColor = p.onBackground.copy(alpha = 0.7f),
        unfocusedLabelColor = p.onBackground.copy(alpha = 0.5f),
        cursorColor = p.onBackground,
        focusedTextColor = p.onBackground,
        unfocusedTextColor = p.onBackground,
        focusedContainerColor = p.surfaceHigh.copy(alpha = 0.6f),
        unfocusedContainerColor = p.surfaceHigh.copy(alpha = 0.4f),
    )

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sonveil", color = p.onBackground, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(
            "Navidrome · Subsonic · OpenSubsonic",
            color = p.onBackground.copy(alpha = 0.55f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(28.dp))
        GlassSurface(Modifier.fillMaxWidth(), radius = 28.dp) {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    httpWarning = it.trim().startsWith("http://", ignoreCase = true)
                    allowInsecureLanHttp = false
                },
                label = { Text("Server URL") },
                placeholder = { Text("https://music.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(16.dp),
            )
            if (httpWarning) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "HTTP exposes API keys and reusable login tokens to anyone who can intercept this connection. HTTPS is recommended, including on your LAN.",
                    color = p.secondary,
                    fontSize = 12.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = allowInsecureLanHttp,
                        onCheckedChange = { allowInsecureLanHttp = it },
                        enabled = !loading,
                    )
                    Text("Allow insecure HTTP for this LAN server", color = p.onBackground, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!showApiKey) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = p.onBackground.copy(alpha = 0.6f),
                            )
                        }
                    },
                )
            } else {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                )
            }
            TextButton(onClick = { showApiKey = !showApiKey }) {
                Text(
                    if (showApiKey) "Use username & password" else "Use OpenSubsonic API key",
                    color = p.onBackground.copy(alpha = 0.75f),
                )
            }
            if (error != null) {
                Text(error!!, color = Color(0xFFFF8A80), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = {
                    error = null
                    loading = true
                    scope.launch {
                        try {
                            val normalized = normalizeUrl(url)
                            val creds = if (showApiKey) {
                                StoredCredentials(
                                    serverUrl = normalized,
                                    apiKey = apiKey.trim(),
                                    authMode = AuthMode.ApiKey,
                                    allowInsecureLanHttp = httpWarning && allowInsecureLanHttp,
                                )
                            } else {
                                StoredCredentials(
                                    serverUrl = normalized,
                                    username = username.trim(),
                                    password = password,
                                    authMode = AuthMode.Token,
                                    allowInsecureLanHttp = httpWarning && allowInsecureLanHttp,
                                )
                            }
                            container.signIn(creds)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: SubsonicException) {
                            error = humanError(e)
                        } catch (e: Exception) {
                            error = e.message ?: "Could not connect"
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading && (!httpWarning || allowInsecureLanHttp) && url.isNotBlank() && (showApiKey && apiKey.isNotBlank() || !showApiKey && username.isNotBlank() && password.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.playButton, contentColor = p.onPlayButton),
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = p.onPrimary, strokeWidth = 2.dp)
                else Text("Connect", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Passwords are encrypted in Android Keystore. Sign-in uses salted token auth when the server allows it. LAN HTTP requires explicit consent; HTTPS is required on the public internet.",
            color = p.onBackground.copy(alpha = 0.4f),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

private fun normalizeUrl(raw: String): String {
    var s = raw.trim().trimEnd('/')
    if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
    if (s.endsWith("/rest")) s = s.removeSuffix("/rest")
    return s
}

private fun humanError(e: SubsonicException): String = when (e.code) {
    40 -> "Wrong username or password"
    41 -> e.message ?: "Token auth is not supported on this account"
    42 -> "This server does not support the selected login method"
    43 -> "Conflicting login parameters"
    44 -> "Invalid API key"
    else -> e.message ?: "Login failed"
}
