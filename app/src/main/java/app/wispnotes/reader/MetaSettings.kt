package app.wispnotes.reader

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Its own store file: two DataStore delegates on "wisp" would crash at start.
private val Context.metaStore by preferencesDataStore("meta")
private val KEY_SUMMARISE = booleanPreferencesKey("summarise_reels")
private val KEY_LOGGED_IN = booleanPreferencesKey("meta_logged_in")

/**
 * The two facts the reader needs before it is worth opening: does the user want reels summarised,
 * and is this phone signed in to meta.ai.
 *
 * [LOGGED_IN] is only a memory of the last live check of meta.ai's own login button. It is a hint for
 * the save sheet, never the authority: the reader checks the page itself every time.
 */
object MetaSettings {
    /** Off by default: a shared reel is saved, and nothing is sent anywhere. */
    fun summarise(context: Context): Flow<Boolean> = context.metaStore.data.map { it[KEY_SUMMARISE] ?: false }

    suspend fun setSummarise(context: Context, on: Boolean) {
        context.metaStore.edit { it[KEY_SUMMARISE] = on }
    }

    fun loggedIn(context: Context): Flow<Boolean> = context.metaStore.data.map { it[KEY_LOGGED_IN] ?: false }

    suspend fun setLoggedIn(context: Context, on: Boolean) {
        context.metaStore.edit { it[KEY_LOGGED_IN] = on }
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
 * The whole rule, as one pure function: the toggle decides whether anything runs, the login state only
 * decides whether it is worth opening the reader.
 */
object ReelPolicy {
    fun action(summarise: Boolean, loggedIn: Boolean): ReelAction = when {
        !summarise -> ReelAction.SaveOnly
        loggedIn -> ReelAction.ReadNow
        else -> ReelAction.SaveAndWarnLogin
    }
}
