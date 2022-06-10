package opencola.core.network

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import opencola.core.network.zerotier.Network
import org.junit.Test
import kotlin.io.path.Path
import kotlin.io.path.readText

class ZeroTierModelTest {
    private fun loadSampleJson(name: String): String {
        return Path(System.getProperty("user.dir")).resolve("../sample-docs").resolve(name).readText()
    }

    @Test
    fun testNetworkModel(){
        val jsonString = loadSampleJson("zero-tier-networks.json")
        val json = Json{ coerceInputValues = true; isLenient = true; ignoreUnknownKeys = true; explicitNulls = false}
        val networks: List<Network> = json.decodeFromString(jsonString)
        println(networks)
    }
}