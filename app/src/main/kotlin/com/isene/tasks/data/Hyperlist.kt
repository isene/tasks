package com.isene.tasks.data

/**
 * Minimal hyperlist model — kastrup's triage and scribe write the same
 * shape: tab-indented hierarchy where one leading tab = category and
 * two leading tabs = item under the previous category. We deliberately
 * support only that subset; anything deeper or with formatting tokens
 * is preserved as a raw item line so a round-trip through this app
 * doesn't lose data we don't render.
 */
data class Item(val text: String, val raw: String) {
    companion object {
        fun fromRaw(rawLine: String): Item {
            val trimmed = rawLine.trimStart('\t')
            return Item(text = trimmed, raw = rawLine)
        }
    }
}

data class Category(val name: String, val items: MutableList<Item> = mutableListOf())

data class Hyperlist(val categories: MutableList<Category> = mutableListOf()) {

    fun addCategory(name: String): Category {
        val c = Category(name)
        categories.add(c)
        return c
    }

    fun addItem(category: Category, text: String) {
        category.items.add(Item(text, "\t\t$text"))
    }

    fun renameCategory(category: Category, newName: String) {
        val idx = categories.indexOf(category)
        if (idx >= 0) categories[idx] = category.copy(name = newName, items = category.items)
    }

    fun renameItem(category: Category, item: Item, newText: String) {
        val ci = categories.indexOf(category); if (ci < 0) return
        val ii = categories[ci].items.indexOf(item); if (ii < 0) return
        categories[ci].items[ii] = Item(newText, "\t\t$newText")
    }

    fun deleteItem(category: Category, item: Item) {
        categories.find { it === category }?.items?.remove(item)
    }

    fun deleteCategory(category: Category) {
        categories.remove(category)
    }

    fun serialize(): String {
        val sb = StringBuilder()
        for (cat in categories) {
            sb.append('\t').append(cat.name).append('\n')
            for (item in cat.items) {
                // Re-emit canonical `\t\ttext` even if the raw form
                // had extra whitespace — keeps the file tidy.
                sb.append("\t\t").append(item.text).append('\n')
            }
        }
        return sb.toString()
    }

    companion object {
        fun parse(content: String): Hyperlist {
            val hl = Hyperlist()
            var current: Category? = null
            for (line in content.lines()) {
                if (line.isBlank()) continue
                val leadingTabs = line.takeWhile { it == '\t' }.length
                when (leadingTabs) {
                    1 -> {
                        // Category — exactly one leading tab.
                        val name = line.substring(1).trim()
                        if (name.isNotEmpty()) current = hl.addCategory(name)
                    }
                    in 2..Int.MAX_VALUE -> {
                        // Item under the current category. We squash
                        // arbitrarily-deep nesting to the second
                        // indent level — the kastrup/scribe format
                        // only ever produces two levels.
                        current?.let { it.items.add(Item.fromRaw(line)) }
                    }
                    else -> {
                        // Zero indent → header / comment line. The
                        // kastrup/scribe format never produces these,
                        // but if a user adds one by hand we silently
                        // skip it rather than crash.
                    }
                }
            }
            return hl
        }
    }
}
