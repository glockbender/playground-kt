package com.prvz.playground

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class TestData(
    val `Test Data`: String
)

fun main() {

    val om = jacksonObjectMapper()

    println(om.writeValueAsString(TestData("test")))

}