package com.hartwig.hmftools.common.actionability.cnv;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.actionability.ActionabilitySource;
import com.hartwig.hmftools.common.actionability.EvidenceItem;
import com.hartwig.hmftools.common.actionability.EvidenceLevel;
import com.hartwig.hmftools.common.actionability.EvidenceScope;
import com.hartwig.hmftools.common.actionability.ImmutableEvidenceItem;
import com.hartwig.hmftools.common.actionability.cancertype.CancerTypeAnalyzer;
import com.hartwig.hmftools.common.purple.gene.GeneCopyNumber;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CopyNumberEvidenceAnalyzer {

    @NotNull
    private final List<ActionableCopyNumber> actionableCopyNumbers;

    CopyNumberEvidenceAnalyzer(@NotNull List<ActionableCopyNumber> actionableCopyNumbers) {
        this.actionableCopyNumbers = actionableCopyNumbers;
    }

    @NotNull
    public Set<String> actionableGenes() {
        Set<String> genes = Sets.newHashSet();
        for (ActionableCopyNumber cnvs : actionableCopyNumbers) {
            genes.add(cnvs.gene());
        }
        return genes;
    }

    @NotNull
    public List<EvidenceItem> evidenceForCopyNumber(@NotNull GeneCopyNumber geneCopyNumber, @Nullable String primaryTumorLocation,
            @NotNull CancerTypeAnalyzer cancerTypeAnalyzer) {
        List<EvidenceItem> evidenceItems = Lists.newArrayList();
        // KODU: Assume the gene copy number has already been determined to be a significant event (LOSS or GAIN)
        for (ActionableCopyNumber actionableCopyNumber : actionableCopyNumbers) {
            if (typeMatches(geneCopyNumber, actionableCopyNumber) && actionableCopyNumber.gene().equals(geneCopyNumber.gene())) {
                ImmutableEvidenceItem.Builder evidenceBuilder = fromActionableCopyNumber(actionableCopyNumber);
                evidenceBuilder.type("CNV");
                evidenceBuilder.gene(actionableCopyNumber.gene());
                evidenceBuilder.chromosome(Strings.EMPTY);
                evidenceBuilder.position(Strings.EMPTY);
                evidenceBuilder.ref(Strings.EMPTY);
                evidenceBuilder.alt(Strings.EMPTY);
                evidenceBuilder.cnvType(actionableCopyNumber.type().toString());
                evidenceBuilder.fusionFiveGene(Strings.EMPTY);
                evidenceBuilder.fusionThreeGene(Strings.EMPTY);
                evidenceBuilder.event(geneCopyNumber.gene() + " " + actionableCopyNumber.type().readableString());
                evidenceBuilder.isOnLabel(cancerTypeAnalyzer.isCancerTypeMatch(actionableCopyNumber.cancerType(),
                        primaryTumorLocation));

                evidenceItems.add(evidenceBuilder.build());
            }
        }
        return evidenceItems;
    }

    private static boolean typeMatches(@NotNull GeneCopyNumber geneCopyNumber, @NotNull ActionableCopyNumber actionableCopyNumber) {
        CopyNumberType geneType = geneCopyNumber.value() <= 1 ? CopyNumberType.DELETION : CopyNumberType.AMPLIFICATION;
        return geneType == actionableCopyNumber.type();
    }

    @NotNull
    private static ImmutableEvidenceItem.Builder fromActionableCopyNumber(@NotNull ActionableCopyNumber actionableCopyNumber) {
        return ImmutableEvidenceItem.builder()
                .reference(actionableCopyNumber.reference())
                .source(ActionabilitySource.fromString(actionableCopyNumber.source()))
                .drug(actionableCopyNumber.drug())
                .drugsType(actionableCopyNumber.drugsType())
                .level(EvidenceLevel.fromString(actionableCopyNumber.level()))
                .response(actionableCopyNumber.response())
                .cancerType(actionableCopyNumber.cancerType())
                .scope(EvidenceScope.SPECIFIC);
    }
}
