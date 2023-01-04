package opencola.server

import java.util.prefs.Preferences


object SleepDetector {
    @JvmStatic
    fun main(args: Array<String>) {
        val prefs =
            Preferences.systemRoot().node("HKEY_LOCAL_MACHINE\\Hardware\\Description\\System\\MultifunctionAdapter\\0")

        prefs.addPreferenceChangeListener { evt ->
            if ("SystemPowerStatus" == evt.key) {
                println("Computer woke from sleep")
            }
        }

        while (true) {
            Thread.sleep(1000)
            print(".")
        }
    }
}