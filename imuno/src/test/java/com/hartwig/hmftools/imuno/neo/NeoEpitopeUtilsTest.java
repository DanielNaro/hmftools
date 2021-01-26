package com.hartwig.hmftools.imuno.neo;

import static com.hartwig.hmftools.common.ensemblcache.GeneTestUtils.createTransExons;
import static com.hartwig.hmftools.common.fusion.FusionCommon.NEG_STRAND;
import static com.hartwig.hmftools.common.fusion.FusionCommon.POS_STRAND;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.NEG_ORIENT;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.POS_ORIENT;
import static com.hartwig.hmftools.common.neo.AminoAcidConverter.reverseStrandBases;
import static com.hartwig.hmftools.common.neo.AminoAcidConverter.swapDnaToRna;
import static com.hartwig.hmftools.common.neo.AminoAcidConverter.swapRnaToDna;
import static com.hartwig.hmftools.imuno.neo.NeoUtils.findSkippedExonBoundaries;
import static com.hartwig.hmftools.imuno.neo.NeoUtils.getDownstreamCodingBases;
import static com.hartwig.hmftools.imuno.neo.NeoUtils.getUpstreamCodingBases;

import static junit.framework.TestCase.assertEquals;

import java.util.List;

import com.hartwig.hmftools.common.ensemblcache.TranscriptData;
import com.hartwig.hmftools.common.genome.refgenome.MockRefGenome;

import org.apache.commons.compress.utils.Lists;
import org.junit.Assert;
import org.junit.Test;

public class NeoEpitopeUtilsTest
{
    public static final String CHR_1 = "";
    public static final String GENE_ID_1 = "ENSG001";
    public static final int TRANS_ID_1 = 1;

    @Test
    public void testDnaRnaRoutines()
    {
        String dnaBases = "AGCT";
        String rnaBases = swapDnaToRna(dnaBases);
        Assert.assertTrue(rnaBases.equals("AGCU"));
        Assert.assertTrue(dnaBases.equals(swapRnaToDna(rnaBases)));

        dnaBases = "AGCTTCGACT";
        String reverseStrandDna = reverseStrandBases(dnaBases);
        Assert.assertTrue(reverseStrandDna.equals("AGTCGAAGCT"));

        Assert.assertTrue(dnaBases.equals(reverseStrandBases(reverseStrandDna)));
    }

    public static String generateRandomBases(int length)
    {
        char[] str = new char[length];
        String bases = "ACGT";

        int baseIndex = 0;
        for(int i = 0; i < length; ++i)
        {
            str[i] = bases.charAt(baseIndex);

            if(baseIndex == 3)
                baseIndex = 0;
            else
                ++baseIndex;
        }

        return String.valueOf(str);
    }

