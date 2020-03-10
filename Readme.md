## 协程学习Demo

### 溯源
```
Q: 如何利用多CPU的能力？
A: 并行(parallelism)

Q: 如何解决多个程序执行时互相阻塞，或运算和互相UI阻塞的问题？ 
A: 并发(concurrency)执行 (多cpu or 单cpu时间片) 

Q: 如何解决并发执行上下文切换问题？ 
A: 使用进程（私有虚拟内存空间）

Q: 如何解决切换进程开销太大的问题？ 
A: 使用线程（共享宿主进程的虚拟内存空间，避免切换）

Q: 如何保证中断处理、始终管理、进程调度、驱动、内存、文件系统等底层功能的管理的运行，并减少开销？ 
A: 使用内核(kernel)（单进程 or 多进程）（常驻内存，受到保护）

Q: JVM进行线程调度的策略是怎么样的？
A: 具体策略基于操作系统，所以在不同系统上有所不同。总体来说是基于优先级，抢占的线程调度算法。

Q: 很多用户程序可以自己实现线程切换管理，不需要系统来管，怎么区分出来？
A: 区分用户态线程和内核态线程。 -> 用户态线程即协程 ❎

```
### 概念
#### 协程(Coroutine)
协程可以简化异步编程，可以顺序地表达程序，协程也提供了一种避免阻塞线程并用更廉价、更可控的操作替代线程阻塞的方法 – 协程挂起。

#### 从不同层面理解
- 与线程相比：轻量级的线程（用户态线程，切换时不用陷入内核）
- 与函数相比：能保存上下文的函数，调用时能接着上次运行的上下文继续运行
- 与回调相比：用编译时变换避免异步回调，用状态机保存
- 从语言特性：yield async await

#### 要解决的问题
| PROBLEM |	SOLUTION |
| --- | --- |
| Simplify Callbacks | Coroutine |
| Get results from a potentially infinite list | 	BuildSequence |
| Get a promise for a future result	| Async/Await |
| Work with streams of data	| Channels and Pipelines |
| Act on multiple asynchronous inputs |	Select |

#### 挂起(suspend)
简单的理解为协程或代码块被挂起。挂起函数挂起协程时，不会阻塞协程所在的线程。
挂起是异步代码顺序执行的核心。如下面的例子：
```kotlin
runBlocking {
    val one = async { generateOne() }.await() // 语句1
    val two = async { generateTwoFromOne(one) } // 语句2
    val three = async { generateThreeFromOne(one) } // 语句3
    plusResult(two.await(), three.await()) // 语句4
}
```
例如上面例子中，`generateOne()`、`generateTwoFromOne()`和`generateThreeFromOne()`是三个挂起函数，包含了需要异步执行的代码。此代码的运行逻辑如下：
1. `runBlocking{}`创建了一个协程，我们暂且称为`容器协程`。其中的四条语句都由`容器协程`运行。
1. 语句1中的`async{}`创建了一个子协程，我们称为`协程1`。依照默认的运行配置，`协程1`会立即开始运行。随后的`await()`方法挂起了`容器协程`，
`语句2`的执行也随之被挂起，直到`协程1`执行完毕后`容器协程`才会恢复运行，继续执行`语句2`。
1. 与上一步相似，`语句2`中的`async{}`创建了`协程2`，并立即开始运行。此语句最后没有调用`await()`，所以`容器协程`不会被挂起，`语句3`被立即执行了。
1. 同上一步，`语句3`创建并运行了`协程3`，随后立即开始运行`语句4`。由于没有挂起，这个时候`容器协程`，`协程2`和`协程3`并行执行。
1. 紧接着在`语句4`中，`协程2`和`协程3`依次调用了`await()`，以挂起`容器协程`，并等待返回值（注：如果在调用await()时协程已经执行完成并获得了结果，外部协程就不需要被挂起，而是直接获得结果）。
随后`容器协程`调用非挂起函数`plusResult()`来做最后运算。
- 注：我们可以看出`语句1`中这种async紧接着await的调用跟直接调用generateOne()一样，都不会触发并发执行，只是一个挂起了原协程，一个是在原协程阻塞调用。

#### `CoroutineScope`
可以理解为一个完整的协程，包含了 CoroutineContext。核心是运行在协程中的代码块。
 
#### `GlobalScope`
一个特殊的`CoroutineScope`，调用`GlobalScope.launch{}`启动的协程会在应用整个生命周期运行。

#### `CoroutineContext`
协程上下文，是一些Element的集合，主要包括 Job 和 CoroutineDispatcher 元素，可以代表一个协程的场景。

#### `EmptyCoroutineContext`
表示一个空的协程上下文。

#### `Job`
代表协程生命周期。可以通过它启动、取消协程，或查询协程的生命状态。

#### `Deferred`
Job的子类，提供了带结果返回的Job。

#### `CoroutineDispatcher`
Dispatcher主要负责协程的调度。每个CoroutineContext中都包含有自己的Dispatcher。
`Dispatchers`提供了一组默认的调度器，它们包括：
- Default：
  默认调度器，使用一组共享的线程。
- IO：
  顾名思义，用作IO等阻塞操作。按需创建线程。
- Main：
  在不同的平台上主线程有不同，Main调度器对此进行了封装。
- Unconfined：
  以上的调度器只作用于协程的代码块，当协程执行完毕，调用者(父协程)会恢复到之前的本身所在线程上运行。
  Unconfined调度器则有所不同，当协程执行完毕后，调用者会继续在子协被程调度到的线程上运行。

#### `Coroutine builders`
- `runBlocking {}` 创建一个新的协程同时阻塞当前线程，直到协程结束。这个不应该在协程中使用，主要是为main函数和测试设计的。
- `CoroutineScope.launch {}` 是最常用的 Coroutine builders，不阻塞当前线程，在后台创建一个新协程，也可以指定协程调度器。
- `CoroutineScope.async {}` 可以实现与 launch builder 一样的效果，在后台创建一个新协程，唯一的区别是它有返回值，因为CoroutineScope.async {}返回的是 Deferred 类型。
- 注：launch和async启动的协程如果使用默认的Dispatcher即线程池，则都会并行执行。但如果指定了同一线程，则会在此线程的任务队列中排队执行。
- `withContext {}` 不会创建新的协程，在指定协程上运行代码块，并挂起该协程直至代码块运行完成。

### Ref
- https://www.kotlincn.net/docs/reference/coroutines/coroutines-guide.html
- https://johnnyshieh.me/posts/kotlin-coroutine-introduction/
