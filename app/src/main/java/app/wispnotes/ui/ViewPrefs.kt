package app.wispnotes.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.wispnotes.index.Sort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Its own store file, for the same reason as MetaSettings: one DataStore delegate per file name.
private val Context.viewStore by preferencesDataStore("view")
private val KEY_SORT = stringPreferencesKey("home_sort")

/**
 * How the user wants to look at the library. It is a view, not a note property: it never touches the
 * vault, and changing it does not rewrite a file.
 */
object ViewPrefs {
    fun sort(context: Context): Flow<Sort> = context.viewStore.data.map { prefs ->
        Sort.entries.firstOrNull { it.name == prefs[KEY_SORT] } ?: Sort.Latest
    }

    suspend fun setSort(context: Context, sort: Sort) {
        context.viewStore.edit { it[KEY_SORT] = sort.name }
    }
}
