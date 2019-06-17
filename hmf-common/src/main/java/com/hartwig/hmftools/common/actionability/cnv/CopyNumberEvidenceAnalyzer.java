package com.hartwig.hmftools.common.actionability.cnv;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.actionability.ActionabilitySource;
import com.hartwig.hmftools.common.actionability.EvidenceItem;
import com.hartwig.hmftools.common.actionability.EvidenceLevel;
import com.hartwig.hmftools.common.actionability.EvidenceScope;
import com.hartwig.hmftools.common.actionability.ImmutableEvidenceItem;
import com.hartwig.hmftools.common.actionability.cancertype.CancerTypeAnalyzer;
import com.hartwig.hmftools.common.purple.gene.GeneCopyNumber;

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
        return actionableCopyNumbers.stream().map(ActionableCopyNumber::gene).collect(Collectors.toSet());
    }

    @NotNull
    public List<ActionableCopyNumber> actionableCopyNumbers() {
        return actionableCopyNumbers;
    }

    @NotNull
    public List<EvidenceItem> evidenceForCopyNumber(@NotNull GeneCopyNumber geneCopyNumber, double averageTumorPloidy,
            @Nullable String primaryTumorLocation, @NotNull CancerTypeAnalyzer cancerTypeAnalyzer) {
        List<EvidenceItem> evidenceItems = Lists.newArrayList();
        if (SignificantGeneCopyNumberFilter.isSignificant(averageTumorPloidy, geneCopyNumber.minCopyNumber())) {
            for (ActionableCopyNumber actionableCopyNumber : actionableCopyNumbers) {
                if (typeMatches(geneCopyNumber, actionableCopyNumber) && actionableCopyNumber.gene().equals(geneCopyNumber.gene())) {
                    ImmutableEvidenceItem.Builder evidenceBuilder = fromActionableCopyNumber(actionableCopyNumber);
                    evidenceBuilder.event(geneCopyNumber.gene() + " " + actionableCopyNumber.type().readableString());
                    evidenceBuilder.isOnLabel(cancerTypeAnalyzer.isCancerTypeMatch(actionableCopyNumber.cancerType(),
                            primaryTumorLocation));

                    evidenceItems.add(evidenceBuilder.build());
                }
            }
        }
        return evidenceItems;
    }

    private static boolean typeMatches(@NotNull GeneCopyNumber geneCopyNumber, @NotNull ActionableCopyNumber actionableCopyNumber) {
        CopyNumberType geneType = geneCopyNumber.minCopyNumber() < 1 ? CopyNumberType.DELETION : CopyNumberType.AMPLIFICATION;
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
