package io.opencola.core.system

import java.io.File

fun runningInDocker() : Boolean {
    return File("/.dockerenv").exists()
}
