package demo.coroutine.callback

import demo.Logger.Companion.println
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test

class CoroutineCallbackTest {

    class Runner : Thread() {

        private lateinit var callback: Callback

        fun withCallback(callback: Callback): Runner {
            this.callback = callback
            return this
        }

        override fun run() {
            callback.start()
            println("job running")
            callback.run()
            sleep(500)
            println("job done")
            callback.done()
        }
    }

    interface Callback {
        fun start()
        fun run()
        fun done()
    }

    @Test
    fun simpleCallback() {
        println("job start")

        Runner().withCallback(object : Callback {
            override fun start() {
                println("job start callback ")
            }

            override fun run() {
                println("job run callback")
            }

            override fun done() {
                println("job done callback")
            }
        }).start()
        Thread.sleep(1000L) // 阻塞主线程 2 秒钟来保证 JVM 存活
    }

    class RxRunner {

        private val runner: Runner = Runner().withCallback(object : Callback {
            override fun start() {
                startCallback()
            }

            override fun run() {
                runCallback()
            }

            override fun done() {
                doneCallback()
            }
        })

        private lateinit var startCallback: () -> Unit
        private lateinit var runCallback: () -> Unit
        private lateinit var doneCallback: () -> Unit

        fun doOnStart(block: () -> Unit): RxRunner {
            startCallback = block
            return this
        }

        fun doOnRun(block: () -> Unit): RxRunner {
            runCallback = block
            return this
        }

        fun doOnDone(block: () -> Unit): RxRunner {
            doneCallback = block
            return this
        }

        fun subscribe() {
            runner.start()
        }
    }

    @Test
    fun callback2Chain() {
        println("job start")

        RxRunner().doOnStart {
            println("job start callback")
        }.doOnRun {
            println("job run callback")
        }.doOnDone {
            println("job done callback")
        }.subscribe()

        Thread.sleep(1000L) // 阻塞主线程 2 秒钟来保证 JVM 存活
    }

    /**
     * suspendCoroutine提供了回调与挂起桥接方法。suspendCoroutine会主动挂起当前协程，
     * 如果要恢复挂起可以在执行结束时调用Continuation.resume或Continuation.resumeWithException
     */
    private suspend fun Runner.await(): Unit = suspendCoroutine {
        Runner().withCallback(object : Callback {
            override fun start() {}

            override fun run() {}

            override fun done() {
                it.resume(Unit)
            }
        }).run()
    }

    @Test
    fun callbackToCoroutine() {
        println("job start")

        runBlocking {
            Runner().await()
        }
        println("job finish")
    }

    /**
     * 参考java中的yield
     * yield方法可以让出协程的Dispatcher所属的线程或线程池的执行权（对unconfined dispatcher无效）。
     */
    @ObsoleteCoroutinesApi
    @Test
    fun testYield() {
        println("job start")

        // 给定一个单线程来运行下面的协程
        val singleThreadCtext = newSingleThreadContext("cxt")

        val cpuConsumingJob = { duration: Long ->
            Thread.sleep(duration) // 模拟cpu占用
        }

        runBlocking {
            launch(singleThreadCtext) {
                // 这里其实不指定dispatcher也可以，因为默认会运行在主线程
                repeat(3) {
                    cpuConsumingJob(50)
                    println("job1 repeat $it")
                    yield() // 如果没有yield，此协程会连续repeat三次执行
                }
            }

            launch(singleThreadCtext) {
                repeat(3) {
                    cpuConsumingJob(50)
                    println("job1 repeat $it")
                    yield() // with yield，协程运行到此处会释放线程，让别的协程也有执行的机会
                }
            }
        }
    }



}
