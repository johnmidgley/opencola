package opencola.server

import java.awt.AWTEvent
import java.awt.Toolkit


object WakeUpDetector {
    @JvmStatic
    fun main(args: Array<String>) {
        Toolkit.getDefaultToolkit().addAWTEventListener({ event: AWTEvent ->
            if (event.id == AWTEvent.RESERVED_ID_MAX + 1) {
                // The computer has just woken up from sleep
                println("Wake up detected!")
            }
        }, (AWTEvent.RESERVED_ID_MAX + 1).toLong())

        while (true) {
            Thread.sleep(1000)
            print(".")
        }
    }
}

object WakeUpDetector2 {
    @JvmStatic
    fun main(args: Array<String>) {
        Toolkit.getDefaultToolkit().addAWTEventListener({ event: AWTEvent ->
                println("event: $event")
        }, 0xFFFFFFFFL)

        while (true) {
            Thread.sleep(1000)
            print(".")
        }
    }


}
