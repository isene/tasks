package com.isene.tasks.data

/**
 * Minimal hyperlist model — kastrup's triage and scribe write the same
 * shape: tab-indented hierarchy where one leading tab = category and
 * two leading tabs = item under the previous category. We deliberately
 * support only that subset; anything deeper or with formatting tokens
 * is preserved as a raw item line so a round-trip through this app
 * doesn't lose data we don't render.
 *
 * Everything is IMMUTABLE: every mutation returns a brand-new
 * Hyperlist with new Category instances. Required so Compose's diff
 * sees new object identities and recomposes the affected rows
 * immediately on add/edit/delete/move, rather than waiting for some
 * other interaction to force a refresh.
 */
data class Item(val text: String, val raw: String) {
    companion object {
        fun fromRaw(rawLine: String): Item {
            val trimmed = rawLine.trimStart('\t')
            return Item(text = trimmed, raw = rawLine)
        }
        fun of(text: String): Item = Item(text, "\t\t$text")
    }
}

data class Category(val name: String, val items: List<Item> = emptyList())

data class Hyperlist(val categories: List<Category> = emptyList()) {

    // ---- Category-level ops ----

    fun addCategory(name: String): Hyperlist =
        copy(categories = categories + Category(name))

    fun renameCategory(catIdx: Int, newName: String): Hyperlist =
        copy(categories = categories.mapIndexed { i, c ->
            if (i == catIdx) c.copy(name = newName) else c
        })

    fun deleteCategory(catIdx: Int): Hyperlist =
        copy(categories = categories.filterIndexed { i, _ -> i != catIdx })

    /** Swap a category with the one above. No-op at top. */
    fun moveCategoryUp(catIdx: Int): Hyperlist {
        if (catIdx <= 0 || catIdx >= categories.size) return this
        val list = categories.toMutableList()
        val tmp = list[catIdx - 1]
        list[catIdx - 1] = list[catIdx]
        list[catIdx] = tmp
        return copy(categories = list)
    }

    /** Swap a category with the one below. No-op at bottom. */
    fun moveCategoryDown(catIdx: Int): Hyperlist {
        if (catIdx < 0 || catIdx >= categories.size - 1) return this
        val list = categories.toMutableList()
        val tmp = list[catIdx + 1]
        list[catIdx + 1] = list[catIdx]
        list[catIdx] = tmp
        return copy(categories = list)
    }

    // ---- Item-level ops ----

    fun addItem(catIdx: Int, text: String): Hyperlist =
        copy(categories = categories.mapIndexed { i, c ->
            if (i == catIdx) c.copy(items = c.items + Item.of(text)) else c
        })

    fun renameItem(catIdx: Int, itemIdx: Int, newText: String): Hyperlist =
        copy(categories = categories.mapIndexed { i, c ->
            if (i != catIdx) c
            else c.copy(items = c.items.mapIndexed { j, it ->
                if (j == itemIdx) Item.of(newText) else it
            })
        })

    fun deleteItem(catIdx: Int, itemIdx: Int): Hyperlist =
        copy(categories = categories.mapIndexed { i, c ->
            if (i != catIdx) c
            else c.copy(items = c.items.filterIndexed { j, _ -> j != itemIdx })
        })

    /** Swap an item with the one above within its category. */
    fun moveItemUp(catIdx: Int, itemIdx: Int): Hyperlist {
        val cat = categories.getOrNull(catIdx) ?: return this
        if (itemIdx <= 0 || itemIdx >= cat.items.size) return this
        val newItems = cat.items.toMutableList()
        val tmp = newItems[itemIdx - 1]
        newItems[itemIdx - 1] = newItems[itemIdx]
        newItems[itemIdx] = tmp
        return copy(categories = categories.mapIndexed { i, c ->
            if (i == catIdx) c.copy(items = newItems) else c
        })
    }

    fun moveItemDown(catIdx: Int, itemIdx: Int): Hyperlist {
        val cat = categories.getOrNull(catIdx) ?: return this
        if (itemIdx < 0 || itemIdx >= cat.items.size - 1) return this
        val newItems = cat.items.toMutableList()
        val tmp = newItems[itemIdx + 1]
        newItems[itemIdx + 1] = newItems[itemIdx]
        newItems[itemIdx] = tmp
        return copy(categories = categories.mapIndexed { i, c ->
            if (i == catIdx) c.copy(items = newItems) else c
        })
    }

    /** Move an item from one category to the END of another category. */
    fun moveItemToCategory(fromCatIdx: Int, itemIdx: Int, toCatIdx: Int): Hyperlist {
        if (fromCatIdx == toCatIdx) return this
        val src = categories.getOrNull(fromCatIdx) ?: return this
        val item = src.items.getOrNull(itemIdx) ?: return this
        return copy(categories = categories.mapIndexed { i, c ->
            when (i) {
                fromCatIdx -> c.copy(items = c.items.filterIndexed { j, _ -> j != itemIdx })
                toCatIdx   -> c.copy(items = c.items + item)
                else       -> c
            }
        })
    }

    // ---- Serialization ----

    fun serialize(): String {
        val sb = StringBuilder()
        for (cat in categories) {
            sb.append('\t').append(cat.name).append('\n')
            for (item in cat.items) {
                sb.append("\t\t").append(item.text).append('\n')
            }
        }
        return sb.toString()
    }

    companion object {
        fun parse(content: String): Hyperlist {
            val categories = mutableListOf<Category>()
            var current: MutableList<Item>? = null
            var currentName: String? = null

            fun commit() {
                val n = currentName; val items = current
                if (n != null && items != null) categories.add(Category(n, items.toList()))
                currentName = null; current = null
            }

            for (line in content.lines()) {
                if (line.isBlank()) continue
                val leadingTabs = line.takeWhile { it == '\t' }.length
                when (leadingTabs) {
                    1 -> {
                        commit()
                        val name = line.substring(1).trim()
                        if (name.isNotEmpty()) {
                            currentName = name; current = mutableListOf()
                        }
                    }
                    in 2..Int.MAX_VALUE -> current?.add(Item.fromRaw(line))
                    else -> { /* zero-indent comment / header — skipped */ }
                }
            }
            commit()
            return Hyperlist(categories.toList())
        }
    }
}
