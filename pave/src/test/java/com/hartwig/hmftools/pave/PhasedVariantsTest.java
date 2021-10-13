package com.hartwig.hmftools.pave;

import static com.hartwig.hmftools.common.fusion.FusionCommon.POS_STRAND;
import static com.hartwig.hmftools.common.genome.region.Strand.NEG_STRAND;
import static com.hartwig.hmftools.common.test.GeneTestUtils.CHR_1;
import static com.hartwig.hmftools.common.test.GeneTestUtils.GENE_ID_1;
import static com.hartwig.hmftools.common.test.GeneTestUtils.GENE_NAME_1;
import static com.hartwig.hmftools.common.test.GeneTestUtils.TRANS_ID_1;
import static com.hartwig.hmftools.common.test.GeneTestUtils.createTransExons;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.common.variant.impact.VariantEffect.FRAMESHIFT;
import static com.hartwig.hmftools.common.variant.impact.VariantEffect.MISSENSE;
import static com.hartwig.hmftools.common.variant.impact.VariantEffect.PHASED_INFRAME_DELETION;
import static com.hartwig.hmftools.common.variant.impact.VariantEffect.PHASED_INFRAME_INSERTION;
import static com.hartwig.hmftools.common.variant.impact.VariantEffect.SYNONYMOUS;
import static com.hartwig.hmftools.pave.ImpactTestUtils.createMockGenome;
import static com.hartwig.hmftools.pave.VariantData.NO_LOCAL_PHASE_SET;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import com.hartwig.hmftools.common.gene.TranscriptData;
import com.hartwig.hmftools.common.test.MockRefGenome;

import org.junit.Test;

public class PhasedVariantsTest
{
    /* scenarios:
        - DEL and DEL causing inframe
        - DEL and DEL remaining frameshift
        - DEL and INS causing missense
        - DEL and INS causing synonymous
        - 3x DEL/INS causing inframe insert or delete or missense
    */

    private MockRefGenome mRefGenome = createMockGenome(201);
    private String mRefBases = mRefGenome.RefGenomeMap.get(CHR_1);
    private ImpactClassifier mClassifier = new ImpactClassifier(mRefGenome);

