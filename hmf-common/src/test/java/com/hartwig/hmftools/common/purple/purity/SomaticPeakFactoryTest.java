package com.hartwig.hmftools.common.purple.purity;

import static org.junit.Assert.assertEquals;

import java.util.List;

import com.google.common.collect.Lists;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class SomaticPeakFactoryTest {

    private static final double EPSILON = 1e-10;

    @Test
    public void testFindPeaks() {

        List<Double> sample = Lists.newArrayList();
        for (int i = 0; i < 100; i++) {
            sample.add(i / 100d);
        }

        for (int i = 0; i < 10; i++) {
            sample.add(0.2);
            sample.add(0.4);
            sample.add(0.6);
        }

        final List<SomaticPeak> peaks = SomaticPeakFactory.findPeaks(sample);
        assertEquals(2, peaks.size());
        assertPeak(peaks.get(0), 0.2, 11);
        assertPeak(peaks.get(1), 0.4, 11);

    }

    private static void assertPeak(@NotNull SomaticPeak victim, double vaf, int count) {
        assertEquals(vaf, victim.alleleFrequency(), EPSILON);
        assertEquals(count, victim.count());
    }

}


