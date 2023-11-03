package io.opencola.relay.cli

import java.nio.file.Path
import java.security.KeyPair

data class Context(val storagePath: Path, val keyPair: KeyPair)