package io.opencola.network.providers.relay

import io.opencola.network.providers.ProviderContext
import java.security.PublicKey

class OCRelayNetworkProviderContext(val clientPublicKey: PublicKey) : ProviderContext()