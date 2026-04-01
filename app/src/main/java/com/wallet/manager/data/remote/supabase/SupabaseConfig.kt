package com.wallet.manager.data.remote.supabase

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SupabaseConfig {
    private const val SUPABASE_URL = "https://kfjntmhfctgsassynzte.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imtmam50bWhmY3Rnc2Fzc3luenRlIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQ5NjUwODksImV4cCI6MjA5MDU0MTA4OX0.uAbKdkC7hDsyb_-i-gClN9EDcfgnzdiQ6-dQuBESi9s"
    private const val ADMIN_EMAIL = "admin@walletai.local"
    private const val ADMIN_PASSWORD = "admin"
    private val authMutex = Mutex()

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }

    suspend fun ensureAuthenticated() {
        authMutex.withLock {
            if (client.auth.currentSessionOrNull() != null) return

            try {
                client.auth.signInWith(Email) {
                    email = ADMIN_EMAIL
                    password = ADMIN_PASSWORD
                }
                return
            } catch (_: Throwable) {
                // Fall through to sign-up for first-run bootstrap.
            }

            try {
                client.auth.signUpWith(Email) {
                    email = ADMIN_EMAIL
                    password = ADMIN_PASSWORD
                }
            } catch (_: Throwable) {
                // Ignore and try sign-in once more below.
            }

            client.auth.signInWith(Email) {
                email = ADMIN_EMAIL
                password = ADMIN_PASSWORD
            }
        }
    }
}
