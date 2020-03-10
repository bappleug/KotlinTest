package demo.coroutine.performace

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class CoroutinePerformanceTest {

    @Test
    fun runALargeNumberOfCoroutines() = runBlocking {
        repeat(100_000) {
            // 启动大量的协程
            launch {
                delay(1000L)
                print("$it.")
            }
        }
    }

    @Test
    fun runALargeNumberOfThreads() = runBlocking {
        repeat(100_000) {
            // 启动大量的线程
            thread {
                Thread.sleep(1000L)
                print("$it.")
            }
        }
        Thread.sleep(1000L)
    }

    @Test
    fun useExecutorInsteadOfThread() {
        val i = AtomicInteger()
        val executor = Executors.newCachedThreadPool()
        val runnable = {
            Thread.sleep(1000L)
            print("${i.incrementAndGet()}.")
        }
        repeat(100_000) {
            executor.execute(runnable)
        }
        Thread.sleep(1000)
    }

    @Test
    fun equivalenceOfDelayedCoroutine() {
        val i = AtomicInteger()
        val executor = Executors.newSingleThreadScheduledExecutor()
        val runnable = {
            print("${i.incrementAndGet()}.")
        }
        repeat(100_000) {
            executor.schedule(runnable, 1, TimeUnit.SECONDS)
        }
        Thread.sleep(2000)
    }
}
