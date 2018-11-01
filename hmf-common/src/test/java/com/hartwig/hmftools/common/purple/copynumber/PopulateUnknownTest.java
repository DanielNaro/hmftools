package com.hartwig.hmftools.common.purple.copynumber;

import static org.junit.Assert.assertEquals;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.purple.gender.Gender;
import com.hartwig.hmftools.common.purple.segment.SegmentSupport;

import org.junit.Test;

public class PopulateUnknownTest {

    @Test
    public void testJoinUnknownRegions() {
        final CombinedRegion region1 = ExtendLongArmTest.createCombinedRegion(1, 1000, 0, 0, SegmentSupport.NONE);
        final CombinedRegion region2 = ExtendLongArmTest.createCombinedRegion(1001, 2000, 0, 0, SegmentSupport.NONE);
        final CombinedRegion region3 = ExtendLongArmTest.createCombinedRegion(2001, 3000, 0, 0, SegmentSupport.NONE);
        final CombinedRegion region4 = ExtendLongArmTest.createCombinedRegion(3001, 4000, 0, 0, SegmentSupport.NONE);
        region4.setTumorCopyNumber(CopyNumberMethod.BAF_WEIGHTED, 2);

        final PopulateUnknown victim = new PopulateUnknown(Gender.FEMALE);
        List<CombinedRegion> result = victim.populateUnknown(Lists.newArrayList(region1, region2, region3, region4));
        assertEquals(2, result.size());

    }

}
