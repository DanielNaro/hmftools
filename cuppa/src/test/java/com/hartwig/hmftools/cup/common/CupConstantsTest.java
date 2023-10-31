package com.hartwig.hmftools.cup.common;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.cup.feature.KnownMutation;
import junit.framework.TestCase;
import org.junit.Test;

import java.util.List;

import static com.hartwig.hmftools.common.variant.VariantType.INDEL;
import static com.hartwig.hmftools.common.variant.VariantType.SNP;
import static com.hartwig.hmftools.cup.common.CupConstants.KNOWN_MUTATIONS;
import static com.hartwig.hmftools.cup.common.CupConstants.loadKnownMutations;
import static org.junit.Assert.assertEquals;

public class CupConstantsTest {

    @Test
    public void testLoadKnownMutations_V37() {
        KNOWN_MUTATIONS.clear();
        List<KnownMutation> expected = Lists.newArrayList();

        expected.add(new KnownMutation("EGFR", SNP, "C", "T", 55249071, 55249071));

        // p.Leu858Ar
        expected.add(new KnownMutation("EGFR", SNP, "T", "G", 55259515, 55259515));

        // inframe DEL in exon 19 (canonical transcript)
        expected.add(new KnownMutation("EGFR", INDEL, "", "", 55242415, 55242513));

        // exon 20
        expected.add(new KnownMutation("EGFR", INDEL, "", "", 55248986, 55249171));

        loadKnownMutations(RefGenomeVersion.V37);
        assertEquals(expected, KNOWN_MUTATIONS);
    }

    @Test
    public void testLoadKnownMutations_V38() {
        List<KnownMutation> expected = Lists.newArrayList();
        KNOWN_MUTATIONS.clear();

        expected.add(new KnownMutation("EGFR", SNP, "C", "T", 55181378, 55181378));
        expected.add(new KnownMutation("EGFR", SNP, "T", "G", 55191822, 55191822));
        expected.add(new KnownMutation("EGFR", INDEL, "", "", 55174722, 55174820));
        expected.add(new KnownMutation("EGFR", INDEL, "", "", 55181293, 55181478));

        loadKnownMutations(RefGenomeVersion.V38);
        assertEquals(expected, KNOWN_MUTATIONS);
    }
}