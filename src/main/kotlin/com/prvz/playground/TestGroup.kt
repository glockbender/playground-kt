package com.prvz.playground

import java.time.DayOfWeek

fun main() {
    group(mapOf())
}

fun group(fromBack: Map<DayOfWeek, IntRange>) {

    val fromBack: Map<DayOfWeek, IntRange> = mapOf(
        DayOfWeek.MONDAY to 10..20,
        DayOfWeek.TUESDAY to 10..20,
        DayOfWeek.WEDNESDAY to 10..19,
        DayOfWeek.THURSDAY to 11..19,
        DayOfWeek.FRIDAY to 11..19,
        DayOfWeek.SATURDAY to 11..19,
        DayOfWeek.SUNDAY to 11..19
    )

    fromBack.entries.groupBy({ it.value }, { it.key })


}