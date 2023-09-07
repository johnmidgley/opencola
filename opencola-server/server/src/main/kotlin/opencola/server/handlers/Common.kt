package opencola.server.handlers

import java.net.InetAddress
import java.net.URI

fun requireNotLocalOCAddress(uri: URI, ocServerPorts: Set<Int>)  {
    if (InetAddress.getByName(uri.host).isLoopbackAddress && ocServerPorts.contains(uri.port)) {
        throw IllegalArgumentException("Saving of local OpenCola resources is not allowed")
    }
}