package io.opencola.io

const val escape = "\u001B"
const val red = "$escape[31m"
const val reset = "$escape[0m"

fun printlnErr(message: String) {
    System.err.println("$red$message$reset")
}