package com.hartwig.hmftools.common.purple.gene;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;

public class GeneCopyNumberTest {

    @Ignore
    public void testCopyNumberZero() {
        final GeneCopyNumber copyNumber = createRandom(new Random());
        assertEquals(0, copyNumber.value());
    }

    @NotNull
    private static GeneCopyNumber createRandom(@NotNull Random random) {
        return GeneCopyNumberFileTest.createRandom(random).minCopyNumber(-2.3).build();
    }
}
