<img src="../../../img/pull-tab.svg" width="150" />

# search

The search library contains code that facilitates searching for entities. The main interface is the SearchIndex:

```kotlin
interface SearchIndex {
    // Add entities to the search index. Any attribute where isIndexable == true is indexed.
    fun addEntities(vararg entities: Entity)

    // Delete entites for a given authority
    fun deleteEntities(authorityId: Id, vararg entityIds: Id)

    // Get search results. Subsequent pages can be accessed by specifying the pagingToken
    // from the previous page's SearchResults
    fun getResults(
        query: Query,
        maxResults: Int,
        pagingToken: String? = null
    ): SearchResults
}
```

Currently the only implementations is [```LuceneSearchIndex```](./src/main/kotlin/io/opencola/search/LuceneSearchIndex.kt), but it is straightforward to add new implementations (originally there was a Solr index, but to simplify things, Lucene is now used directly).