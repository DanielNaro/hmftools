package com.hartwig.hmftools.gripss.link

import com.hartwig.hmftools.gripss.StructuralVariantContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Link(val link: String, val vcfId: String, val otherVcfId: String, val minDistance: Int, val maxDistance: Int) {

    companion object {
        operator fun invoke(variant: StructuralVariantContext): Link {
            return Link("PAIR", variant.vcfId, variant.mateId!!, variant.insertSequenceLength, variant.insertSequenceLength)
        }

        operator fun invoke(link: String, variants: Pair<StructuralVariantContext, StructuralVariantContext>): Link {
            val (minDistance, maxDistance) = distance(variants.first, variants.second)
            return Link(link, variants.first.vcfId, variants.second.vcfId, minDistance, maxDistance)
        }


        private fun distance(first: StructuralVariantContext, second: StructuralVariantContext): Pair<Int, Int> {
            val minDistance = abs(first.maxStart - second.minStart)
            val maxDistance = abs(first.minStart - second.maxStart)
            return Pair(min(minDistance, maxDistance), max(minDistance, maxDistance))
        }
    }

    override fun toString(): String {
        return "$vcfId<$link>$otherVcfId"
    }


}