package demo.coroutine.compose

import demo.Logger.Companion.println
import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis
import kotlin.test.Test

internal class CoroutineComposeTest {

    suspend fun doSomethingUsefulOne(): Int {
        delay(1000L) // 假设我们在这里做了一些有用的事
        return 13
    }

    suspend fun doSomethingUsefulTwo(): Int {
        delay(1000L) // 假设我们在这里也做了一些有用的事
        return 29
    }

    /**
     * 挂起函数默认会顺序执行
     * measureTimeMillis可以用来测量运行时间
     */
    @Test
    fun callInSequence() = runBlocking {
        val time = measureTimeMillis {
            val one = doSomethingUsefulOne()
            val two = doSomethingUsefulTwo()
            println("result is ${one + two}")
        }
        println("time used $time")
    }

    /**
     * 使用async以并发执行挂起函数，使用await等待结果
     */
    @Test
    fun callInParallel() = runBlocking {
        val time = measureTimeMillis {
            val one = async { doSomethingUsefulOne() }
            val two = async { doSomethingUsefulTwo() }
            println("The answer is ${one.await() + two.await()}")
        }
        println("Completed in $time ms")
    }

    /**
     * 惰性(LAZY)启动的async需要通过start()方法启动
     * 不调用start直接调用await会导致函数顺序执行，失去并发能力
     */
    @Test
    fun callLazyInParallel() = runBlocking {
        val time = measureTimeMillis {
            val one = async(start = CoroutineStart.LAZY) { doSomethingUsefulOne() }
            val two = async(start = CoroutineStart.LAZY) { doSomethingUsefulTwo() }
            // 执行一些计算
            one.start() // 启动第一个
            two.start() // 启动第二个
            println("The answer is ${one.await() + two.await()}")
        }
        println("Completed in $time ms")
    }

    // somethingUsefulOneAsync 函数的返回值类型是 Deferred<Int>
    fun somethingUsefulOneAsync() = GlobalScope.async {
        doSomethingUsefulOne()
    }

    // somethingUsefulTwoAsync 函数的返回值类型是 Deferred<Int>
    fun somethingUsefulTwoAsync() = GlobalScope.async {
        doSomethingUsefulTwo()
    }

    /**
     * 将方法封装进async里，直接返回Deffered，方便协程外直接调用。
     * 但这种调用方法无法正确处理async方法异常，不建议使用
     */
    @Test
    fun callWrappedAsync() {
        val time = measureTimeMillis {
            // 我们可以在协程外面启动异步执行
            val one = somethingUsefulOneAsync()
            val two = somethingUsefulTwoAsync()
            // 但是等待结果必须调用其它的挂起或者阻塞
            // 当我们等待结果的时候，这里我们使用 `runBlocking { …… }` 来阻塞主线程
            runBlocking {
                println("The answer is ${one.await() + two.await()}")
            }
        }
        println("Completed in $time ms")
    }

    @Test
    fun callWrappedAsyncWithinCoroutineScope() = runBlocking {
        val time = measureTimeMillis {
            println("The answer is ${concurrentSum()}")
        }
        println("Completed in $time ms")
    }

    /**
     * 在给定的coroutineScope中调用async，当某个async函数抛出异常时，同一作用域的所有函数都会被取消。
     * 参见下一个例子
     */
    private suspend fun concurrentSum(): Int = coroutineScope {
        val one = async { doSomethingUsefulOne() }
        val two = async { doSomethingUsefulTwo() }
        one.await() + two.await()
    }

    @Test
    fun callAsyncWithinScopeThenOneFail() = runBlocking<Unit> {
        try {
            failedConcurrentSum()
        } catch(e: ArithmeticException) {
            println("Computation failed with ArithmeticException")
        }
    }

    /**
     * 当coroutineScope这个作用域中的某个async失败时，作用域中的其它async也被取消，父协程也被取消
     */
    private suspend fun failedConcurrentSum(): Int = coroutineScope {
        val one = async<Int> {
            try {
                delay(Long.MAX_VALUE) // 模拟一个长时间的运算
                42
            } finally {
                println("First child was cancelled")
            }
        }
        val two = async<Int> {
            println("Second child throws an exception")
            throw ArithmeticException()
        }
        one.await() + two.await()
    }

}
