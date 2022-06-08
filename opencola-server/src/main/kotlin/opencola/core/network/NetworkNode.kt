package opencola.core.network

import com.github.edouardswiac.zerotier.ZTServiceImpl
import com.zerotier.sockets.ZeroTierEventListener
import com.zerotier.sockets.ZeroTierNative
import com.zerotier.sockets.ZeroTierNode
import mu.KotlinLogging
import opencola.core.extensions.hexStringToByteArray
import opencola.core.model.Id
import opencola.core.security.Encryptor
import opencola.core.serialization.LongByteArrayCodec
import opencola.core.storage.AddressBook
import org.apache.commons.math3.analysis.function.Add
import java.nio.file.Path

private val logger = KotlinLogging.logger("NetworkNode")

class NetworkNode(private val storagePath: Path, private val authorityId: Id, private val addressBook: AddressBook, private val encryptor: Encryptor) {
    // TODO: Make install script put the platform dependent version of libzt in the right place. On mac, it needs to be
    //  put in ~/Library/Java/Extensions/ (or try /Library/Java/Extensions/ globally)
    //  Need to figure out where it goes on Linux / Windows
    private val node = ZeroTierNode()

    init {
        if(getAuthToken() == null){
            logger.warn { "No network token specified. Cannot manage peer connections." }
        }
    }

    private fun getAuthToken() : String? {
        return addressBook.getAuthority(authorityId)?.networkToken?.let { String(encryptor.decrypt(authorityId, it)) }
    }

    fun isNetworkTokenValid(networkToken: String) : Boolean {
        try {
            ZTServiceImpl(networkToken).networks
        }catch(e: Exception){
            logger.debug { e }
            return false
        }

        return true
    }

    fun start() {
        node.initFromStorage(storagePath.toString())
        node.initSetEventHandler(MyZeroTierEventListener())
        node.start()

        while (!node.isOnline) {
            // TODO: Break infinite loop
            ZeroTierNative.zts_util_delay(50);
        }
    }

    fun connect(address: String){
        val id = LongByteArrayCodec.decode(address.hexStringToByteArray())
        node.join(id)
    }

    fun disconnect(address: String) {
        val id = LongByteArrayCodec.decode(address.hexStringToByteArray())
        node.leave(id)
    }

    fun getId(): String {
        return node.id.toString(16)
    }
}

internal class MyZeroTierEventListener : ZeroTierEventListener {
    override fun onZeroTierEvent(id: Long, eventCode: Int) {
        if (eventCode == ZeroTierNative.ZTS_EVENT_NODE_UP) {
            logger.info("EVENT_NODE_UP")
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NODE_ONLINE) {
            logger.info("EVENT_NODE_ONLINE: " + java.lang.Long.toHexString(id))
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NODE_OFFLINE) {
            logger.info("EVENT_NODE_OFFLINE")
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NODE_DOWN) {
            logger.info("EVENT_NODE_DOWN")
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NETWORK_READY_IP4) {
            logger.info("ZTS_EVENT_NETWORK_READY_IP4: " + java.lang.Long.toHexString(id))
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NETWORK_READY_IP6) {
            logger.info("ZTS_EVENT_NETWORK_READY_IP6: " + java.lang.Long.toHexString(id))
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NETWORK_DOWN) {
            logger.info("EVENT_NETWORK_DOWN: " + java.lang.Long.toHexString(id))
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NETWORK_OK) {
            logger.info("EVENT_NETWORK_OK: " + java.lang.Long.toHexString(id))
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NETWORK_ACCESS_DENIED) {
            logger.info("EVENT_NETWORK_ACCESS_DENIED: " + java.lang.Long.toHexString(id))
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NETWORK_NOT_FOUND) {
            logger.info("EVENT_NETWORK_NOT_FOUND: " + java.lang.Long.toHexString(id))
        }
    }
}