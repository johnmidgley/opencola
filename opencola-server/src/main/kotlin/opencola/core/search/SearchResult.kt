package opencola.core.search

import opencola.core.model.Id

// TODO: Add highlighting
// TODO: Should probably return full entity, but somewhat expensive, and won't be compatible with highlighting
class SearchResult(val entityId: Id, val name: String?, val description: String?)