package com.isene.tasks.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isene.tasks.data.Hyperlist
import com.isene.tasks.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS = "tasks_prefs"
private const val KEY_URI = "doc_uri"
private const val KEY_COLLAPSED = "collapsed_categories"

data class UiState(
    val pickedUri: Uri? = null,
    val displayName: String? = null,
    val hyperlist: Hyperlist = Hyperlist(),
    val collapsed: Set<String> = emptySet(),
    val toast: String? = null,
    val loading: Boolean = false,
)

class TasksViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TaskRepository(app)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var lastSeenMtime: Long = 0L

    init {
        prefs.getString(KEY_URI, null)?.let { uriStr ->
            val uri = Uri.parse(uriStr)
            val collapsed = prefs.getStringSet(KEY_COLLAPSED, emptySet())?.toSet() ?: emptySet()
            _state.value = _state.value.copy(
                pickedUri = uri,
                collapsed = collapsed,
                displayName = repo.displayName(uri),
            )
            reload()
        }
    }

    fun onFilePicked(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // Some providers won't persist — re-pick after process death.
        }
        prefs.edit().putString(KEY_URI, uri.toString()).apply()
        _state.value = _state.value.copy(
            pickedUri = uri,
            displayName = repo.displayName(uri),
        )
        reload()
    }

    fun reload() {
        val uri = _state.value.pickedUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hl = repo.load(uri)
                lastSeenMtime = repo.lastModified(uri)
                _state.value = _state.value.copy(hyperlist = hl)
            } catch (e: Exception) {
                _state.value = _state.value.copy(toast = "Load failed: ${e.message}")
            }
        }
    }

    fun reloadIfChanged() {
        val uri = _state.value.pickedUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val m = repo.lastModified(uri)
            if (m > 0 && m != lastSeenMtime) {
                reload()
                _state.value = _state.value.copy(toast = "File changed on disk — reloaded")
            }
        }
    }

    /** Apply a Hyperlist → Hyperlist transformation, update state, persist. */
    private fun apply(transform: (Hyperlist) -> Hyperlist) {
        val next = transform(_state.value.hyperlist)
        if (next === _state.value.hyperlist) return
        _state.value = _state.value.copy(hyperlist = next)
        persist()
    }

    // Category ops
    fun addCategory(name: String) = apply { it.addCategory(name) }
    fun renameCategory(catIdx: Int, newName: String) = apply { it.renameCategory(catIdx, newName) }
    fun deleteCategory(catIdx: Int) {
        // Also drop its collapsed-state entry by name if present
        val name = _state.value.hyperlist.categories.getOrNull(catIdx)?.name
        apply { it.deleteCategory(catIdx) }
        if (name != null && _state.value.collapsed.contains(name)) {
            val next = _state.value.collapsed - name
            _state.value = _state.value.copy(collapsed = next)
            prefs.edit().putStringSet(KEY_COLLAPSED, next).apply()
        }
    }
    fun moveCategoryUp(catIdx: Int) = apply { it.moveCategoryUp(catIdx) }
    fun moveCategoryDown(catIdx: Int) = apply { it.moveCategoryDown(catIdx) }

    // Item ops
    fun addItem(catIdx: Int, text: String) = apply { it.addItem(catIdx, text) }
    fun renameItem(catIdx: Int, itemIdx: Int, newText: String) =
        apply { it.renameItem(catIdx, itemIdx, newText) }
    fun deleteItem(catIdx: Int, itemIdx: Int) = apply { it.deleteItem(catIdx, itemIdx) }
    fun moveItemUp(catIdx: Int, itemIdx: Int) = apply { it.moveItemUp(catIdx, itemIdx) }
    fun moveItemDown(catIdx: Int, itemIdx: Int) = apply { it.moveItemDown(catIdx, itemIdx) }
    fun moveItemToCategory(fromCatIdx: Int, itemIdx: Int, toCatIdx: Int) =
        apply { it.moveItemToCategory(fromCatIdx, itemIdx, toCatIdx) }

    fun toggleCollapse(name: String) {
        val cur = _state.value.collapsed
        val next = if (cur.contains(name)) cur - name else cur + name
        _state.value = _state.value.copy(collapsed = next)
        prefs.edit().putStringSet(KEY_COLLAPSED, next).apply()
    }

    fun clearToast() {
        _state.value = _state.value.copy(toast = null)
    }

    private fun persist() {
        val uri = _state.value.pickedUri ?: return
        val hl = _state.value.hyperlist
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.save(uri, hl)
                lastSeenMtime = repo.lastModified(uri)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(toast = "Save failed: ${e.message}")
                }
            }
        }
    }
}
