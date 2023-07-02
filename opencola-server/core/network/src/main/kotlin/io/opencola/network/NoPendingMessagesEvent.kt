package io.opencola.network

import io.opencola.model.Id

class NoPendingMessagesEvent(val personaId: Id) : ProviderEvent(ProviderEventType.NO_PENDING_MESSAGES)