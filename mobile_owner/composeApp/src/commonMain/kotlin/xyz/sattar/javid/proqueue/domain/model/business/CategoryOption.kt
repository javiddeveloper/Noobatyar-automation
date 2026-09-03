package xyz.sattar.javid.proqueue.domain.model.business

/**
 * One selectable category, as the *server* describes it.
 *
 * Distinct from [BusinessCategory] on purpose. That enum is this build's
 * compiled-in copy of the vocabulary; this is whatever the server currently
 * serves. The picker renders these so a category added after the app shipped
 * is still selectable, and the enum is only the offline fallback.
 *
 * [value] is the wire code (`"DENTIST"`) — the only part the API cares about.
 */
data class CategoryOption(
    val value: String,
    val label: String,
)

/** A named section of the picker, e.g. «سلامت و درمان». */
data class CategoryGroup(
    val key: String,
    val label: String,
    val categories: List<CategoryOption>,
)

/**
 * The vocabulary this build ships with, used when the server cannot be
 * reached. Derived from [BusinessCategory] so there is exactly one hardcoded
 * copy of the list in the app.
 */
fun fallbackCategoryGroups(): List<CategoryGroup> =
    BusinessCategory.grouped().map { (group, options) ->
        CategoryGroup(
            key = group.name,
            label = group.persianName,
            categories = options.map { CategoryOption(it.value, it.persianName) },
        )
    }
