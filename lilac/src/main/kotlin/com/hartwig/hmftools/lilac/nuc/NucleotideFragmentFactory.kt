package com.hartwig.hmftools.lilac.nuc

import com.hartwig.hmftools.common.codon.Codons
import com.hartwig.hmftools.common.genome.bed.NamedBed
import com.hartwig.hmftools.common.utils.SuffixTree
import com.hartwig.hmftools.lilac.read.AminoAcidIndices
import com.hartwig.hmftools.lilac.sam.SAMCodingRecord
import com.hartwig.hmftools.lilac.seq.HlaSequenceLoci
import htsjdk.samtools.SAMRecord


class NucleotideFragmentFactory(private val minBaseQuality: Int, inserts: List<HlaSequenceLoci>, deletes: List<HlaSequenceLoci>) {

    companion object {
        fun createNucleotidesFromAminoAcid(aminoAcid: String): List<String> {
            if (aminoAcid == ".") {
                return listOf(".", ".", ".")
            }
            val codons = Codons.codons(aminoAcid);
            return listOf(codons[0].toString(), codons[1].toString(), codons.substring(2))
        }
    }

    private val insertSuffixTrees = inserts.map { Pair(it, SuffixTree(it.sequence())) }.toMap()
    private val deleteSuffixTrees = deletes.map { Pair(it, SuffixTree(it.sequence())) }.toMap()

    fun createFragment(record: SAMRecord, reverseStrand: Boolean, codingRegionLoci: Int, codingRegion: NamedBed): NucleotideFragment? {
        val samCoding = SAMCodingRecord.create(codingRegion, record)
        return createFragment(samCoding, reverseStrand, codingRegionLoci, codingRegion)
    }


    fun createFragment(samCoding: SAMCodingRecord, reverseStrand: Boolean, codingRegionLoci: Int, codingRegion: NamedBed): NucleotideFragment? {
        val samCodingLength = samCoding.positionEnd - samCoding.positionStart + 1
        val samCodingStartLoci = if (reverseStrand) {
            codingRegionLoci + codingRegion.end().toInt() - samCoding.positionEnd
        } else {
            codingRegionLoci + samCoding.positionStart - codingRegion.start().toInt()
        }
        val samCodingEndLoci = samCodingStartLoci + samCodingLength - 1

        val codingRegionRead = samCoding.codingRegionRead(reverseStrand)
        val codingRegionQuality = samCoding.codingRegionQuality(reverseStrand)


        if (samCoding.containsIndel() || samCoding.containsSoftClip()) {
            val aminoAcidIndices = AminoAcidIndices.indices(samCodingStartLoci, samCodingEndLoci)
            val nucleotideStartLoci = aminoAcidIndices.first * 3

            val sequence = codingRegionRead.joinToString("")
            val aminoAcids = Codons.aminoAcids(sequence.substring(nucleotideStartLoci - samCodingStartLoci))
            if (aminoAcids.isNotEmpty()) {
                val matchRangeAllowed = (aminoAcidIndices.first - samCoding.softClippedStart / 3 - samCoding.maxIndelSize())..(aminoAcidIndices.first + samCoding.maxIndelSize())
                val matchingInserts = insertSuffixTrees
                        .map { Pair(it.key, it.value.indices(aminoAcids)) }
                        .map { Pair(it.first, it.second.filter { i -> matchRangeAllowed.contains(i) }) }
                        .filter { it.second.isNotEmpty() }
                if (matchingInserts.isNotEmpty()) {
                    val best = matchingInserts[0]
                    val result = createNucleotideSequence(samCoding.id, codingRegion, best.second[0], aminoAcids, best.first)
                    if (result.containsIndel()) {
                        return result
                    }
                }

                val matchingDeletes = deleteSuffixTrees
                        .map { Pair(it.key, it.value.indices(aminoAcids)) }
                        .map { Pair(it.first, it.second.filter { i -> matchRangeAllowed.contains(i) }) }
                        .filter { it.second.isNotEmpty() }
                if (matchingDeletes.isNotEmpty()) {
                    val best = matchingDeletes[0]
                    val result = createNucleotideSequence(samCoding.id, codingRegion, best.second[0], aminoAcids, best.first)
                    if (result.containsIndel()) {
                        return result
                    }
                }
            }

            if (samCoding.containsIndel()) {
                return null
            }
        }

        // NORMAL CASE
        val loci = (samCodingStartLoci..samCodingEndLoci).toList()
        val nucleotides = codingRegionRead.map { it.toString() }
        val qualities = codingRegionQuality.toList()

        return NucleotideFragment(samCoding.id, setOf(codingRegion.name()), loci, qualities, nucleotides)
    }

    private fun createNucleotideSequence(id: String, codingRegion: NamedBed, startLoci: Int, bamSequence: String, hlaSequence: HlaSequenceLoci): NucleotideFragment {
        val endLoci = endLoci(startLoci, bamSequence, hlaSequence)
        val aminoAcidLoci = (startLoci..endLoci).toList()
        val nucleotideLoci = aminoAcidLoci.flatMap { listOf(3 * it, 3 * it + 1, 3 * it + 2) }
        val nucleotides = aminoAcidLoci.map { hlaSequence.sequence(it) }.flatMap { createNucleotidesFromAminoAcid(it) }
        val qualities = nucleotideLoci.map { minBaseQuality }

        val genes = setOf(codingRegion.name())

        return NucleotideFragment(id, genes, nucleotideLoci, qualities, nucleotides)
    }

    private fun endLoci(startLoci: Int, bamSequence: String, hlaSequence: HlaSequenceLoci): Int {
        val builder = StringBuilder()
        for (loci in startLoci until hlaSequence.length) {
            builder.append(hlaSequence.sequences[loci])
            if (!bamSequence.startsWith(builder.toString())) {
                return loci - 1
            }
        }

        return hlaSequence.length - 1
    }

}