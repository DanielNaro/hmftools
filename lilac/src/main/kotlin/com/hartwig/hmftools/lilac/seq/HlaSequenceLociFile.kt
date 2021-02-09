package com.hartwig.hmftools.lilac.seq

import java.io.File
import kotlin.math.max

object HlaSequenceLociFile {

    fun write(file: String, sequences: List<HlaSequenceLoci>) {
        require(sequences.isNotEmpty())

        val outputFile = File(file)
        outputFile.writeText("")

        val maxLengths = sequences.map { it.lengths() }.reduce { left, right -> maxLengths(left, right) }
        val templateSequence = sequences[0].padInserts(maxLengths)

        outputFile.appendText("${sequences[0].allele}".padEnd(20, ' ') + "\t" + templateSequence + "\n")
        for (i in 1 until sequences.size) {
            val victimSequence = diff(sequences[i].padInserts(maxLengths), templateSequence)
            outputFile.appendText("${sequences[i].allele}".padEnd(20, ' ') + "\t" + victimSequence + "\n")
        }

    }

    private fun diff(victim: String, reference: String): String {
        return victim
                .mapIndexed { index, _ -> diff(victim[index], if (index < reference.length) reference[index] else '!') }
                .joinToString("")
    }

    private fun diff(victim: Char, reference: Char): Char {
        return when (victim) {
            '|' -> '|'
            '*' -> '*'
            '.' -> '.'
            reference -> '-'
            else -> victim
        }
    }

    private fun HlaSequenceLoci.padInserts(lengths: List<Int>): String {
        return this.sequences
                .mapIndexed { index, value -> value.padEnd(lengths[index], '.') }
                .joinToString("")
                .trimEnd { x -> x == '.' }
    }

    private fun HlaSequenceLoci.lengths(): List<Int> {
        return this.sequences.map { it.length }
    }

    private fun maxLengths(left: List<Int>, right: List<Int>): List<Int> {
        val result = mutableListOf<Int>()
        for (i in 0 until max(left.size, right.size)) {
            val leftValue = if (i < left.size) left[i] else 0
            val rightValue = if (i < right.size) right[i] else 0
            result.add(max(leftValue, rightValue))
        }
        return result
    }

}