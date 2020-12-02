package com.hartwig.hmftools.serve.sources.iclusion;

import static org.junit.Assert.assertEquals;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.iclusion.datamodel.IclusionTrial;
import com.hartwig.hmftools.iclusion.datamodel.IclusionTumorLocation;
import com.hartwig.hmftools.iclusion.datamodel.ImmutableIclusionTumorLocation;

import org.junit.Test;

public class ActionableTrialFactoryTest {

    @Test
    public void canCreateActionableTrials() {
        String location1 = "loc1";
        String location2 = "loc2";
        String treatment = "trial";

        IclusionTumorLocation loc1 = ImmutableIclusionTumorLocation.builder().primaryTumorLocation(location1).build();
        IclusionTumorLocation loc2 = ImmutableIclusionTumorLocation.builder().primaryTumorLocation(location2).build();
        IclusionTrial trial = IclusionTestFactory.trialWithTumors(treatment, Lists.newArrayList(loc1, loc2));

        List<ActionableTrial> actionableTrials = ActionableTrialFactory.toActionableTrials(trial);
        assertEquals(2, actionableTrials.size());
        assertEquals(treatment, actionableTrials.get(0).treatment());
        assertEquals(treatment, actionableTrials.get(1).treatment());
        assertEquals(location1, actionableTrials.get(0).cancerType());
        assertEquals(location2, actionableTrials.get(1).cancerType());
    }

}