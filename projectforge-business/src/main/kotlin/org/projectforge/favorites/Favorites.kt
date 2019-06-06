package org.projectforge.favorites

import org.projectforge.framework.i18n.translate
import java.util.*

/**
 * Persist the user's sortedSet of favorites sorted by unique names. The user may configure a sortedSet of favorites and my apply one
 * by choosing from a drop down sortedSet.
 *
 * Ensures the uniqueness of favorite's names.
 */
class Favorites<T : AbstractFavorite>() {
    private val log = org.slf4j.LoggerFactory.getLogger(Favorites::class.java)

    private val sortedSet: SortedSet<T> = sortedSetOf()

    fun add(filter: T) {
        sortedSet.add(filter)
        fixNames()
    }

    fun remove(name: String) {
        fixNames()
        sortedSet.removeIf { it.name == name }
    }

    /**
     * Fixes empty names and doublets of names.
     */
    private fun fixNames() {
        val namesSet = mutableSetOf<String>()
        sortedSet.forEach {
            if (it.name.isNullOrBlank())
                it.name = getAutoName() // Fix empty names
            if (namesSet.contains(it.name)) {
                // Doublet found
                it.name = getAutoName(it.name)
            }
            namesSet.add(it.name)
        }
    }

    fun getAutoName(prefix: String? = null): String {
        var _prefix = prefix ?: translate("calendar.filter.untitled")
        if (sortedSet.isEmpty()) {
            return _prefix
        }
        val existingNames = sortedSet.map { it.name }
        if (!existingNames.contains(_prefix))
            return _prefix
        for (i in 1..30) {
            val name = "$_prefix $i"
            if (!existingNames.contains(name))
                return name
        }
        return _prefix // Giving up, 1..30 are already used.
    }


    val favoriteNames: List<String>
        get() {
            fixNames()
            return sortedSet.map { it.name }
        }

    fun getFilter(index: Int): T? {
        if (index < 0) return null // No filter is marked as active.
        if (index < sortedSet.size) {
            // Get the user's active filter:
            return sortedSet.elementAt(index)
        }
        log.error("Favorite index #$index is out of array bounds [0..${sortedSet.size - 1}].")
        return null
    }

    internal fun getFilter(name: String): T? {
        sortedSet.forEach {
            if (name == it.name)
                return it
        }
        log.error("Favorite named '$name' not found.")
        return null
    }
}
