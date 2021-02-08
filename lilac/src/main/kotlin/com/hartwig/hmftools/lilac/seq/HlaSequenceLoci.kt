package com.hartwig.hmftools.lilac.seq

import com.hartwig.hmftools.lilac.hla.HlaAllele

data class HlaSequenceLoci(val allele: HlaAllele, val sequences: List<String>) {

    fun containsInserts(): Boolean {
        return sequences.any { it.length > 1 }
    }

    fun containsDeletes(): Boolean {
        return sequences.any { it == "." }
    }

    fun containsIndels(): Boolean {
        return sequences.any { it == "." || it.length > 1 }
    }


    fun sequence(locus: Int): String {
        return sequences[locus]
    }

    fun sequence(): String {
        return sequences.joinToString(separator = "").replace(".", "")
    }

    fun sequence(startLocus: Int, endLocus: Int): String {
        return sequences
                .filterIndexed { index, _ -> index in startLocus..endLocus }
                .joinToString("")
                .replace(".", "")
    }


    companion object {

        fun create(sequences: List<HlaSequence>): List<HlaSequenceLoci> {
            val reference = sequences[0].rawSequence
            return sequences.map { create(it.allele, it.rawSequence, reference) }
        }

        fun create(allele: HlaAllele, sequence: String, reference: String): HlaSequenceLoci {
            val sequences = mutableListOf<String>()

            fun isBaseIgnored(i: Int) = (sequence[i] == '.' && reference[i] == '.') || (sequence[i] == '|' && reference[i] == '|')
            fun isBaseInserted(i: Int) = sequence[i] != '.' && reference[i] == '.'
            var insLength = 0

            for (i in sequence.indices) {
                val isInsert = isBaseInserted(i)
                val isIgnored = isBaseIgnored(i)

                if (insLength > 0 && !isInsert) {
                    val insert = sequence.substring(i - insLength, i)
                    sequences[sequences.size - 1] = sequences[sequences.size - 1] + insert
                    insLength = 0
                }

                if (isInsert) {
                    insLength++
                } else if (!isIgnored) {
                    val locusSequence = sequence[i].toString()
                    if (locusSequence == "-") {
                        sequences.add(reference[i].toString())
                    } else {
                        sequences.add(locusSequence)
                    }
                }
            }
            return HlaSequenceLoci(allele, sequences)
        }
    }

}