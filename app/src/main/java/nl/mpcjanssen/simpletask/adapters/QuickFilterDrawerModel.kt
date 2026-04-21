package nl.mpcjanssen.simpletask.adapters

import nl.mpcjanssen.simpletask.dates.DateLens

sealed class QuickFilterDrawerRow {
    data class Header(val label: String, val kind: HeaderKind) : QuickFilterDrawerRow()
    data class DateLensItem(val lens: DateLens, val label: String) : QuickFilterDrawerRow()
    data class ContextItem(val value: String) : QuickFilterDrawerRow()
    data class ProjectItem(val value: String) : QuickFilterDrawerRow()
}

enum class HeaderKind {
    DATE_LENS,
    CONTEXT,
    PROJECT
}

data class QuickFilterDrawerModel(
    val rows: List<QuickFilterDrawerRow>,
    val dateLensHeaderPosition: Int,
    val contextHeaderPosition: Int,
    val projectsHeaderPosition: Int
) {
    fun indexOfLens(lens: DateLens): Int {
        return rows.indexOfFirst { it is QuickFilterDrawerRow.DateLensItem && it.lens == lens }
    }

    fun indexOfContext(value: String): Int {
        return rows.indexOfFirst { it is QuickFilterDrawerRow.ContextItem && it.value == value }
    }

    fun indexOfProject(value: String): Int {
        return rows.indexOfFirst { it is QuickFilterDrawerRow.ProjectItem && it.value == value }
    }

    companion object {
        fun build(
            dateLensHeader: String,
            dateLensLabels: Map<DateLens, String>,
            contextHeader: String,
            contexts: List<String>,
            projectHeader: String,
            projects: List<String>
        ): QuickFilterDrawerModel {
            val rows = ArrayList<QuickFilterDrawerRow>()
            val dateLensHeaderPosition = rows.size
            rows.add(QuickFilterDrawerRow.Header(dateLensHeader, HeaderKind.DATE_LENS))
            DateLens.values().forEach { lens ->
                rows.add(QuickFilterDrawerRow.DateLensItem(lens, dateLensLabels[lens] ?: lens.displayName))
            }
            val contextHeaderPosition = rows.size
            rows.add(QuickFilterDrawerRow.Header(contextHeader, HeaderKind.CONTEXT))
            rows.addAll(contexts.sorted().map { QuickFilterDrawerRow.ContextItem(it) })
            val projectsHeaderPosition = rows.size
            rows.add(QuickFilterDrawerRow.Header(projectHeader, HeaderKind.PROJECT))
            rows.addAll(projects.sorted().map { QuickFilterDrawerRow.ProjectItem(it) })

            return QuickFilterDrawerModel(
                rows = rows,
                dateLensHeaderPosition = dateLensHeaderPosition,
                contextHeaderPosition = contextHeaderPosition,
                projectsHeaderPosition = projectsHeaderPosition
            )
        }
    }
}

