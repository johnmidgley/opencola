package io.opencola.system

import java.io.File

fun runningInDocker() : Boolean {
    return File("/.dockerenv").exists()
}
