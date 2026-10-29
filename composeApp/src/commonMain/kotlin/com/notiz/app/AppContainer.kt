package com.notiz.app

import androidx.compose.runtime.staticCompositionLocalOf
import com.notiz.app.data.AuthRepository
import com.notiz.app.data.ChatRepository
import com.notiz.app.data.ComplaintRepository
import com.notiz.app.data.DuesRepository
import com.notiz.app.data.NoticeRepository
import com.notiz.app.data.PollRepository
import com.notiz.app.data.SocietyRepository
import com.notiz.app.data.createAppSupabaseClient

/** Manual dependency container — one per app process. */
class AppContainer {
    private val supabase = createAppSupabaseClient()

    val auth = AuthRepository(supabase)
    val societies = SocietyRepository(supabase)
    val notices = NoticeRepository(supabase)
    val dues = DuesRepository(supabase)
    val complaints = ComplaintRepository(supabase)
    val polls = PollRepository(supabase)
    val chat = ChatRepository(supabase)

    companion object {
        /** Created lazily so Android and iOS share a single Supabase client. */
        val instance: AppContainer by lazy { AppContainer() }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
