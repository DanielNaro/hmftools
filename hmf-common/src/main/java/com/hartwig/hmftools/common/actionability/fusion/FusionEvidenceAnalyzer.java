package com.hartwig.hmftools.common.actionability.fusion;

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
import com.hartwig.hmftools.common.variant.structural.annotation.SimpleGeneFusion;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FusionEvidenceAnalyzer {

    @NotNull
    private final List<ActionableFusion> fusionPairs;
    @NotNull
    private final List<ActionablePromiscuousFive> promiscuousFive;
    @NotNull
    private final List<ActionablePromiscuousThree> promiscuousThree;

    FusionEvidenceAnalyzer(@NotNull final List<ActionableFusion> fusionPairs,
            @NotNull final List<ActionablePromiscuousFive> promiscuousFive,
            @NotNull final List<ActionablePromiscuousThree> promiscuousThree) {
        this.fusionPairs = fusionPairs;
        this.promiscuousFive = promiscuousFive;
        this.promiscuousThree = promiscuousThree;
    }

    @NotNull
    public Set<String> actionableGenes() {
        Set<String> genes = Sets.newHashSet();
        for (ActionableFusion fusionPairsSet : fusionPairs) {
            genes.add(fusionPairsSet.fiveGene());
            genes.add(fusionPairsSet.threeGene());
        }

        for (ActionablePromiscuousFive promiscuousFiveSet : promiscuousFive) {
            genes.add(promiscuousFiveSet.gene());
        }

        for (ActionablePromiscuousThree promiscuousThreeSet : promiscuousThree) {
            genes.add(promiscuousThreeSet.gene());
        }

        return genes;
    }

    @NotNull
    public List<EvidenceItem> evidenceForFusion(@NotNull SimpleGeneFusion geneFusion, @Nullable String primaryTumorLocation,
            @NotNull CancerTypeAnalyzer cancerTypeAnalyzer) {
        List<EvidenceItem> evidenceItems = Lists.newArrayList();

        for (ActionableFusion actionableFusion : fusionPairs) {
            if (actionableFusion.fiveGene().equals(geneFusion.fiveGene()) && actionableFusion.threeGene()
                    .equals(geneFusion.threeGene())) {
                ImmutableEvidenceItem.Builder evidenceBuilder = fromActionableFusionPairs(actionableFusion);
                evidenceBuilder.event(actionableFusion.fiveGene() + " - " + actionableFusion.threeGene() + " fusion");
                evidenceBuilder.isOnLabel(cancerTypeAnalyzer.isCancerTypeMatch(actionableFusion.cancerType(), primaryTumorLocation));
                evidenceItems.add(evidenceBuilder.build());
            }
        }

        for (ActionablePromiscuousFive actionablePromiscuousFive : promiscuousFive) {
            if (actionablePromiscuousFive.gene().equals(geneFusion.fiveGene())) {
                ImmutableEvidenceItem.Builder evidenceBuilder = fromActionableFusionsPromiscuousFive(actionablePromiscuousFive);
                evidenceBuilder.event(
                        actionablePromiscuousFive.gene() + " - " + geneFusion.threeGene() + " fusion");
                evidenceBuilder.isOnLabel(cancerTypeAnalyzer.isCancerTypeMatch(actionablePromiscuousFive.cancerType(),
                        primaryTumorLocation));

                evidenceItems.add(evidenceBuilder.build());
            }
        }

        for (ActionablePromiscuousThree actionablePromiscuousThree : promiscuousThree) {
            if (actionablePromiscuousThree.gene().equals(geneFusion.threeGene())) {
                ImmutableEvidenceItem.Builder evidenceBuilder = fromActionableFusionsPromiscuousThree(actionablePromiscuousThree);
                evidenceBuilder.event(
                        geneFusion.fiveGene() + " - " + actionablePromiscuousThree.gene() + " fusion");
                evidenceBuilder.isOnLabel(cancerTypeAnalyzer.isCancerTypeMatch(actionablePromiscuousThree.cancerType(),
                        primaryTumorLocation));

                evidenceItems.add(evidenceBuilder.build());
            }
        }

        return evidenceItems;
    }

    @NotNull
    private static ImmutableEvidenceItem.Builder fromActionableFusionPairs(@NotNull ActionableFusion actionableFusionPair) {
        return ImmutableEvidenceItem.builder()
                .reference(actionableFusionPair.reference())
                .source(ActionabilitySource.fromString(actionableFusionPair.source()))
                .drug(actionableFusionPair.drug())
                .drugsType(actionableFusionPair.drugsType())
                .level(EvidenceLevel.fromString(actionableFusionPair.level()))
                .response(actionableFusionPair.response())
                .cancerType(actionableFusionPair.cancerType())
                .scope(EvidenceScope.SPECIFIC);
    }

    @NotNull
    private static ImmutableEvidenceItem.Builder fromActionableFusionsPromiscuousThree(
            @NotNull ActionablePromiscuousThree actionablePromiscuousThree) {
        return ImmutableEvidenceItem.builder()
                .reference(actionablePromiscuousThree.reference())
                .source(ActionabilitySource.fromString(actionablePromiscuousThree.source()))
                .drug(actionablePromiscuousThree.drug())
                .drugsType(actionablePromiscuousThree.drugsType())
                .level(EvidenceLevel.fromString(actionablePromiscuousThree.level()))
                .response(actionablePromiscuousThree.response())
                .cancerType(actionablePromiscuousThree.cancerType())
                .scope(EvidenceScope.SPECIFIC);
    }

    @NotNull
    private static ImmutableEvidenceItem.Builder fromActionableFusionsPromiscuousFive(
            @NotNull ActionablePromiscuousFive actionablePromiscuousFive) {
        return ImmutableEvidenceItem.builder()
                .reference(actionablePromiscuousFive.reference())
                .source(ActionabilitySource.fromString(actionablePromiscuousFive.source()))
                .drug(actionablePromiscuousFive.drug())
                .drugsType(actionablePromiscuousFive.drugsType())
                .level(EvidenceLevel.fromString(actionablePromiscuousFive.level()))
                .response(actionablePromiscuousFive.response())
                .cancerType(actionablePromiscuousFive.cancerType())
                .scope(EvidenceScope.SPECIFIC);
    }
}