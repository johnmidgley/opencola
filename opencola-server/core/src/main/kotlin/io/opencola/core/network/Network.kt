package io.opencola.core.network

import com.lordcodes.turtle.shellRun
import java.io.File

//TODO: Install docker by command line
// see "How to Set Up Docker for Mac.mht" in sample docs

// TODO: Peer communication classes should go in this namespace.
//  getFacts(epoch) - Gets all facts from epoch forward
//  getData(dataId) - Gets data for id. Use Merkle trees to support multi-part / multi-source downloads

private val networkPath = File(System.getProperties()["user.dir"].toString(), "network")

fun startNetwork(){
    shellRun(networkPath){
        command("docker-compose", listOf("up", "-d"))
    }
}

fun stopNetwork(){
    shellRun(networkPath){
        command("docker-compose", listOf("down"))
    }
}

fun isNetworkAvailable() : Boolean {
    try {
        // Throws an exception when the docker daemon isn't running
        shellRun(networkPath) { command("docker", listOf("version")) }
    }catch(e: Exception){
        return false
    }

    return true
}
