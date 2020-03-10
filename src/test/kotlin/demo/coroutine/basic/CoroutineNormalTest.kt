package demo.coroutine.basic

import demo.Logger.Companion.println
import kotlinx.coroutines.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.concurrent.thread
import kotlin.test.Test

internal class CoroutineNormalTest {

    /**
     * thread {} 是kotlin中启动Thread的语法糖。
     */
    @Test
    fun withThread() {
        thread {
            // 启动线程
            Thread.sleep(1000L) // 阻塞一秒钟
            println("World!") // 在延迟后打印输出
        }
        println("Hello,") // 主线程先打印
        Thread.sleep(2000L) // 阻塞主线程 2 秒钟来保证 JVM 存活
    }

    @Test
    fun withExecutor() {
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            Thread.sleep(1000L) // 阻塞一秒钟
            println("World!") // 在延迟后打印输出
        }
        println("Hello,") // 主线程先打印
        Thread.sleep(2000L) // 阻塞主线程 2 秒钟来保证 JVM 存活
    }

    @Test
    fun withFuture() {
        val executor = Executors.newSingleThreadExecutor()

        fun asyncPow2(i: Int): Future<Int> {
            return executor.submit(Callable {
                println("Power of $i is: ") // 执行时打印
                Thread.sleep(1000L) // 阻塞一秒钟
                i * i
            })
        }

        println("${asyncPow2(5).get()}!") // get()阻塞了主线程打印，直到执行完成
    }

    /**
     * GlobalScope是协程的顶层Scope，launch会启动一个新协程。
     * 协程是非阻塞的，所以需要主线程sleep以等待其完成。
     */
    @Test
    fun withDelaySleep() {
        GlobalScope.launch {
            // 在后台启动一个新的协程并继续
            delay(1000L) // 非阻塞的等待 1 秒钟（默认时间单位是毫秒）
            println("World!") // 在延迟后打印输出
        }
        println("Hello,") // 协程已在等待时主线程还在继续
        Thread.sleep(2000L) // 阻塞主线程 2 秒钟来保证 JVM 存活
    }

    /**
     * runBlocking可以桥接主线程与协程。
     * 与CoroutineScope.launch{}类似，runBlocking{}也会启动一个新协程，但它会阻塞主线程，直到协程执行完成。
     */
    @Test
    fun withRunBlocking() {
        GlobalScope.launch {
            // 在后台启动一个新的协程并继续
            delay(1000L) // 非阻塞的等待 1 秒钟（默认时间单位是毫秒）
            println("World!") // 在延迟后打印输出
        }
        println("Hello,") // 协程已在等待时主线程还在继续
        runBlocking {
            delay(2000L)
        }
    }

    /**
     * 与上例类似，只是将runBlocking移至最外层。
     */
    @Test
    fun withWrappedRunBlocking() = runBlocking {
        // 开始执行主协程
        GlobalScope.launch {
            // 在后台启动一个新的协程并继续
            delay(1000L)
            println("World!")
        }
        println("Hello,") // 主协程在这里会立即执行
        delay(2000L)      // 延迟 2 秒来保证 JVM 存活
    }

    /**
     * 参考java中的join
     * 使用join()来让一个协程阻塞的等待另一个协程的完成。当两个协程的context没有继承关系时使用
     * 与下面一个例子对照
     */
    @Test
    fun withJobJoin() = runBlocking {
        // 开始执行主协程
        val job = GlobalScope.launch {
            // 在后台启动一个新的协程并继续
            delay(1000L)
            println("World!  in scope ${this.coroutineContext}")
        }
        println("Hello,  in scope ${this.coroutineContext}") // 主协程在这里会立即执行
        job.join()
    }

    /**
     * 无需使用join()，因为launch创建的协程的Context继承于外层Scope的Context
     * 与上面一个例子对照
     */
    @Test
    fun lunchCoroutineInScope() = runBlocking {
        // this: CoroutineScope
        launch {
            // 在 runBlocking 作用域中启动一个新协程
            delay(1000L)
            println("World!  in scope ${this.coroutineContext}")
        }
        println("Hello,  in scope ${this.coroutineContext}")
    }

    /**
     * runBlocking和coroutineScope都会创建一个新的scope，只是后者在等待子协程运行结束时不会阻塞线程运行，也自然不会阻塞同线程运行的不同作用域的协程
     */
    @Test
    fun withCoroutineScope() = runBlocking {
        // this: CoroutineScope
        println("Method start") // 1.必然是第一个么

        launch {
            delay(200L) // 与4的协程同时在运行
            println("Task from runBlocking") // 3.这一行delay比4短
        }

        println("Will launch coroutine scope ") // 5.这一行在内嵌 launch 执行完毕后才输出

        coroutineScope {
            // 创建一个新的协程作用域，且运行的子协程不阻塞当前线程
            launch {
                delay(500L) //与3的协程同时在运行
                println("Task from nested launch") // 4.这一行delay比4长
            }

            delay(100L)
            println("Task from coroutine scope") // 2.这一行会在内嵌 launch 之前输出
        }

        println("Coroutine scope is over") // 5.这一行在内嵌 launch 执行完毕后才输出
    }

    /**
     * 协程内调用的可挂起函数需要加上suspend关键字
     */
    @Test
    fun callSuspendFunction() = runBlocking {
        // this: CoroutineScope
        launch {
            delayWithSuspend()
        }
        println("Hello,")
    }

    private suspend fun delayWithSuspend() {
        // 在 runBlocking 作用域中启动一个新协程
        delay(1000L)
        println("World!")
    }

    @Test
    fun asyncReadFile() = runBlocking {
        launch {
            val fileA = async { readFile("/etc/fstab") }
            val fileB = async { readFile("/etc/shells") }
            println(fileA.await())
            println(fileB.await())
        }
        println("Hello")
    }

    private suspend fun readFile(path: String): String {
        println("read file from path: $path")
        delay(1000L)
        return "content of file: $path"
    }
}
