package com.prvz.playground

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import kotlin.random.Random

class A {
    fun test() {
        val bb = ByteBuffer.wrap(Random.nextBytes(105))
        val ch = bb.chunked(10)
        val bb1: ByteBuffer = ByteBuffer.allocate(105).apply {
            ch.first.forEach { inn -> put(inn) }
            put(ch.second!!)
            rewind()
        }

        if (bb != bb1) {
            throw IllegalStateException()
        }

        println()
    }
}

fun main() {

    A().test()

    val expectedList = mutableListOf<Byte>()

    val flow = flow<ByteBuffer> {

        // random bytearrays with size from 1Kb to 16Kb

        var totalSize = 0

        for (i in 0..10000) {
            val bb = ByteBuffer.wrap(Random.nextBytes(Random.nextInt(1024 / 2, 1024 * 16)))
            expectedList.addAll(bb.array().toTypedArray())
            bb.rewind()
            emit(bb)
            totalSize += bb.capacity()
        }

        println("EXPECTED TOTAL SIZE: $totalSize")

    }

    val actualList = mutableListOf<Byte>()

    runBlocking {
        flow.resizeParts(
            minPartSize = 2048,
            maxPartSize = 2048 * 2
        ).withIndex()
            .let { flow ->
                var totalSize = 0
                flow.collect {
                    println("IDX: ${it.index}. SIZE: ${it.value.capacity()}")
                    val checkList = expectedList.subList(totalSize, totalSize + it.value.capacity())
                    totalSize += it.value.capacity()
                    if (it.value.position() != 0) {
                        throw IllegalStateException()
                    }
                    if (it.value.array().size != it.value.capacity()) {
                        println("@@@ALARM@@@")
                    }
                    val addList = it.value.array().asList()
                    if (checkList != addList) {
                        println("±±±ALARM±±±")
                    }
                    actualList.addAll(addList)
                }
                println("TOTAL SIZE: $totalSize")
            }

    }

    println("EQUALS: ${expectedList == actualList}")
}

fun ByteBuffer.chunked(chunkSize: Int): Pair<List<ByteBuffer>, ByteBuffer?> {
    val size = this.capacity()
    if (size < chunkSize) {
        return listOf<ByteBuffer>() to this
    }

    val parts = size / chunkSize
    val remains = size % chunkSize

    var startIdx = 0

    val fullPartsList = ArrayList<ByteBuffer>(parts)

    for (i in 1..parts) {
        val partBytes = this.copyNoShare(startIdx, chunkSize)
        fullPartsList.add(partBytes)
        startIdx += chunkSize
    }

    val lastPart = if (remains == 0) {
        null
    } else {
        this.copyNoShare(startIdx, remains)
    }

    return fullPartsList to lastPart
}

fun ByteBuffer.concat(bb: ByteBuffer): ByteBuffer = ByteBuffer
    .allocate(this.capacity() + bb.capacity())
    .put(this)
    .put(bb)
    .rewind()

fun ByteBuffer.copyNoShare(startIdx: Int, newSize: Int): ByteBuffer {
    val copy = ByteBuffer.allocate(newSize)
    val capacityWithIdx = startIdx + newSize
    if (this.capacity() >= capacityWithIdx) {
        copy.put(this.slice(startIdx, newSize))
    } else {
        copy.put(this)
    }
    return copy.rewind()
}

suspend fun Flow<ByteBuffer>.resizeParts(
    minPartSize: Int,
    maxPartSize: Int
): Flow<ByteBuffer> = flow {

    val firstFlow = this@resizeParts
    var accumulatedBytes: ByteBuffer? = null

    suspend fun accStage(received: ByteBuffer): ByteBuffer? {
        val partToAddInAccSize = maxPartSize - accumulatedBytes!!.capacity()
        return if (received.capacity() <= partToAddInAccSize) {
            accumulatedBytes = accumulatedBytes!!.concat(received)
            null
        } else {
            val receivedBytesToAddInAcc = received.slice(0, partToAddInAccSize)
            emit(accumulatedBytes!!.concat(receivedBytesToAddInAcc))
            accumulatedBytes = null
            val remains = received.copyNoShare(partToAddInAccSize, received.capacity() - partToAddInAccSize)
            remains
        }
    }

    suspend fun nextStage(received: ByteBuffer) {
        when {
            received.capacity() < minPartSize -> {
                accumulatedBytes = received
            }
            received.capacity() > maxPartSize -> {
                val chunked = received.chunked(maxPartSize)
                if (chunked.first.isNotEmpty()) {
                    chunked.first.forEach { part -> emit(part) }
                }
                accumulatedBytes = chunked.second
            }
            else -> {
                emit(received)
            }
        }
    }

    firstFlow
        .collect { received ->
            if (accumulatedBytes != null) {
                accStage(received)?.let { nextStage(it) }
            } else {
                nextStage(received)
            }
        }

    accumulatedBytes?.let { emit(it) }
}
