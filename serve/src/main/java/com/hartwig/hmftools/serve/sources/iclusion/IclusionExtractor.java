package com.hartwig.hmftools.serve.sources.iclusion;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.serve.classification.EventType;
import com.hartwig.hmftools.iclusion.datamodel.IclusionMutation;
import com.hartwig.hmftools.iclusion.datamodel.IclusionMutationCondition;
import com.hartwig.hmftools.iclusion.datamodel.IclusionTrial;
import com.hartwig.hmftools.serve.actionability.fusion.ActionableFusion;
import com.hartwig.hmftools.serve.actionability.gene.ActionableGene;
import com.hartwig.hmftools.serve.actionability.hotspot.ActionableHotspot;
import com.hartwig.hmftools.serve.actionability.range.ActionableRange;
import com.hartwig.hmftools.serve.actionability.signature.ActionableSignature;
import com.hartwig.hmftools.serve.checkertool.CheckCodonRanges;
import com.hartwig.hmftools.serve.checkertool.CheckExons;
import com.hartwig.hmftools.serve.extraction.ActionableEventFactory;
import com.hartwig.hmftools.serve.extraction.EventExtractor;
import com.hartwig.hmftools.serve.extraction.EventExtractorOutput;
import com.hartwig.hmftools.serve.extraction.ExtractionFunctions;
import com.hartwig.hmftools.serve.extraction.ExtractionResult;
import com.hartwig.hmftools.serve.extraction.ImmutableExtractionResult;
import com.hartwig.hmftools.serve.util.ProgressTracker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class IclusionExtractor {

    private static final Logger LOGGER = LogManager.getLogger(IclusionExtractor.class);

    @NotNull
    private final EventExtractor eventExtractor;
    @NotNull
    private final ActionableTrialFactory actionableTrialFactory;

    IclusionExtractor(@NotNull final EventExtractor eventExtractor, @NotNull final ActionableTrialFactory actionableTrialFactory) {
        this.eventExtractor = eventExtractor;
        this.actionableTrialFactory = actionableTrialFactory;
    }

    @NotNull
    public ExtractionResult extract(@NotNull List<IclusionTrial> trials, @NotNull CheckExons checkExons,
            @NotNull CheckCodonRanges checkCodonRanges) {
        // We assume filtered trials (no empty acronyms, only OR mutations, and no negated mutations

        ProgressTracker tracker = new ProgressTracker("iClusion", trials.size());
        List<ExtractionResult> extractions = Lists.newArrayList();
        for (IclusionTrial trial : trials) {
            List<ActionableTrial> actionableTrials = actionableTrialFactory.toActionableTrials(trial);
            for (ActionableTrial actionableTrial : actionableTrials) {
                LOGGER.debug("Generated {} based off {}", actionableTrial, trial);
            }

            List<EventExtractorOutput> eventExtractions = Lists.newArrayList();
            for (IclusionMutationCondition mutationCondition : trial.mutationConditions()) {
                for (IclusionMutation mutation : mutationCondition.mutations()) {
                    LOGGER.debug("Interpreting '{}' on '{}' for {}", mutation.name(), mutation.gene(), trial.acronym());
                    eventExtractions.add(eventExtractor.extract(mutation.gene(), null, mutation.type(), mutation.name()));

                    if (mutation.type() == EventType.CODON) {
                        checkCodonRanges.run(mutation.name(), mutation.gene());
                    } else if (mutation.type() == EventType.EXON) {
                        checkExons.run(mutation.name(), mutation.gene());
                    }
                }
            }

            extractions.add(toExtractionResult(actionableTrials, eventExtractions));

            tracker.update();
        }

        return ExtractionFunctions.merge(extractions);
    }

    @NotNull
    private static ExtractionResult toExtractionResult(@NotNull List<ActionableTrial> actionableTrials,
            @NotNull List<EventExtractorOutput> eventExtractions) {
        Set<ActionableHotspot> actionableHotspots = Sets.newHashSet();
        Set<ActionableRange> actionableRanges = Sets.newHashSet();
        Set<ActionableGene> actionableGenes = Sets.newHashSet();
        Set<ActionableFusion> actionableFusions = Sets.newHashSet();
        Set<ActionableSignature> actionableSignatures = Sets.newHashSet();

        for (ActionableTrial trial : actionableTrials) {
            for (EventExtractorOutput extraction : eventExtractions) {
                actionableHotspots.addAll(ActionableEventFactory.toActionableHotspots(trial, extraction.hotspots()));
                actionableRanges.addAll(ActionableEventFactory.toActionableRanges(trial, extraction.codons()));
                actionableRanges.addAll(ActionableEventFactory.toActionableRanges(trial, extraction.exons()));

                if (extraction.geneLevelEvent() != null) {
                    actionableGenes.add(ActionableEventFactory.geneLevelEventToActionableGene(trial, extraction.geneLevelEvent()));
                }

                if (extraction.knownCopyNumber() != null) {
                    actionableGenes.add(ActionableEventFactory.copyNumberToActionableGene(trial, extraction.knownCopyNumber()));
                }

                if (extraction.knownFusionPair() != null) {
                    actionableFusions.add(ActionableEventFactory.toActionableFusion(trial, extraction.knownFusionPair()));
                }

                if (extraction.signatureName() != null) {
                    actionableSignatures.add(ActionableEventFactory.toActionableSignature(trial, extraction.signatureName()));
                }
            }
        }

        return ImmutableExtractionResult.builder()
                .actionableHotspots(actionableHotspots)
                .actionableRanges(actionableRanges)
                .actionableGenes(actionableGenes)
                .actionableFusions(actionableFusions)
                .actionableSignatures(actionableSignatures)
                .build();
    }
}
