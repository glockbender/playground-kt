package com.prvz.playground

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import kotlin.random.Random

fun main() {

    val expectedList = mutableListOf<Byte>()

    val flow = flow<ByteBuffer> {

        // random bytearrays with size from 1Kb to 16Kb

        var totalSize = 0

        for (i in 0..1000) {
            val bb = ByteBuffer.wrap(Random.nextBytes(Random.nextInt(1024, 1024 * 16)))
            emit(bb)
            totalSize += bb.capacity()
            expectedList.addAll(bb.array().toTypedArray())
        }

        println("EXPECTED TOTAL SIZE: $totalSize")

    }

    val actualList = mutableListOf<Byte>()

    runBlocking {
        flow.resizeParts(
            minPartSize = 2048 * 6,
            maxPartSize = 2048 * 8
        ).withIndex()
            .let { flow ->
                var totalSize = 0
                flow.collect {
                    println("IDX: ${it.index}. SIZE: ${it.value.capacity()}")
                    totalSize += it.value.capacity()
                    actualList.addAll(it.value.array().toTypedArray())
                }
                println("TOTAL SIZE: $totalSize")
            }

    }

    assert(expectedList == actualList)
}

fun ByteBuffer.chunked(separatePartSize: Int): Pair<List<ByteBuffer>, ByteBuffer?> {
    val size = this.capacity()
    if (size < separatePartSize) {
        return listOf<ByteBuffer>() to this
    }

    val parts = size / separatePartSize
    val remains = size % separatePartSize

    var startIdx = 0

    val fullPartsList = ArrayList<ByteBuffer>(parts)

    for (i in 1..parts) {
        val partBytes = this.slice(startIdx, separatePartSize)
        fullPartsList.add(partBytes)
        startIdx += separatePartSize
    }

    val lastPart = if (remains == 0) {
        null
    } else {
        this.slice(startIdx, remains)
    }

    return fullPartsList to lastPart
}

suspend fun Flow<ByteBuffer>.resizeParts(
    minPartSize: Int,
    maxPartSize: Int
): Flow<ByteBuffer> = flow {

    val firstFlow = this@resizeParts
    var accumulatedBytes: ByteBuffer? = null

    suspend fun whenLessThanMinPartSize(bb: ByteBuffer) {
        if (accumulatedBytes == null) {
            accumulatedBytes = bb
        } else {
            val newAccumulatedSize = accumulatedBytes!!.capacity() + bb.capacity()
            if (newAccumulatedSize <= maxPartSize) {
                accumulatedBytes = accumulatedBytes!!.put(bb)
            } else {
                if (accumulatedBytes!!.capacity() >= minPartSize) {
                    emit(accumulatedBytes!!)
                    accumulatedBytes = bb
                } else {
                    val receivedBytesSizeLimit = maxPartSize - accumulatedBytes!!.capacity()
                    val bytesToEmit = accumulatedBytes!!
                        .put(bb.slice(0, receivedBytesSizeLimit))
                    accumulatedBytes = bb.slice(receivedBytesSizeLimit, bb.capacity() - receivedBytesSizeLimit)
                    emit(bytesToEmit)
                }
            }
        }
    }

    suspend fun whenMoreThanMaxPartSize(bb: ByteBuffer) {
        val remain: ByteBuffer
        if (accumulatedBytes != null) {
            // bytes need to be added to accumulated bytes array for growing up to maxPartSize
            val bytesToAddToAccBytes = maxPartSize - accumulatedBytes!!.capacity()
            accumulatedBytes = accumulatedBytes!!.put(bb.slice(0, bytesToAddToAccBytes))
            emit(accumulatedBytes!!)
            accumulatedBytes = null
            remain = bb.slice(bytesToAddToAccBytes, bb.capacity() - bytesToAddToAccBytes)
        } else {
            remain = bb
        }
        val chunked = remain.chunked(maxPartSize)
        if (chunked.first.isNotEmpty()) {
            chunked.first.forEach { part -> emit(part) }
        }
        accumulatedBytes = chunked.second
    }

    firstFlow.collect {
        val receivedSize = it.capacity()
        when {
            receivedSize < minPartSize -> whenLessThanMinPartSize(it)
            receivedSize > maxPartSize -> whenMoreThanMaxPartSize(it)
            else -> {
                emit(it)
            }
        }
    }

    accumulatedBytes?.let { emit(it) }
}