    @Test
    public void testInframeDelPair()
    {
        TranscriptData transDataPos = createTransExons(
            GENE_ID_1, TRANS_ID_1, POS_STRAND, new int[] {0, 100}, 80, 10, 190, false, "");

        // 2 DELs making inframe DEL
        int pos = 20;

        String ref = mRefBases.substring(pos, pos + 3);
        String alt = mRefBases.substring(pos, pos + 1);
        VariantData var1 = new VariantData(CHR_1, pos, ref, alt);
        var1.setVariantDetails(1, "", "", 0);
        VariantTransImpact impact1 = classifyVariant(var1, transDataPos);

        assertTrue(impact1.codingContext().IsFrameShift);
        assertTrue(impact1.hasEffect(FRAMESHIFT));

        pos = 25;
        ref = mRefBases.substring(pos, pos + 2);
        alt = mRefBases.substring(pos, pos + 1);
        VariantData var2 = new VariantData(CHR_1, pos, ref, alt);
        var2.setVariantDetails(1, "", "", 0);
        VariantTransImpact impact2 = classifyVariant(var2, transDataPos);

        assertTrue(impact2.codingContext().IsFrameShift);
        assertTrue(impact2.hasEffect(FRAMESHIFT));

        mClassifier.processPhasedVariants(NO_LOCAL_PHASE_SET);

        assertTrue(impact1.phasedFrameshift());
        assertFalse(impact1.codingContext().IsFrameShift);
        assertFalse(impact1.hasEffect(FRAMESHIFT));
        assertTrue(impact1.hasEffect(PHASED_INFRAME_DELETION));
        assertFalse(impact2.codingContext().IsFrameShift);
        assertFalse(impact2.hasEffect(FRAMESHIFT));
        assertTrue(impact2.hasEffect(PHASED_INFRAME_DELETION));

        // as before but with an extra base so remaining out of frame
        var1.getImpacts().clear();
        impact1 = classifyVariant(var1, transDataPos);

        assertTrue(impact1.codingContext().IsFrameShift);
        assertTrue(impact1.hasEffect(FRAMESHIFT));

        pos = 25;
        ref = mRefBases.substring(pos, pos + 3);
        alt = mRefBases.substring(pos, pos + 1);
        var2 = new VariantData(CHR_1, pos, ref, alt);
        var2.setVariantDetails(1, "", "", 0);
        impact2 = classifyVariant(var2, transDataPos);

        assertTrue(impact2.codingContext().IsFrameShift);
        assertTrue(impact2.hasEffect(FRAMESHIFT));

        mClassifier.processPhasedVariants(NO_LOCAL_PHASE_SET);

        assertTrue(impact1.codingContext().IsFrameShift);
        assertTrue(impact1.hasEffect(FRAMESHIFT));

        assertTrue(impact2.codingContext().IsFrameShift);
        assertTrue(impact2.hasEffect(FRAMESHIFT));

        // 2 DELs which overlap ref codon bases but can form a frameshift
        // codons 13-15, 16-18, 19-21, 22-24 etc
        pos = 16;
        ref = mRefBases.substring(pos, pos + 3);
        alt = mRefBases.substring(pos, pos + 1); // deletes 17 & 18;
        var1 = new VariantData(CHR_1, pos, ref, alt);
        var1.setVariantDetails(1, "", "", 0);
        impact1 = classifyVariant(var1, transDataPos);
        assertEquals(16, impact1.proteinContext().RefCodonsRange[SE_START]);
        assertEquals(21, impact1.proteinContext().RefCodonsRange[SE_END]);

        assertTrue(impact1.codingContext().IsFrameShift);
        assertTrue(impact1.hasEffect(FRAMESHIFT));

        pos = 20;
        ref = mRefBases.substring(pos, pos + 2);
        alt = mRefBases.substring(pos, pos + 1); // deletes 21 being the 3rd codon base which they share
        var2 = new VariantData(CHR_1, pos, ref, alt);
        var2.setVariantDetails(1, "", "", 0);
        impact2 = classifyVariant(var2, transDataPos);
        assertEquals(19, impact2.proteinContext().RefCodonsRange[SE_START]);
        assertEquals(24, impact2.proteinContext().RefCodonsRange[SE_END]);

        assertTrue(impact2.codingContext().IsFrameShift);
        assertTrue(impact2.hasEffect(FRAMESHIFT));

        mClassifier.processPhasedVariants(NO_LOCAL_PHASE_SET);

        assertTrue(impact1.phasedFrameshift());
        assertFalse(impact1.codingContext().IsFrameShift);
        assertFalse(impact1.hasEffect(FRAMESHIFT));
        assertTrue(impact1.hasEffect(PHASED_INFRAME_DELETION));
        assertFalse(impact2.codingContext().IsFrameShift);
        assertFalse(impact2.hasEffect(FRAMESHIFT));
        assertTrue(impact2.hasEffect(PHASED_INFRAME_DELETION));
    }

