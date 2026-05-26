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

    /**
     * Resolve a flat-row drag (from, to indices into the visible
     * LazyColumn) into a semantic move on the Hyperlist:
     *
     * - Header dragged → category reorder (items follow as a block).
     *   The drop position rounds to the nearest category boundary.
     * - Item dragged → moves to whichever category contains the target
     *   slot. If dropped on a Header slot it lands at position 0 of
     *   that category. If dropped on an Item slot it inserts at that
     *   item's position.
     *
     * The flat-row list is recomputed from the current state +
     * collapsed set so the resolver always agrees with what's
     * actually on screen.
     */
    fun onDragMove(fromFlat: Int, toFlat: Int) {
        if (fromFlat == toFlat) return
        val hl = _state.value.hyperlist
        val collapsed = _state.value.collapsed
        val rows = hl.flatRows(collapsed)
        val src = rows.getOrNull(fromFlat) ?: return
        val dst = rows.getOrNull(toFlat) ?: rows.lastOrNull() ?: return

        val next = when (src) {
            is com.isene.tasks.data.Hyperlist.Row.Header -> {
                // Category move. Resolve dst → target category index.
                val targetCatIdx = when (dst) {
                    is com.isene.tasks.data.Hyperlist.Row.Header -> dst.catIdx
                    is com.isene.tasks.data.Hyperlist.Row.Item   -> dst.catIdx
                }
                hl.moveCategoryTo(src.catIdx, targetCatIdx)
            }
            is com.isene.tasks.data.Hyperlist.Row.Item -> {
                when (dst) {
                    is com.isene.tasks.data.Hyperlist.Row.Header ->
                        // Dropped on a category header → land at top of that category.
                        hl.moveItemTo(src.catIdx, src.itemIdx, dst.catIdx, 0)
                    is com.isene.tasks.data.Hyperlist.Row.Item ->
                        // Dropped on an item → take that item's slot.
                        hl.moveItemTo(src.catIdx, src.itemIdx, dst.catIdx, dst.itemIdx)
                }
            }
        }
        if (next === hl) return
        _state.value = _state.value.copy(hyperlist = next)
        persist()
    }

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
