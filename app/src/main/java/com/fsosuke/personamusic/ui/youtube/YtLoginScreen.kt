package com.fsosuke.personamusic.ui.youtube

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fsosuke.personamusic.ui.components.LocalAppContainer
import com.fsosuke.personamusic.ui.components.RansomText
import com.fsosuke.personamusic.ui.theme.LocalPersonaColors
import com.fsosuke.personamusic.ui.theme.PersonaType
import com.fsosuke.personamusic.youtube.InnerTube
import kotlinx.coroutines.launch

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
        "&continue=https%3A%2F%2Fmusic.youtube.com%2F"

/**
 * Google sign-in inside a WebView. As soon as music.youtube.com is reached with a session cookie,
 * the cookie is validated and stored; the password itself never touches the app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtLoginScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val colors = LocalPersonaColors.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf(false) }

    fun submit(cookie: String) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            runCatching { container.ytAccounts.signIn(cookie) }
                .onSuccess { onDone() }
                .onFailure { error = it.localizedMessage ?: it.javaClass.simpleName }
            busy = false
        }
    }
    val onCookie by rememberUpdatedState(::submit)

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            RansomText("Sign in", fontSize = 24.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { manual = true }) { Text("Cookie", color = colors.accent) }
        }
        error?.let {
            Text("⚠ $it", color = colors.accent, style = PersonaType.caption, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        val cookies = CookieManager.getInstance()
                        cookies.setAcceptCookie(true)
                        cookies.setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                if (url?.startsWith(InnerTube.ORIGIN) == true) {
                                    val cookie = cookies.getCookie(InnerTube.ORIGIN)
                                    if (InnerTube.hasAuthCookie(cookie)) onCookie(cookie)
                                }
                            }
                        }
                        loadUrl(LOGIN_URL)
                    }
                },
            )
            if (busy) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }
        }
    }

    if (manual) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            containerColor = Color.Black,
            shape = RectangleShape,
            title = { Text("Paste cookie", color = Color.White, style = PersonaType.title) },
            text = {
                Column {
                    Text(
                        "If Google blocks the sign-in here: sign in to music.youtube.com on a computer, " +
                            "copy the full \"Cookie\" request header from the developer tools and paste it here.",
                        color = colors.muted,
                        style = PersonaType.caption,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.heightIn(min = 120.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    manual = false
                    submit(text.trim().removePrefix("Cookie:").trim())
                }) { Text("Sign in") }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("Cancel") } },
        )
    }
}