    @Test
    public void testInframeDelInsPair()
    {
        TranscriptData transDataNeg = createTransExons(
                GENE_ID_1, TRANS_ID_1, NEG_STRAND, new int[] { 0, 100 }, 80, 10, 190, false, "");

        // DEL and INS making inframe insert
        int pos = 20;

        String ref = mRefBases.substring(pos, pos + 3);
        String alt = mRefBases.substring(pos, pos + 1);
        VariantData var1 = new VariantData(CHR_1, pos, ref, alt);
        var1.setVariantDetails(1, "", "", 0);
        VariantTransImpact impact1 = classifyVariant(var1, transDataNeg);

        assertTrue(impact1.codingContext().IsFrameShift);
        assertTrue(impact1.hasEffect(FRAMESHIFT));

        pos = 25;
        ref = mRefBases.substring(pos, pos);
        alt = ref + "AAAAAAAA";
        VariantData var2 = new VariantData(CHR_1, pos, ref, alt);
        var2.setVariantDetails(1, "", "", 0);
        VariantTransImpact impact2 = classifyVariant(var2, transDataNeg);

        assertTrue(impact2.codingContext().IsFrameShift);
        assertTrue(impact2.hasEffect(FRAMESHIFT));

        mClassifier.processPhasedVariants(NO_LOCAL_PHASE_SET);

        assertTrue(impact1.phasedFrameshift());
        assertFalse(impact1.codingContext().IsFrameShift);
        assertFalse(impact1.hasEffect(FRAMESHIFT));
        assertTrue(impact1.hasEffect(PHASED_INFRAME_INSERTION));
        assertFalse(impact2.codingContext().IsFrameShift);
        assertFalse(impact2.hasEffect(FRAMESHIFT));
        assertTrue(impact2.hasEffect(PHASED_INFRAME_INSERTION));

        // again but remaining out-of-frame
        var1.getImpacts().clear();

        impact1 = classifyVariant(var1, transDataNeg);

        pos = 25;
        ref = mRefBases.substring(pos, pos);
        alt = ref + "AAAAAAA";
        var2 = new VariantData(CHR_1, pos, ref, alt);
        var2.setVariantDetails(1, "", "", 0);
        impact2 = classifyVariant(var2, transDataNeg);

        assertTrue(impact2.codingContext().IsFrameShift);
        assertTrue(impact2.hasEffect(FRAMESHIFT));

        mClassifier.processPhasedVariants(NO_LOCAL_PHASE_SET);

        assertTrue(impact1.codingContext().IsFrameShift);
        assertTrue(impact1.hasEffect(FRAMESHIFT));
        assertTrue(impact2.codingContext().IsFrameShift);
        assertTrue(impact2.hasEffect(FRAMESHIFT));
    }

    @Test
    public void testMissenseSynonymousDelInsPair()
    {
        TranscriptData transDataNeg = createTransExons(
                GENE_ID_1, TRANS_ID_1, POS_STRAND, new int[] { 0, 100 }, 80, 10, 190, false, "");

        // DEL and INS making missense and symonymous effects
        int pos = 20;

        String ref = mRefBases.substring(pos, pos + 1);
        String alt = ref + "AGCT";
        VariantData var1 = new VariantData(CHR_1, pos, ref, alt);
        var1.setVariantDetails(1, "", "", 0);
        VariantTransImpact impact1 = classifyVariant(var1, transDataNeg);

        assertTrue(impact1.codingContext().IsFrameShift);
        assertTrue(impact1.hasEffect(FRAMESHIFT));

        pos = 25;
        ref = mRefBases.substring(pos, pos + 5);
        alt = mRefBases.substring(pos, pos + 1);
        VariantData var2 = new VariantData(CHR_1, pos, ref, alt);
        var2.setVariantDetails(1, "", "", 0);
        VariantTransImpact impact2 = classifyVariant(var2, transDataNeg);

        assertTrue(impact2.codingContext().IsFrameShift);
        assertTrue(impact2.hasEffect(FRAMESHIFT));

        mClassifier.processPhasedVariants(NO_LOCAL_PHASE_SET);

        assertTrue(impact1.phasedFrameshift());
        assertFalse(impact1.codingContext().IsFrameShift);
        assertFalse(impact1.hasEffect(FRAMESHIFT));
        assertTrue(impact1.hasEffect(MISSENSE));
        assertFalse(impact2.codingContext().IsFrameShift);
        assertFalse(impact2.hasEffect(FRAMESHIFT));
        assertTrue(impact2.hasEffect(MISSENSE));

        // now synonynous
        pos = 20;
        ref = mRefBases.substring(pos, pos + 5);
        alt = mRefBases.substring(pos, pos + 1);
        var1 = new VariantData(CHR_1, pos, ref, alt);
        var1.setVariantDetails(2, "", "", 0);
        impact1 = classifyVariant(var1, transDataNeg);

        assertTrue(impact1.codingContext().IsFrameShift);
        assertTrue(impact1.hasEffect(FRAMESHIFT));

        pos = 28;
        ref = mRefBases.substring(pos, pos + 1);
        alt = ref + "ATCG";
        var2 = new VariantData(CHR_1, pos, ref, alt);
        var2.setVariantDetails(2, "", "", 0);
        impact2 = classifyVariant(var2, transDataNeg);

        assertTrue(impact2.codingContext().IsFrameShift);
        assertTrue(impact2.hasEffect(FRAMESHIFT));

        mClassifier.processPhasedVariants(NO_LOCAL_PHASE_SET);

        assertTrue(impact1.phasedFrameshift());
        assertFalse(impact1.codingContext().IsFrameShift);
        assertFalse(impact1.hasEffect(FRAMESHIFT));
        assertTrue(impact1.hasEffect(SYNONYMOUS));
        assertFalse(impact2.codingContext().IsFrameShift);
        assertFalse(impact2.hasEffect(FRAMESHIFT));
        assertTrue(impact2.hasEffect(SYNONYMOUS));
    }

