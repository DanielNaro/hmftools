package com.hartwig.hmftools.common.purple.region;

import static org.junit.Assert.assertEquals;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class PloidyPenaltyTest {

    private static final double EPSILON = 1e-10;

    @Test
    public void testNew() {
        assertNew(2.0, "A");
        assertNew(1.0, "AB");
        assertNew(3.0, "AA");
        assertNew(2.0, "AAB");
        assertNew(2.0, "AABB");
        assertNew(4.0, "AAA");
        assertNew(3.0, "AAAB");
        assertNew(3.0, "AAABB");
        assertNew(4.0, "AAABBB");
    }


    private static void assertNew(double expectedResult, @NotNull final String descriptiveBAF) {
        int major = (int) descriptiveBAF.chars().filter(x -> x == 'A').count();
        int minor = (int) descriptiveBAF.chars().filter(x -> x == 'B').count();
        assertEquals(expectedResult, PloidyPenalty.penalty(1, minor, major), EPSILON);
    }
}
