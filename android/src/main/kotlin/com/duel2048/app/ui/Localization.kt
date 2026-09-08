package com.duel2048.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.duel2048.app.R
import com.duel2048.app.game.DuelError
import com.duel2048.app.ui.components.FxStrings
import java.util.Locale

/** Languages offered in settings: BCP-47 tag to native name. Blank follows the system. */
object Languages {
    val all = listOf(
        "" to "System",
        "en" to "English",
        "ja" to "日本語",
        "zh-CN" to "中文",
        "es" to "Español",
    )
}

/**
 * Keeps the Activity as the base context (dialogs need it) but serves resources for the
 * chosen language, so every stringResource() call follows the in-app setting.
 */
private class LocalizedContext(base: Context, private val res: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = res
}

@Composable
fun rememberLocalizedContext(language: String): Context {
    val base = LocalContext.current
    return remember(base, language) {
        if (language.isBlank()) {
            base
        } else {
            val config = Configuration(base.resources.configuration)
            config.setLocale(Locale.forLanguageTag(language))
            LocalizedContext(base, base.createConfigurationContext(config).resources)
        }
    }
}

@Composable
fun errorText(e: DuelError): String = when (e.code) {
    "name_taken" -> stringResource(R.string.err_name_taken)
    "invalid_name" -> stringResource(R.string.err_invalid_name)
    "invalid_password" -> stringResource(R.string.err_invalid_password)
    "bad_credentials" -> stringResource(R.string.err_bad_credentials)
    "session_expired", "bad_token" -> stringResource(R.string.err_session_expired)
    "not_logged_in" -> stringResource(R.string.err_not_logged_in)
    "connect_failed" -> stringResource(R.string.err_connect_failed, e.detail ?: "")
    "connection_lost" -> stringResource(R.string.err_connection_lost)
    "timeout" -> stringResource(R.string.err_timeout)
    "version" -> stringResource(R.string.err_version, e.detail ?: "")
    else -> stringResource(R.string.err_server, e.detail ?: e.code)
}

@Composable
fun rememberFxStrings(): FxStrings = FxStrings(
    combo = stringResource(R.string.fx_combo),
    attack = stringResource(R.string.fx_attack),
    garbage = stringResource(R.string.fx_garbage),
    shattered = stringResource(R.string.fx_shattered),
)