    @Test
    public void testUpstreamCodingBases()
    {
        final MockRefGenome refGenome = new MockRefGenome();

        final String chr1Bases = generateRandomBases(100);

        refGenome.RefGenomeMap.put(CHR_1, chr1Bases);

        // tests: repeated for each strand

        int[] exonStarts = {0, 20, 40, 60, 80, 100};
        Integer codingStart = new Integer(25);
        Integer codingEnd = new Integer(85);

        TranscriptData transDataUp = createTransExons(
                GENE_ID_1, TRANS_ID_1, POS_STRAND, exonStarts, 10, codingStart, codingEnd, false, "");

        // upstream: intronic
        int nePosition = 32; // intronic
        byte neOrientation = NEG_ORIENT;

        String codingBases = getUpstreamCodingBases(refGenome, transDataUp, CHR_1, nePosition, neOrientation, 9);

        String actCodingBases = chr1Bases.substring(40, 49);
        Assert.assertEquals(actCodingBases, codingBases);

        // spanning 3 exons
        codingBases = getUpstreamCodingBases(refGenome, transDataUp, CHR_1, nePosition, neOrientation, 25);

        actCodingBases = chr1Bases.substring(40, 51) + chr1Bases.substring(60, 71) + chr1Bases.substring(80, 83);
        Assert.assertEquals(actCodingBases, codingBases);

        // upstream: intronic, coding bases insufficient for required bases
        nePosition = 76;
        codingBases = getUpstreamCodingBases(refGenome, transDataUp, CHR_1, nePosition, neOrientation, 10);

        actCodingBases = chr1Bases.substring(80, 86);
        Assert.assertEquals(actCodingBases, codingBases);

        // upstream: exonic
        nePosition = 44;
        codingBases = getUpstreamCodingBases(refGenome, transDataUp, CHR_1, nePosition, neOrientation, 10);

        actCodingBases = chr1Bases.substring(44, 51) + chr1Bases.substring(60, 63);
        Assert.assertEquals(actCodingBases, codingBases);

        // upstream: exonic, coding bases insufficient for required bases
        nePosition = 68;
        codingBases = getUpstreamCodingBases(refGenome, transDataUp, CHR_1, nePosition, neOrientation, 10);

        actCodingBases = chr1Bases.substring(68, 71) + chr1Bases.substring(80, 86);
        Assert.assertEquals(actCodingBases, codingBases);

        // upstream: exonic, coding finishes in same exon
        nePosition = 81;
        codingBases = getUpstreamCodingBases(refGenome, transDataUp, CHR_1, nePosition, neOrientation, 10);

        actCodingBases = chr1Bases.substring(81, 86);
        Assert.assertEquals(actCodingBases, codingBases);

        // test again with reverse orientation
        neOrientation = POS_ORIENT;
        nePosition = 55; // intronic

        codingBases = getUpstreamCodingBases(refGenome, transDataUp, CHR_1, nePosition, neOrientation, 9);

        actCodingBases = chr1Bases.substring(42, 51);
        Assert.assertEquals(actCodingBases, codingBases);

        // spanning 3 exons
        nePosition = 75;
        codingBases = getUpstreamCodingBases(refGenome, transDataUp, CHR_1, nePosition, neOrientation, 25);

        actCodingBases = chr1Bases.substring(28, 31) + chr1Bases.substring(40, 51) + chr1Bases.substring(60, 71);
        Assert.assertEquals(actCodingBases, codingBases);

        // intronic, coding bases insufficient for required bases
        nePosition = 35;
        codingBases = getUpstreamCodingBases(refGenome, transDataUp, CHR_1, nePosition, neOrientation, 10);

        actCodingBases = chr1Bases.substring(25, 31);
        Assert.assertEquals(actCodingBases, codingBases);

        // exonic
        nePosition = 44;
        codingBases = getUpstreamCodingBases(refGenome, transDataUp, CHR_1, nePosition, neOrientation, 10);

        actCodingBases = chr1Bases.substring(26, 31) + chr1Bases.substring(40, 45);
        Assert.assertEquals(actCodingBases, codingBases);

        // exonic, coding bases insufficient for required bases
        nePosition = 40;
        codingBases = getUpstreamCodingBases(refGenome, transDataUp, CHR_1, nePosition, neOrientation, 10);

        actCodingBases = chr1Bases.substring(25, 31) + chr1Bases.substring(40, 41);
        Assert.assertEquals(actCodingBases, codingBases);

        // exonic, coding finishes in same exon
        nePosition = 29;
        codingBases = getUpstreamCodingBases(refGenome, transDataUp, CHR_1, nePosition, neOrientation, 10);

        actCodingBases = chr1Bases.substring(25, 30);
        Assert.assertEquals(actCodingBases, codingBases);
    }

