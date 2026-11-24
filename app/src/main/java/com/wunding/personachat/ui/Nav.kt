package com.wunding.personachat.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wunding.personachat.PersonaChatApp

@Composable
fun PersonaNav(app: PersonaChatApp) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "splash") {
        composable("splash") {
            SplashScreen(onDone = {
                nav.navigate("home") { popUpTo("splash") { inclusive = true } }
            })
        }
        composable("home") {
            HomeScreen(
                app = app,
                onOpenPack = { id -> nav.navigate("detail/" + android.net.Uri.encode(id)) },
                onOpenChat = { sid -> nav.navigate("chat/" + android.net.Uri.encode(sid)) },
                onOpenSettings = { nav.navigate("settings") },
                onOpenModels = { nav.navigate("models") },
                onOpenSessions = { nav.navigate("sessions") },
                onOpenVoices = { nav.navigate("voices") },
            )
        }
        composable("detail/{id}") { back ->
            val id = back.arguments?.getString("id") ?: ""
            PersonaDetailScreen(
                app = app,
                packId = java.net.URLDecoder.decode(id, "UTF-8"),
                onBack = { nav.popBackStack() },
                onStartChat = { sid -> nav.navigate("chat/" + android.net.Uri.encode(sid)) },
                onOpenChats = { nav.navigate("personaChats/" + android.net.Uri.encode(id)) },
            )
        }
        composable("chat/{sid}") { back ->
            val sid = back.arguments?.getString("sid") ?: ""
            ChatScreen(
                app = app,
                sessionId = java.net.URLDecoder.decode(sid, "UTF-8"),
                onBack = { nav.popBackStack() },
                onDetail = { pid -> nav.navigate("detail/" + android.net.Uri.encode(pid)) },
                onMemory = { id -> nav.navigate("memory/" + android.net.Uri.encode(id)) },
                onSettings = { nav.navigate("settings") },
                onSwitchSession = { sid -> nav.navigate("chat/" + android.net.Uri.encode(sid)) },
            )
        }
        composable("personaChats/{id}") { back ->
            val id = back.arguments?.getString("id") ?: ""
            PersonaChatsScreen(
                app = app,
                packId = java.net.URLDecoder.decode(id, "UTF-8"),
                onBack = { nav.popBackStack() },
                onOpen = { sid -> nav.navigate("chat/" + android.net.Uri.encode(sid)) },
            )
        }
        composable("memory/{sid}") { back ->
            val sid = back.arguments?.getString("sid") ?: ""
            MemoryScreen(app = app, sessionId = java.net.URLDecoder.decode(sid, "UTF-8"), onBack = { nav.popBackStack() })
        }
        composable("sessions") {
            SessionsScreen(
                app = app,
                onBack = { nav.popBackStack() },
                onOpen = { sid -> nav.navigate("chat/" + android.net.Uri.encode(sid)) },
            )
        }
        composable("settings") {
            SettingsScreen(app = app, onBack = { nav.popBackStack() }, onOpenModels = { nav.navigate("models") })
        }
        composable("models") {
            ModelStoreScreen(app = app, onBack = { nav.popBackStack() })
        }
        composable("voices") {
            VoiceScreen(app = app, onBack = { nav.popBackStack() })
        }
    }
}

fun newSessionId(): String = "s-" + System.currentTimeMillis().toString(36) + "-" + (1000..9999).random()
