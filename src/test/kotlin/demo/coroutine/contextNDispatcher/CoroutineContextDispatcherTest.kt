package demo.coroutine.contextNDispatcher

import demo.Logger.Companion.println
import kotlinx.coroutines.*
import kotlin.test.Test

internal class CoroutineContextDispatcherTest {

    /**
     * launch的默认参数是继承自父作用域的上下文，但也可以通过指定Dispatcher来切换线程
     */
    @ObsoleteCoroutinesApi
    @Test
    fun launchWithDispatcher() {
        runBlocking {
            // 运行在父协程的上下文中，即 runBlocking 主协程
            launch {
                println("main runBlocking      : I'm working in thread ${Thread.currentThread().name}")
            }
            // 不受限的——将工作在主线程中
            launch(Dispatchers.Unconfined) {
                println("Unconfined            : I'm working in thread ${Thread.currentThread().name}")
            }
            // 将会获取默认调度器
            launch(Dispatchers.Default) {
                println("Default               : I'm working in thread ${Thread.currentThread().name}")
            }
            // 将使它获得一个昂贵新的线程，最后一定要close掉
            launch(newSingleThreadContext("MyOwnThread")) {
                println("newSingleThreadContext: I'm working in thread ${Thread.currentThread().name}")
            }
        }
    }

    /**
     * 一般的协程都是受限的。受限的协程只在给定的线程上执行（继承父作用域的上下文）
     * 非受限（Uniconfined）的协程可以不按指定的线程运行？？
     */
    @Test
    fun callWithUnconfinedDispatcher() {
        runBlocking {
            launch(Dispatchers.Unconfined) {
                // 非受限的——将和主线程一起工作
                println("Unconfined      : I'm working in thread ${Thread.currentThread().name}")
                delay(500)
                println("Unconfined      : After delay in thread ${Thread.currentThread().name}")
            }
            launch {
                // 父协程的上下文，主 runBlocking 协程
                println("main runBlocking: I'm working in thread ${Thread.currentThread().name}")
                delay(1000)
                println("main runBlocking: After delay in thread ${Thread.currentThread().name}")
            }
        }
    }

    /**
     * withContext可以用来切换上下文，不同上下文可以对应不同线程的dispatcher，所以可以把withContext作为一种方便的切换线程的方式
     * withContext的作用域结束后线程会自动切换回来
     * kotlin标准库中的use可以消费一个closeable并在消费完成后自动关闭它，这里用来关闭启动的单线程Dispatcher
     */
    @ObsoleteCoroutinesApi
    @Test
    fun callInThread() {
        newSingleThreadContext("Ctx1").use { ctx1 ->
            newSingleThreadContext("Ctx2").use { ctx2 ->
                runBlocking(ctx1) {
                    println("Started in ctx1")
                    withContext(ctx2) {
                        println("Working in ctx2")
                    }
                    println("Back to ctx1")
                }
            }
        }
    }

    /**
     * 使用加号组合Context
     */
    @Test
    fun contextCompose() = runBlocking {
        launch(Dispatchers.Default + CoroutineName("test")) {
            println("I'm working in thread ${Thread.currentThread().name}")
        }
        return@runBlocking
    }
}
