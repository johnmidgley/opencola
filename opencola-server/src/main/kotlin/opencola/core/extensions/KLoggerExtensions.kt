package opencola.core.extensions

import mu.KLogger

fun KLogger.logErrorAndThrow(msg: String) {
    error { msg }
    throw RuntimeException(msg)
}

fun KLogger.errorAndReturnMsg(msg: String) : String {
    error { msg }
    return msg
}