    @Test
    public void testMultiIndel()
    {
        TranscriptData transDataNeg = createTransExons(
                GENE_ID_1, TRANS_ID_1, NEG_STRAND, new int[] { 0, 100 }, 80, 10, 190, false, "");

        // DEL and INS making missense and symonymous effects
        int pos = 20;

        String ref = mRefBases.substring(pos, pos + 1);
        String alt = ref + "AG";
        VariantData var1 = new VariantData(CHR_1, pos, ref, alt);
        var1.setVariantDetails(1, "", "", 0);
        VariantTransImpact impact1 = classifyVariant(var1, transDataNeg);

        assertTrue(impact1.codingContext().IsFrameShift);
        assertTrue(impact1.hasEffect(FRAMESHIFT));

        pos = 25;
        ref = mRefBases.substring(pos, pos + 8);
        alt = mRefBases.substring(pos, pos + 1);
        VariantData var2 = new VariantData(CHR_1, pos, ref, alt);
        var2.setVariantDetails(1, "", "", 0);
        VariantTransImpact impact2 = classifyVariant(var2, transDataNeg);

        assertTrue(impact2.codingContext().IsFrameShift);
        assertTrue(impact2.hasEffect(FRAMESHIFT));

        pos = 35;
        ref = mRefBases.substring(pos, pos + 1);
        alt = ref + "GG";
        VariantData var3 = new VariantData(CHR_1, pos, ref, alt);
        var3.setVariantDetails(1, "", "", 0);
        VariantTransImpact impact3 = classifyVariant(var3, transDataNeg);

        assertTrue(impact3.codingContext().IsFrameShift);
        assertTrue(impact3.hasEffect(FRAMESHIFT));

        mClassifier.processPhasedVariants(NO_LOCAL_PHASE_SET);

        assertFalse(impact1.codingContext().IsFrameShift);
        assertFalse(impact1.hasEffect(FRAMESHIFT));
        assertTrue(impact1.phasedFrameshift());
        assertTrue(impact1.hasEffect(PHASED_INFRAME_DELETION));
        assertTrue(impact2.hasEffect(PHASED_INFRAME_DELETION));
        assertTrue(impact3.hasEffect(PHASED_INFRAME_DELETION));
    }

    private VariantTransImpact classifyVariant(final VariantData var, final TranscriptData transData)
    {
        VariantTransImpact impact = mClassifier.classifyVariant(var, transData);
        var.addImpact(GENE_NAME_1, impact);
        return impact;
    }


}
