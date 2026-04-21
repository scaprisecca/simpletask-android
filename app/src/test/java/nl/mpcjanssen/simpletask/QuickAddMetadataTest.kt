package nl.mpcjanssen.simpletask

import junit.framework.TestCase

class QuickAddMetadataTest : TestCase() {
    fun testCollectSuggestionsBuildsContextsAndProjectsFromTargetLines() {
        val suggestions = QuickAddMetadata.collectSuggestions(
            listOf(
                "Buy milk @errands +home",
                "Plan shelves @woodshop +atelier"
            )
        )

        assertEquals(listOf("errands", "woodshop"), suggestions.contexts)
        assertEquals(listOf("atelier", "home"), suggestions.projects)
    }
}
