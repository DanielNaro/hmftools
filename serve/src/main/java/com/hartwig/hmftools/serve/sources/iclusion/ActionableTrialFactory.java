package com.hartwig.hmftools.serve.sources.iclusion;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.serve.Knowledgebase;
import com.hartwig.hmftools.common.serve.actionability.EvidenceDirection;
import com.hartwig.hmftools.common.serve.actionability.EvidenceLevel;
import com.hartwig.hmftools.iclusion.datamodel.IclusionTrial;
import com.hartwig.hmftools.iclusion.datamodel.IclusionTumorLocation;
import com.hartwig.hmftools.serve.curation.DoidLookup;

import org.apache.commons.compress.utils.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class ActionableTrialFactory {

    private static final Logger LOGGER = LogManager.getLogger(ActionableTrialFactory.class);

    @NotNull
    private final DoidLookup missingDoidLookup;

    public ActionableTrialFactory(@NotNull final DoidLookup missingDoidLookup) {
        this.missingDoidLookup = missingDoidLookup;
    }

    @NotNull
    public List<ActionableTrial> toActionableTrials(@NotNull IclusionTrial trial) {
        ImmutableActionableTrial.Builder actionableBuilder = ImmutableActionableTrial.builder()
                .source(Knowledgebase.ICLUSION)
                .treatment(trial.acronym())
                .level(EvidenceLevel.B)
                .direction(EvidenceDirection.RESPONSIVE)
                .urls(Sets.newHashSet("https://iclusion.org/hmf/" + trial.id()));

        List<ActionableTrial> actionableTrials = Lists.newArrayList();
        for (IclusionTumorLocation tumorLocation : trial.tumorLocations()) {
            List<String> doids = tumorLocation.doids();
            if (doids.isEmpty()) {
                Set<String> manualDoids = missingDoidLookup.lookupDoidsForCancerType(tumorLocation.primaryTumorLocation());
                if (manualDoids == null) {
                    LOGGER.warn("No doids could be derived for iClusion primary tumor location '{}'", tumorLocation.primaryTumorLocation());
                } else {
                    LOGGER.debug("Resolved doids to '{}' for iClusion primary tumor location '{}'",
                            manualDoids,
                            tumorLocation.primaryTumorLocation());
                    doids = Lists.newArrayList(manualDoids.iterator());
                }
            }
            for (String doid : doids) {
                actionableTrials.add(actionableBuilder.cancerType(tumorLocation.primaryTumorLocation()).doid(doid).build());
            }
        }

        return actionableTrials;
    }
}
