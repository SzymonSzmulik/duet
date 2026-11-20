package app.duet.music.search

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Past searches, newest first, without repeats (case-insensitive); kept between launches. */
class SearchHistory(private val store: Settings = Settings(), private val max: Int = 20) {
    private val _queries = MutableStateFlow(store.getString(KEY, "").split('\n').filter { it.isNotBlank() })
    val queries: StateFlow<List<String>> = _queries.asStateFlow()

    fun add(query: String) {
        val q = query.trim().takeIf { it.isNotEmpty() } ?: return
        save((listOf(q) + _queries.value.filterNot { it.equals(q, ignoreCase = true) }).take(max))
    }

    fun remove(query: String) = save(_queries.value - query)
    fun clear() = save(emptyList())

    private fun save(list: List<String>) {
        _queries.value = list
        store.putString(KEY, list.joinToString("\n"))
    }

    private companion object { const val KEY = "search.history" }
}
