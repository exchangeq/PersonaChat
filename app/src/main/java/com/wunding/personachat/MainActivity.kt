package com.wunding.personachat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.wunding.personachat.data.PersonaDb
import com.wunding.personachat.data.Settings
import com.wunding.personachat.data.SettingsStore
import com.wunding.personachat.data.openDb
import com.wunding.personachat.ui.AccentAmber
import com.wunding.personachat.ui.AccentGreen
import com.wunding.personachat.ui.PersonaNav
import com.wunding.personachat.ui.PersonaTheme

class PersonaChatApp : android.app.Application() {
    lateinit var db: PersonaDb
    override fun onCreate() {
        super.onCreate()
        db = openDb(this)
        com.wunding.personachat.data.AppStorage.root(this)
        com.wunding.personachat.data.DeviceLlm.init(this)
        com.wunding.personachat.data.GgufLlm.init(this)
        com.wunding.personachat.data.Voice.init(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PersonaChatApp
        setContent {
            val settings by SettingsStore.flow(this).collectAsState(initial = Settings())
            val dark = when (settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val accent = if (settings.accent == "amber") AccentAmber else AccentGreen
            PersonaTheme(dark = dark, accent = accent) {
                PersonaNav(app)
            }
        }
    }
}
