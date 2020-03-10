package demo.coroutine.scope

import demo.Logger.Companion.println
import kotlinx.coroutines.*
import kotlin.test.Test

internal class CoroutineScopeTest {

    open class Activity {

        init {
            this.start()
        }

        open fun start() {
            println("activity started")
        }

        open fun destroy() {
            println("activity destroyed")
        }
    }

    // ScopedActivity实现了带有Default dispatcher的CoroutineScope，并在destroy中调用了scope.cancel
    class ScopedActivity : Activity(), CoroutineScope by CoroutineScope(Dispatchers.Default) {

        fun doSomething() {
            // 在示例中启动了 10 个协程，且每个都工作了不同的时长
            repeat(10) { i ->
                launch {
                    delay((i + 1) * 200L) // 延迟 200 毫秒、400 毫秒、600 毫秒等等不同的时间
                    println("Coroutine $i is done")
                }
            }
        }

        override fun destroy() {
            super.destroy()
            cancel("Activity destroyed")
        }
    }

    /**
     * 绑定了scope的activity可以在销毁时自动取消内部运行的协程
     */
    @Test
    fun scopedActivity() = runBlocking {
        val activity = ScopedActivity()
        activity.doSomething()
        println("Launched coroutine")
        delay(500L) // 延迟半秒钟
        println("Destroying activity!")
        activity.destroy() // 取消所有的协程
        delay(1000) // 为了在视觉上确认它们没有工作
    }
}
