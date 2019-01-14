package com.hartwig.hmftools.knowledgebaseimporter.cgi.readers

import com.hartwig.hmftools.knowledgebaseimporter.cgi.input.CgiActionableInput
import com.hartwig.hmftools.knowledgebaseimporter.knowledgebases.CodonMutations
import com.hartwig.hmftools.knowledgebaseimporter.knowledgebases.readers.SomaticEventReader
import com.hartwig.hmftools.knowledgebaseimporter.transvar.matchers.CodonMatcher
import kotlin.math.max

object CgiActionableCodonMutationsReader : SomaticEventReader<CgiActionableInput, CodonMutations> {
    private val ANY_CODON_PATTERN = "\\.[0-9]+\\.".toRegex()

    // CGI codon mutations have the form: 'V600.'
    private fun matches(event: CgiActionableInput): Boolean {
        return event.`Alteration type` == "MUT" && (CodonMatcher.matches(codon(event)) || ANY_CODON_PATTERN.matches(event.variant))
    }

    override fun read(event: CgiActionableInput): List<CodonMutations> {
        if (matches(event)) return listOf(CodonMutations(event.gene, event.transcript, codonNumber(event)))
        return emptyList()
    }

    private fun codon(event: CgiActionableInput): String {
        val lastCodonIndex = max(0, event.variant.length - 1)
        return if (lastCodonIndex > 0 && event.variant[lastCodonIndex] == '.') {
            event.variant.substring(0, lastCodonIndex)
        } else {
            event.variant
        }
    }

    private fun codonNumber(event: CgiActionableInput) = "([\\d]+)".toRegex().find(event.variant)!!.groupValues[1].toInt()
}
