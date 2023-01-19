package io.opencola.util

import java.net.URI

fun URI.trim() : URI {
    return this.toString().trim('/').let {URI(it) }
}