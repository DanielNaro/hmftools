package com.hartwig.hmftools.lilac.read

import com.hartwig.hmftools.lilac.amino.AminoAcidFragment
import com.hartwig.hmftools.lilac.hla.HlaAllele
import com.hartwig.hmftools.lilac.seq.HlaSequenceLoci
import com.hartwig.hmftools.lilac.seq.HlaSequenceMatch


class FragmentAlleles(val fragment: AminoAcidFragment, val full: Collection<HlaAllele>, val partial: Collection<HlaAllele>, val wild: Collection<HlaAllele>) {

    fun contains(allele: HlaAllele): Boolean {
        return full.contains(allele) || partial.contains(allele) || wild.contains(allele)
    }

    companion object {

        private fun FragmentAlleles.filter(alleles: Collection<HlaAllele>): FragmentAlleles {
            return FragmentAlleles(this.fragment, this.full.filter { it in alleles }, this.partial.filter { it in alleles }, this.wild.filter { it in alleles })
        }

        fun List<FragmentAlleles>.filter(alleles: Collection<HlaAllele>): List<FragmentAlleles> {
            return this
                    .map { it.filter(alleles) }
                    .filter { it.full.isNotEmpty() || it.partial.isNotEmpty()}
        }

        fun create(aminoAcidFragments: List<AminoAcidFragment>, hetLoci: Collection<Int>, sequences: Collection<HlaSequenceLoci>, nucleotideLoci: Collection<Int>, nucleotideSequences: Collection<HlaSequenceLoci>): List<FragmentAlleles> {
            return aminoAcidFragments
                    .map { create(it, hetLoci, sequences, nucleotideLoci, nucleotideSequences) }
                    .filter { it.full.isNotEmpty() || it.partial.isNotEmpty() }
        }

        private fun create(aminoAcidFragment: AminoAcidFragment,
                aminoAcidLoci: Collection<Int>, aminoAcidSequences: Collection<HlaSequenceLoci>,
                nucleotideLoci: Collection<Int>, nucleotideSequences: Collection<HlaSequenceLoci>): FragmentAlleles {

//            if (aminoAcidFragment.id == "A00624:78:H2MLGDSXY:4:2152:5837:27132") {
//                println("AR")
//            }
//
//            if (aminoAcidFragment.id == "A00624:78:H2MLGDSXY:4:1145:1967:11976") {
//                println("AG")
//            }

            val fragmentNucleotideLoci = (aminoAcidFragment.nucleotideLoci() intersect nucleotideLoci).sorted().toIntArray()
            val fragmentNucleotides = aminoAcidFragment.nucleotides(*fragmentNucleotideLoci)
            val matchingNucleotideSequences = nucleotideSequences
                    .map { Pair(it.allele, it.match(fragmentNucleotides, *fragmentNucleotideLoci)) }
                    .filter { it.second != HlaSequenceMatch.NONE }
                    .filter { aminoAcidFragment.genes.contains("HLA-${it.first.gene}") }
                    .map { Pair(it.first.asFourDigit(), it.second) }
                    .distinct()

            val fullNucleotideMatch = matchingNucleotideSequences.filter { it.second == HlaSequenceMatch.FULL }.map { it.first }.toSet()
            val partialNucleotideMatch = matchingNucleotideSequences
                    .filter { it.second == HlaSequenceMatch.PARTIAL || it.second == HlaSequenceMatch.WILD }
                    .map { it.first }
                    .toSet() subtract fullNucleotideMatch

            val fragmentAminoAcidLoci = (aminoAcidFragment.aminoAcidIndices() intersect aminoAcidLoci).sorted().toIntArray()
            val fragmentAminoAcids = aminoAcidFragment.aminoAcids(*fragmentAminoAcidLoci)
            val matchingAminoAcidSequences = aminoAcidSequences
                    .map { Pair(it.allele, it.match(fragmentAminoAcids, *fragmentAminoAcidLoci)) }
                    .filter { it.second != HlaSequenceMatch.NONE }
                    .filter { aminoAcidFragment.genes.contains("HLA-${it.first.gene}") }

            val fullAminoAcidMatch = matchingAminoAcidSequences.filter { it.second == HlaSequenceMatch.FULL }.map { it.first }.toSet()
            val partialAminoAcidMatch = matchingAminoAcidSequences.filter { it.second == HlaSequenceMatch.PARTIAL }.map { it.first }.toSet()
            val wildAminoAcidMatch = matchingAminoAcidSequences.filter { it.second == HlaSequenceMatch.WILD }.map { it.first }.toSet()

//            if (fullAminoAcidMatch.size == 1 && partialAminoAcidMatch.isEmpty() && wildAminoAcidMatch.isEmpty() && fullAminoAcidMatch.first() == HlaAllele("C*07:57")) {
//                println(aminoAcidFragment.id)
//                println(fragmentAminoAcidLoci.joinToString(","))
//                println(fragmentAminoAcids.joinToString(","))
//            }


            if (fullNucleotideMatch.isEmpty() && partialNucleotideMatch.isEmpty()) {
                return FragmentAlleles(aminoAcidFragment, fullAminoAcidMatch, partialAminoAcidMatch, wildAminoAcidMatch)
            }

//            val consistentFull = fullAminoAcidMatch.filter { it.asFourDigit() in fullNucleotideMatch }
//            val remainingFull = fullAminoAcidMatch.filter { it.asFourDigit() !in fullNucleotideMatch }
//            return FragmentAlleles(aminoAcidFragment, consistentFull, remainingFull union partialAminoAcidMatch, wildAminoAcidMatch)


            val consistentFull = fullAminoAcidMatch.filter { it.asFourDigit() in fullNucleotideMatch }
            val downgradedToPartial = fullAminoAcidMatch.filter { it.asFourDigit() in partialNucleotideMatch }
            val otherPartial = partialAminoAcidMatch.filter { it.asFourDigit() in partialNucleotideMatch }

            return FragmentAlleles(aminoAcidFragment, consistentFull, downgradedToPartial union otherPartial, wildAminoAcidMatch)


        }
    }


}