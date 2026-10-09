package com.caproverforge

import android.app.Application
import android.content.Context
import com.caproverforge.data.CapRoverApi
import com.caproverforge.data.CapRoverRepository
import com.caproverforge.data.SessionStore
import com.caproverforge.data.SettingsStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CaproverForgeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(
    context: Context,
    val sessionStore: SessionStore = SessionStore(context),
) {
    val settingsStore = SettingsStore(context)

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // Some operations (deleting apps with volumes, enabling SSL) block server-side for a while.
        .readTimeout(3, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    val api = CapRoverApi(sessionStore, httpClient)
    val repository = CapRoverRepository(api, sessionStore)
}
