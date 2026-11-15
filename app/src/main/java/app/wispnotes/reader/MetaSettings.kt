package app.wispnotes.reader

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Its own store file: two DataStore delegates on "wisp" would crash at start.
private val Context.metaStore by preferencesDataStore("meta")
private val KEY_SUMMARISE = stringPreferencesKey("summarise_reels")
private val KEY_LOGIN = stringPreferencesKey("meta_login")

/**
 * The two facts the reader needs before it is worth opening: does the user want reels summarised, and
 * what did the last live check of meta.ai say about the session.
 *
 * The login state is deliberately three-valued. **Unknown** is not "signed out": on a fresh install
 * there is no answer yet, and guessing "signed out" would tell a signed-in user to log in again. It is
 * only ever a hint for the save sheet; the reader checks the page itself every time.
 */
object MetaSettings {
    /** Off by default: a shared reel is saved, and nothing is sent anywhere. */
    fun summarise(context: Context): Flow<Boolean> = context.metaStore.data.map { it[KEY_SUMMARISE] == "on" }

    suspend fun setSummarise(context: Context, on: Boolean) {
        context.metaStore.edit { it[KEY_SUMMARISE] = if (on) "on" else "off" }
    }

    /** True signed in, false signed out, null when this phone has never been checked. */
    fun loginState(context: Context): Flow<Boolean?> = context.metaStore.data.map {
        when (it[KEY_LOGIN]) {
            "in" -> true
            "out" -> false
            else -> null
        }
    }

    suspend fun setLoggedIn(context: Context, signedIn: Boolean) {
        context.metaStore.edit { it[KEY_LOGIN] = if (signedIn) "in" else "out" }
    }
}

/** What a share of an Instagram reel should do. */
enum class ReelAction {
    /** Save the note and stop. The default. */
    SaveOnly,

    /** Save, then open the reader. */
    ReadNow,

    /** Save, then tell the user to log in. Opening the reader would only hit a wall. */
    SaveAndWarnLogin,
}

/**
 * The whole rule, as one pure function. The toggle decides whether anything runs; the login hint only
 * decides whether it is worth opening the reader. `loggedIn == null` means "not checked yet", which
 * opens the reader and lets the live check settle it.
 */
object ReelPolicy {
    fun action(summarise: Boolean, loggedIn: Boolean?): ReelAction = when {
        !summarise -> ReelAction.SaveOnly
        loggedIn == false -> ReelAction.SaveAndWarnLogin
        else -> ReelAction.ReadNow
    }
}
