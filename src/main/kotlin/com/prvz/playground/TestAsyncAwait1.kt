package com.prvz.playground

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) = runBlocking {
    val time = measureTimeMillis {
        withContext(Dispatchers.IO) {
            val first = async { firstNumber() }
            val second = async { secondNumber() }
            val third = async { thirdNumber() }

            //listOf(first, second, third).awaitAll()

            first.await() + second.await() + third.await()
        }

    }

    println(time) //prints 7 seconds
}

fun firstNumber(): Int {
    println("start 1")

    //withContext(Dispatchers.IO) {
    Thread.sleep(5000)
    //}
    //delay(7_000) // 3 seconds delay
    //throw RuntimeException("1")
    println("end 1")
    return 5
}

suspend fun secondNumber(): Int {
    println("start 2")
    //delay(5_000) // 5 seconds delay
    withContext(Dispatchers.IO) { Thread.sleep(3000) }
    println("end 2")
    return 8
}

suspend fun thirdNumber(): Int {
    println("start 3")
    //delay(3_000) // 7 seconds delay
    withContext(Dispatchers.IO) { Thread.sleep(1000) }
    println("end 3")
    return 10
}