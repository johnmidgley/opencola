package opencola.core.model

// doc.pdf
// id - hash of data
// source - uri source
// parent - id of parent (container or website)
// name, desc, tags, trust, like, rating
open class DataEntity : Entity {
    constructor(authorityId: Id, dataId: Id, mimeType: String) : super(authorityId, dataId){
        this.mimeType = mimeType
    }
    constructor(facts: List<Fact>) : super(facts)

    // URI where data was originally fetched
    var mimeType by stringAttributeDelegate
}