    @Test
    public void testDownstreamCodingBases()
    {
        final MockRefGenome refGenome = new MockRefGenome();

        final String chr1Bases = generateRandomBases(280);

        refGenome.RefGenomeMap.put(CHR_1, chr1Bases);

        // tests: repeated for each strand
        // intronic within coding section
        // starting in an exon but skipping it
        // starting in an exon and using it
        // upstream starting at first splice acceptor (2nd exon)
        // getting all remaining bases
        // non-coding - get all bases

        int[] exonStarts = { 100, 120, 140, 160, 180, 200, 220, 240, 260 };
        Integer codingStart = new Integer(125);
        Integer codingEnd = new Integer(245);

        TranscriptData transData = createTransExons(
                GENE_ID_1, TRANS_ID_1, POS_STRAND, exonStarts, 10, codingStart, codingEnd, false, "");

        // intronic
        int nePosition = 132;
        byte neOrientation = NEG_ORIENT;

        String codingBases = getDownstreamCodingBases(refGenome, transData, CHR_1, nePosition, neOrientation, 9, true, true, false);

        String actCodingBases = chr1Bases.substring(140, 149);
        Assert.assertEquals(actCodingBases, codingBases);

        // not allowed to start in the same exon
        nePosition = 144;
        codingBases = getDownstreamCodingBases(refGenome, transData, CHR_1, nePosition, neOrientation, 15, false, true, false);

        actCodingBases = chr1Bases.substring(160, 171) + chr1Bases.substring(180, 184);
        Assert.assertEquals(actCodingBases, codingBases);

        // allowed to start in an exon
        nePosition = 147;
        codingBases = getDownstreamCodingBases(refGenome, transData, CHR_1, nePosition, neOrientation, 10, true, true, false);

        actCodingBases = chr1Bases.substring(47, 51) + chr1Bases.substring(160, 166);
        Assert.assertEquals(actCodingBases, codingBases);

        // upstream
        nePosition = 80;
        codingBases = getDownstreamCodingBases(refGenome, transData, CHR_1, nePosition, neOrientation, 15, true, true, false);

        actCodingBases = chr1Bases.substring(120, 131) + chr1Bases.substring(140, 144);
        Assert.assertEquals(actCodingBases, codingBases);

        // all remaining bases
        nePosition = 215;
        codingBases = getDownstreamCodingBases(refGenome, transData, CHR_1, nePosition, neOrientation, 1, false, true, true);

        actCodingBases = chr1Bases.substring(220, 231) + chr1Bases.substring(240, 251) + chr1Bases.substring(260, 271);
        Assert.assertEquals(actCodingBases, codingBases);

        // non-coding transcript
        transData = createTransExons(
                GENE_ID_1, TRANS_ID_1, POS_STRAND, exonStarts, 10, null, null, false, "");

        nePosition = 215;
        codingBases = getDownstreamCodingBases(refGenome, transData, CHR_1, nePosition, neOrientation, 1, false, true, true);

        actCodingBases = chr1Bases.substring(220, 231) + chr1Bases.substring(240, 251) + chr1Bases.substring(260, 271);
        Assert.assertEquals(actCodingBases, codingBases);


        // repeated for negative strand
        transData = createTransExons(
                GENE_ID_1, TRANS_ID_1, NEG_STRAND, exonStarts, 10, codingStart, codingEnd, false, "");

        nePosition = 155;
        neOrientation = POS_ORIENT;

        codingBases = getDownstreamCodingBases(refGenome, transData, CHR_1, nePosition, neOrientation, 11, false, true, false);

        actCodingBases = chr1Bases.substring(140, 151);
        Assert.assertEquals(actCodingBases, codingBases);

        // not allowed to start in the same exon
        nePosition = 165;
        codingBases = getDownstreamCodingBases(refGenome, transData, CHR_1, nePosition, neOrientation, 10, false, true, false);

        actCodingBases = chr1Bases.substring(141, 151);
        Assert.assertEquals(actCodingBases, codingBases);

        // allowed to start in an exon
        nePosition = 165;
        codingBases = getDownstreamCodingBases(refGenome, transData, CHR_1, nePosition, neOrientation, 10, true, true, false);

        actCodingBases = chr1Bases.substring(147, 151) + chr1Bases.substring(160, 166);
        Assert.assertEquals(actCodingBases, codingBases);

        // upstream
        nePosition = 300;
        codingBases = getDownstreamCodingBases(refGenome, transData, CHR_1, nePosition, neOrientation, 15, true, true, false);

        actCodingBases = chr1Bases.substring(227, 231) + chr1Bases.substring(240, 251);
        Assert.assertEquals(actCodingBases, codingBases);

        // all remaining bases
        nePosition = 135;
        codingBases = getDownstreamCodingBases(refGenome, transData, CHR_1, nePosition, neOrientation, 1, false, true, true);

        actCodingBases = chr1Bases.substring(100, 111) + chr1Bases.substring(120, 131);
        Assert.assertEquals(actCodingBases, codingBases);

        // non-coding transcript
        transData = createTransExons(
                GENE_ID_1, TRANS_ID_1, NEG_STRAND, exonStarts, 10, null, null, false, "");

        nePosition = 135;
        codingBases = getDownstreamCodingBases(refGenome, transData, CHR_1, nePosition, neOrientation, 1, false, true, true);

        actCodingBases = chr1Bases.substring(100, 111) + chr1Bases.substring(120, 131);
        Assert.assertEquals(actCodingBases, codingBases);
    }

    @Test
    public void testSkippedAcceptorDonors()
    {
        int[] exonStarts = { 100, 300, 500, 700, 900 };

        TranscriptData transData1 = createTransExons(
                GENE_ID_1, TRANS_ID_1, POS_STRAND, exonStarts, 100, null, null, false, "");

        exonStarts = new int[] { 200, 500, 800, 1100 };

        TranscriptData transData2 = createTransExons(
                GENE_ID_1, TRANS_ID_1, POS_STRAND, exonStarts, 100, null, null, false, "");

        int[] posBoundaries = {50, 1200};

        final List<TranscriptData> transDataList = Lists.newArrayList();
        transDataList.add(transData1);
        transDataList.add(transData2);

        int skippedCount = findSkippedExonBoundaries(transDataList, posBoundaries, true, true);
        assertEquals(6, skippedCount); // exon start at 500 is repeated so not double-counted

        posBoundaries = new int[] {600, 1200};

        skippedCount = findSkippedExonBoundaries(transDataList, posBoundaries, true, true);
        assertEquals(4, skippedCount); // exon start at 500 is repeated so not double-counted

        posBoundaries = new int[] {1200, 1400};

        skippedCount = findSkippedExonBoundaries(transDataList, posBoundaries, true, true);
        assertEquals(0, skippedCount); // exon start at 500 is repeated so not double-counted

        // upstream donor skipping
        posBoundaries = new int[] {350, 1300};

        skippedCount = findSkippedExonBoundaries(transDataList, posBoundaries, false, false);
        assertEquals(6, skippedCount); // exon start at 500 is repeated so not double-counted

        posBoundaries = new int[] {650, 750};

        skippedCount = findSkippedExonBoundaries(transDataList, posBoundaries, false, false);
        assertEquals(0, skippedCount); // exon start at 500 is repeated so not double-counted



    }


}
