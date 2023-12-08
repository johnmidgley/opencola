package io.opencola.io

const val escape = "\u001B"
const val red = "$escape[31m"
const val yellow = "$escape[33m"
const val reset = "$escape[0m"

enum class Color(val code: String) {
    RED("$escape[31m"),
    YELLOW("$escape[33m"),
}

fun colorize(color: Color, s: String): String {
    return "${color.code}$s$reset"
}