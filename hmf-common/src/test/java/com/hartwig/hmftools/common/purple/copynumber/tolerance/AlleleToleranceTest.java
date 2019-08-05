package com.hartwig.hmftools.common.purple.copynumber.tolerance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hartwig.hmftools.common.purple.PurityAdjuster;
import com.hartwig.hmftools.common.purple.PurpleDatamodelTest;
import com.hartwig.hmftools.common.purple.gender.Gender;
import com.hartwig.hmftools.common.purple.region.FittedRegion;
import com.hartwig.hmftools.common.purple.region.ImmutableFittedRegion;

import org.junit.Before;
import org.junit.Test;

public class AlleleToleranceTest {

    AlleleTolerance victim;

    @Before
    public void setup() {
        victim = new AlleleTolerance(new PurityAdjuster(Gender.FEMALE, 1, 1));
    }

    @Test
    public void testMinObservedBafDeviation() {
        FittedRegion left = createBafRegion(1000, 1, 0);
        FittedRegion right = createBafRegion(1000, 0.5, 0);
        assertTrue(victim.inTolerance(left, right));

        left = ImmutableFittedRegion.copyOf(left).withObservedBAF(0.031);
        assertFalse(victim.inTolerance(left, right));
    }

    @Test
    public void testMinBafCount() {
        FittedRegion left = createBafRegion(0, 1, 0.031);
        FittedRegion right = createBafRegion(1000, 0.5, 0);
        assertTrue(victim.inTolerance(left, right));

        left = ImmutableFittedRegion.copyOf(left).withBafCount(1000);
        assertFalse(victim.inTolerance(left, right));
    }

    @Test
    public void testRelativeChange() {
        FittedRegion left = createCopyNumberRegion(1000, 10, 10);
        FittedRegion right = createCopyNumberRegion(1000, 9.1, 9.1);
        assertTrue(victim.inTolerance(left, right));

        left = createCopyNumberRegion(1000, 2, 2);
        right = createCopyNumberRegion(1000, 1.1, 1.1);
        assertFalse(victim.inTolerance(left, right));
    }

    @Test
    public void testRelativeChangeToNegativeCopyNumber() {
        assertEquals(6, AlleleTolerance.relativeCopyNumberChange(2.5, -0.5), 0.01);
    }

    private FittedRegion createBafRegion(int bafCount, double tumorBaf, double observervedBaf) {
        return PurpleDatamodelTest.createDefaultFittedRegion("1", 100, 200)
                .bafCount(bafCount)
                .tumorBAF(tumorBaf)
                .observedBAF(observervedBaf)
                .tumorCopyNumber((double) 2)
                .refNormalisedCopyNumber((double) 2)
                .depthWindowCount(100)
                .build();
    }

    private FittedRegion createCopyNumberRegion(int depthWindowCount, double copyNumber, double refCopyNumber) {
        return PurpleDatamodelTest.createDefaultFittedRegion("1", 100, 200)
                .bafCount(0)
                .tumorCopyNumber(copyNumber)
                .refNormalisedCopyNumber(refCopyNumber)
                .depthWindowCount(depthWindowCount)
                .build();
    }
}
