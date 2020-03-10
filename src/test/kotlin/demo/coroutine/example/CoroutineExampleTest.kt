package demo.coroutine.example

import demo.Logger.Companion.println
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.merge
import org.junit.Test
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext

internal class CoroutineExampleTest {

    object RestClient {
        fun postRequest(url: String): String {
            println("post request $url")
            Thread.sleep(500)
            return "response from $url"
        }

        fun getRequest(token: Token, url: String): String {
            println("post request $url")
            Thread.sleep(500)
            return "response from $url with $token"
        }
    }

    class MixedMethodRepo() {
        fun authorize(): Mono<Token> {
            return Mono.create<Token> {
                it.success(RestClient.postRequest("/authorize").let { Token(it) })
            }.subscribeOn(Schedulers.elastic())
        }

        fun getUserInfo(token: Token, callback: (UserInfo) -> Unit) {
            thread {
                val userInfo = RestClient.getRequest(token, "/userInfo").let { UserInfo(it) }
                callback(userInfo)
            }
        }

        fun getMoments(token: Token): Future<Moments> {
            return Executors.newSingleThreadExecutor().submit(Callable {
                RestClient.getRequest(token, "/moment").let { Moments(it) }
            })
        }
    }

    private val mixedRepo = MixedMethodRepo()

    @Test
    fun runConcurrentTasksWithMixedRepo() {
        println("start")

        asyncLoadNShow1()

        repeat(11) {
            println("update UI")
            Thread.sleep(100)
        }

        println("finish")
    }

    private fun asyncLoadNShow1() {
        mixedRepo.authorize().doOnNext {
            println("token: $it")
        }.doOnNext { token ->
            mixedRepo.getUserInfo(token) {
                println("userInfo: $it how to update on UI?")
            }
            val moments = mixedRepo.getMoments(token).get()
            println("moments: $moments how to update on UI?")
        }.subscribe()
    }

    class CoroutineRepo() {
        suspend fun authorize() = withContext(Dispatchers.IO) {
            RestClient.postRequest("/authorize").let { Token(it) }
        }

        suspend fun getUserInfo(token: Token) = withContext(Dispatchers.IO) {
            RestClient.getRequest(token, "/userInfo").let { UserInfo(it) }
        }

        suspend fun getMoments(token: Token) = withContext(Dispatchers.IO) {
            RestClient.getRequest(token, "/moment").let { Moments(it) }
        }
    }

    data class Token(val value: String)

    data class UserInfo(val value: String)

    data class Moments(val value: String)

    private val coroutineRepo: CoroutineRepo = CoroutineRepo()
    private val mockMainDispatcher = newSingleThreadContext("main")

    @Test
    fun runConcurrentTasksWithCoroutine() {

        println("start")

        asyncLoadNShow2()

        repeat(11) {
            println("draw UI")
            Thread.sleep(100)
        }

        println("finish")
    }

    private fun asyncLoadNShow2() = GlobalScope.launch(mockMainDispatcher) {
        val token = coroutineRepo.authorize()
        println("token: $token")

        val userInfo = async { coroutineRepo.getUserInfo(token) }
        val moments = async { coroutineRepo.getMoments(token) }
        println("update ui for userInfo: ${userInfo.await()}")
        println("update ui for moments: ${moments.await()}")
    }

}
