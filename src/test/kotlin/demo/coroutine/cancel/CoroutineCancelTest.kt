package demo.coroutine.cancel

import demo.Logger.Companion.println
import kotlinx.coroutines.*
import kotlin.test.Test

internal class CoroutineCancelTest {

    /**
     * launch返回的job可以被cancel
     */
    @Test
    fun cancelJob() = runBlocking {
        val job = launch {
            repeat(1000) { i ->
                println("job: I'm sleeping $i ...")
                delay(500L)
            }
        }
        delay(1300L) // 延迟一段时间
        println("main: I'm tired of waiting!")
        job.cancel() // 取消该作业
        job.join() // 等待作业执行结束
//        job.cancelAndJoin() // 组合cancel&join
        println("main: Now I can quit.")
    }

    /**
     * job在cancel时需要在内部进行检查，并主动停止后续处理
     * 取消时抛出CancellationException
     */
    @Test
    fun unCancelableJob() = runBlocking {
        val startTime = System.currentTimeMillis()
        val job = launch(Dispatchers.Default) {
            var nextPrintTime = startTime
            var i = 0
            while (i < 5) { // 一个执行计算的循环，只是为了占用 CPU
                // 每秒打印消息两次
                if (System.currentTimeMillis() >= nextPrintTime) {
                    println("job: I'm sleeping ${i++} ...")
                    nextPrintTime += 500L
                }
            }
        }
        delay(1300L) // 等待一段时间
        println("main: I'm tired of waiting!")
        job.cancelAndJoin() // 取消一个作业并且等待它结束
        println("main: Now I can quit.")
    }

    /**
     * cancel的检查以及cancel后job终止运行的逻辑都需要job内部主动处理。
     * 首先需要检查scope.isActive的状态，然后可以选择是否主动抛出CancellationException
     * kotlinx.coroutines中的所有挂起函数默认会抛出CancellationException，说明上称其为'协作的'
     */
    @Test
    fun cancelableJob() = runBlocking {
        val startTime = System.currentTimeMillis()
        val job = launch(Dispatchers.Default) {
            var nextPrintTime = startTime
            var i = 0
            try {
                while (isActive) { // 可以被取消的计算循环，需要检查cancel状态以停止循环
                    // 每秒打印消息两次
                    if (System.currentTimeMillis() >= nextPrintTime) {
                        println("job: I'm sleeping ${i++} ...")
                        nextPrintTime += 500L
                    }
                }
                println("job: Running after canceled loop")
            } catch (exception: Exception) {
                println("job: catch exception ${exception.message}") // 不会打印，因为没有主动抛出异常
            } finally {
                println("job: I'm running finally") // 会打印
            }
            println("job: Running after finally")
            i = 0
            try {
                while (i < 5) { // 这段循环还能被执行？！！因为这些都是job内部的逻辑控制的，外部的cancel需要内部的'协作'
                    // 每秒打印消息两次
                    if (System.currentTimeMillis() >= nextPrintTime) {
                        println("job: I'm sleeping again after canceled ${i++} ...")
                        nextPrintTime += 500L
                    }
                }
            } catch (exception: Exception) { // 不会捕获这个异常，因为第二次cancel没有生效
                println("job: catch exception again ${exception.message}")
            }
            println("job: Running after second while")
        }
        delay(1300L) // 等待一段时间
        println("main: I'm tired of waiting!")
        job.cancelAndJoin() // 取消该作业并等待它结束
        delay(1300L) // 再登待一段时间
        job.cancelAndJoin() // 再次取消作业，不会生效
        println("main: Now I can quit.")
    }

    /**
     * repeat默认支持cancel操作，而且会主动抛出CancellationException
     */
    @Test
    fun handleInFinal() = runBlocking {
        val job = launch {
            try {
                repeat(1000) { i ->
                    println("job: I'm sleeping $i ...")
                    delay(500L)
                }
            } catch (exception: Exception) {
                println("job: catch exception ${exception.message}")
            } finally {
                println("job: I'm running finally")
            }
            println("job: Running after finally?")
        }
        delay(1300L) // 延迟一段时间
        println("main: I'm tired of waiting!")
        job.cancelAndJoin() // 取消该作业并且等待它结束
        println("main: Now I can quit.")
    }

    /**
     * 在finally中调用挂起函数会立即抛出CancellationException
     */
    @Test
    fun callSuspendFunctionInFinallyAndThrowCancellationException() = runBlocking {
        val job = launch {
            try {
                repeat(1000) { i ->
                    println("job: I'm sleeping $i ...")
                    delay(500L)
                }
            } catch (exception: Exception) {
                println("job: catch exception ${exception.message}")
            } finally {
                println("job: I'm running finally")
                try {
                    delay(200L)
                    println("job: Running after calling suspend function in finally") // not called
                } catch (e: Exception) {
                    println("job: catch exception in finally ${e.message}") // called
                }
            }
            println("job: Running after finally?")
        }
        delay(1300L) // 延迟一段时间
        println("main: I'm tired of waiting!")
        job.cancelAndJoin() // 取消该作业并且等待它结束
        println("main: Now I can quit.")
    }

    /**
     * 使用withContext(NonCancellable)来允许在finally中调用挂起函数
     * 原理是什么呢？Context的作用是什么？
     */
    @Test
    fun suspendCanceledCoroutine() = runBlocking {
        val job = launch {
            try {
                repeat(1000) { i ->
                    println("job: I'm sleeping $i ...")
                    delay(500L)
                }
            } finally {
                withContext(NonCancellable) {
                    println("job: I'm running finally")
                    delay(1000L)
                    println("job: And I've just delayed for 1 sec because I'm non-cancellable")
                }
            }
        }
        delay(1300L) // 延迟一段时间
        println("main: I'm tired of waiting!")
        job.cancelAndJoin() // 取消该作业并等待它结束
        println("main: Now I can quit.")
    }

    @Test
    fun cancelOnTimeout() = runBlocking {
        try {
            withTimeout(1500L) {
                repeat(1000) { i ->
                    println("$i")
                    delay(500L)
                }
            }
        } catch (e: TimeoutCancellationException) {
            println("Cancelled due to timeout")
        }
    }

    @Test
    fun replaceTimeoutWithNull() = runBlocking {
        val result = withTimeoutOrNull(1500L) {
            repeat(1000) { i ->
                println("$i")
                delay(500L)
            }
        }
        println("result is $result")
    }

    /**
     * 取消父协程同时也会取消子协程，并在子协程内抛出CancellationException
     */
    @Test
    fun cancelParentCoroutine() = runBlocking {
        val parentCoroutine = launch {
            println("parent job run")
            launch {
                try {
                    println("job 1 run")
                    delay(100)
                    println("job 1 finish")
                } catch (e: Throwable) {
                    println("job 1 throw exception $e")
                }
            }

            launch {
                try {
                    println("job 2 run")
                    delay(100)
                    println("job 2 finish")
                } catch (e: Throwable) {
                    println("job 2 throw exception $e")
                }
            }
        }
        delay(50)
        parentCoroutine.cancel()
    }
}
