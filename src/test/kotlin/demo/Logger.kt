package demo

import java.text.SimpleDateFormat
import java.util.*

class Logger {
    companion object {
        private val dateFormatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS")

        fun println(s: String) {
            kotlin.io.println("${dateFormatter.format(Date())} [${Thread.currentThread().name}]: $s")
        }
    }
}
