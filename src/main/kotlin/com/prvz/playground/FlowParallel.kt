package com.prvz.playground

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

fun main() {
    val flow = flow<ByteBuffer> {
        for (i in 1..10) {
            val emittedString = buildString { repeat(Int.MAX_VALUE / 1000000) { append(it) } }
            emit(ByteBuffer.wrap(emittedString.toByteArray()))
        }
    }

    runBlocking { FlowHandler().handle(flow) }
}

class FlowHandler {

    suspend fun handle(flow: Flow<ByteBuffer>) {

        val map = hashMapOf<Int, String>()

        flow.withIndex()
            .map {
                println(coroutineContext.toString())
                println("MAPPING start. Idx: ${it.index}")
                val result = it.index to copyAllBytesFrom(it.value)
                println("MAPPING end. Idx: ${it.index}")
                result
            }
            .buffer(2)
//            .map { it.first to futureAction(it.first, it.second).await() }
            .flatMapMerge {
                println(coroutineContext.toString())
                flow<Pair<Int, String>> { emit(it.first to futureAction(it.first, it.second).await()) }
            }
//            .map { it.first to CoroutineScope(Dispatchers.IO).async { futureAction(it.first, it.second).await() } }
            .collect { (index, value) ->
                map[index] = "value"
                println("Collecting. Idx: $index")
            }

        println(map)

    }

    fun futureAction(idx: Int, ba: ByteArray): CompletableFuture<String> =
        CompletableFuture.supplyAsync {
            println("FUTURE start. Idx: $idx")
            Thread.sleep(longNumberWithJitter())
            val baToString = String(ba)
            println("FUTURE end. Idx: $idx")
            baToString
        }

    fun copyAllBytesFrom(bb: ByteBuffer): ByteArray {
        Thread.sleep(longNumberWithJitter(base = 100, maxJitter = 50))
        return if (bb.hasArray()) {
            Arrays.copyOfRange(bb.array(), bb.arrayOffset(), bb.arrayOffset() + bb.limit())
        } else {
            val copy = bb.asReadOnlyBuffer()
            copy.rewind()
            val dst = ByteArray(copy.remaining())
            copy[dst]
            dst
        }
    }

    fun longNumberWithJitter(base: Long = 1000, maxJitter: Long = 500) =
        if (Random.nextBoolean()) {
            base + Random.nextLong(maxJitter)
        } else {
            base - Random.nextLong(maxJitter)
        }
}