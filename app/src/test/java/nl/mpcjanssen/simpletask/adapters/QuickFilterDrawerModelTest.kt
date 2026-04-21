package nl.mpcjanssen.simpletask.adapters

import junit.framework.TestCase
import nl.mpcjanssen.simpletask.dates.DateLens

class QuickFilterDrawerModelTest : TestCase() {
    fun testDateLensSectionPrecedesContextsAndProjects() {
        val model = QuickFilterDrawerModel.build(
            dateLensHeader = "Dates",
            dateLensLabels = DateLens.values().associateWith { it.displayName },
            contextHeader = "Lists",
            contexts = listOf("@work"),
            projectHeader = "Tags",
            projects = listOf("+home")
        )

        assertEquals(0, model.dateLensHeaderPosition)
        assertEquals(1, model.indexOfLens(DateLens.ALL))
        assertTrue(model.contextHeaderPosition > model.indexOfLens(DateLens.UPCOMING))
        assertTrue(model.projectsHeaderPosition > model.contextHeaderPosition)
        assertEquals(model.contextHeaderPosition + 1, model.indexOfContext("@work"))
        assertEquals(model.projectsHeaderPosition + 1, model.indexOfProject("+home"))
    }
}

