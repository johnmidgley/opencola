package opencola.server

fun handleAction(action: String, value: String?, mhtml: ByteArray?){
    when(action){
        "like" -> handleLikeAction(value, mhtml)
    }
}

fun handleLikeAction(value: String?, mhtml: ByteArray?){

}