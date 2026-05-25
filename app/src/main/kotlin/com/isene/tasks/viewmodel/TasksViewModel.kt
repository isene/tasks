package com.isene.tasks.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.isene.tasks.data.Category
import com.isene.tasks.data.Hyperlist
import com.isene.tasks.data.Item
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

    /** Caller passes the URI returned by ActivityResultContracts.OpenDocument. */
    fun onFilePicked(uri: Uri) {
        // Persist permission so it survives reboots.
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // Some providers (e.g. download manager) don't grant persistence —
            // user will have to re-pick after process death. Not fatal.
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

    /** Cheap external-change check; called from the foreground lifecycle. */
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

    private fun mutate(block: (Hyperlist) -> Unit) {
        val hl = _state.value.hyperlist
        block(hl)
        // Trigger recomposition by emitting a shallow copy.
        _state.value = _state.value.copy(hyperlist = hl.copy(categories = hl.categories.toMutableList()))
        persist()
    }

    fun addCategory(name: String) = mutate { it.addCategory(name) }
    fun renameCategory(category: Category, newName: String) = mutate { it.renameCategory(category, newName) }
    fun deleteCategory(category: Category) = mutate { it.deleteCategory(category) }
    fun addItem(category: Category, text: String) = mutate { it.addItem(category, text) }
    fun renameItem(category: Category, item: Item, newText: String) = mutate { it.renameItem(category, item, newText) }
    fun deleteItem(category: Category, item: Item) = mutate { it.deleteItem(category, item) }

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
