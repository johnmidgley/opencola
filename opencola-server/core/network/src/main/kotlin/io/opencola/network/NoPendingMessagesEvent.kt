package io.opencola.network

import io.opencola.model.Id
import io.opencola.network.providers.ProviderEvent
import io.opencola.network.providers.ProviderEventType

class NoPendingMessagesEvent(val personaId: Id) : ProviderEvent(ProviderEventType.NO_PENDING_MESSAGES)