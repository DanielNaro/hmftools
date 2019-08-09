package com.hartwig.hmftools.linx.fusion;

import static com.hartwig.hmftools.linx.utils.GeneTestUtils.addGeneData;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.addTransExonData;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.createEnsemblGeneData;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.createGeneAnnotation;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.createTransExons;
import static com.hartwig.hmftools.linx.gene.SvGeneTranscriptCollection.extractTranscriptExonData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.variant.structural.annotation.EnsemblGeneData;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneAnnotation;
import com.hartwig.hmftools.common.variant.structural.annotation.Transcript;
import com.hartwig.hmftools.common.variant.structural.annotation.TranscriptData;
import com.hartwig.hmftools.linx.gene.SvGeneTranscriptCollection;

import org.junit.Test;

public class RnaFusionTest
{
    @Test
    public void testRnaMatching()
    {
        SvGeneTranscriptCollection geneTransCache = new SvGeneTranscriptCollection();

        String geneName = "GENE1";
        String geneId = "ENSG0001";
        String chromosome = "1";

        List<EnsemblGeneData> geneList = Lists.newArrayList();
        geneList.add(createEnsemblGeneData(geneId, geneName, chromosome, 1, 10000, 20000));

        // one on the negative strand
        String geneName2 = "GENE2";
        String geneId2 = "ENSG0003";
        geneList.add(createEnsemblGeneData(geneId2, geneName2, chromosome, -1, 10000, 20000));

        addGeneData(geneTransCache, chromosome, geneList);

        List<TranscriptData> transDataList = Lists.newArrayList();

        int transId = 1;
        byte strand = 1;

        long[] exonStarts = new long[]{10500, 11500, 12500, 13500};
        int[] exonPhases = new int[]{-1, 1, 2, -1};

        TranscriptData transData = createTransExons(geneId, transId++, strand, exonStarts, exonPhases, 100, true);
        String transName = transData.TransName;
        transDataList.add(transData);

        addTransExonData(geneTransCache, geneId, transDataList);

        transDataList = Lists.newArrayList();

        strand = -1;

        exonPhases = new int[]{-1, -1, -1, -1};
        transData = createTransExons(geneId, transId++, strand, exonStarts, exonPhases, 100, true);
        String transName2 = transData.TransName;
        transDataList.add(transData);

        addTransExonData(geneTransCache, geneId2, transDataList);

        FusionFinder fusionAnalyser = new FusionFinder(null, geneTransCache);

        // test positive strand

        long svPos1 = 12700;
        GeneAnnotation geneAnnot1 = createGeneAnnotation(0, true, geneName, geneId, 1, chromosome, svPos1, 1);

        transData = geneTransCache.getTranscriptData(geneId, transName);
        assertEquals(4, transData.exons().size());

        Transcript trans = extractTranscriptExonData(transData, geneAnnot1.position(), geneAnnot1);

        assertTrue(trans != null);

        // test upstream scenarios
        long rnaPosition = 12600;
        boolean isValid = fusionAnalyser.isTranscriptBreakendViableForRnaBoundary(
                trans, true, geneAnnot1.position(), rnaPosition, true);

        assertTrue(isValid);

        // after the next splice site
        svPos1 = 13500;
        geneAnnot1.setPositionalData(chromosome, svPos1, (byte)1);

        isValid = fusionAnalyser.isTranscriptBreakendViableForRnaBoundary(
                trans, true, geneAnnot1.position(), rnaPosition, true);

        assertFalse(isValid);

        // test non-exact RNA boundary
        rnaPosition = 12550;
        isValid = fusionAnalyser.isTranscriptBreakendViableForRnaBoundary(
                trans, true, geneAnnot1.position(), rnaPosition, false);

        assertFalse(isValid);

        rnaPosition = 12700;
        isValid = fusionAnalyser.isTranscriptBreakendViableForRnaBoundary(
                trans, true, geneAnnot1.position(), rnaPosition, false);

        assertFalse(isValid);

        // test downstream

        // exact base at 2nd exon
        svPos1 = 100; // pre promotor
        geneAnnot1.setPositionalData(chromosome, svPos1, (byte)1);

        rnaPosition = 11500;
        isValid = fusionAnalyser.isTranscriptBreakendViableForRnaBoundary(
                trans, false, geneAnnot1.position(), rnaPosition, true);

        assertTrue(isValid);

        // before previous splice acceptor
        svPos1 = 12400;
        geneAnnot1.setPositionalData(chromosome, svPos1, (byte)1);

        rnaPosition = 13500;
        isValid = fusionAnalyser.isTranscriptBreakendViableForRnaBoundary(
                trans, false, geneAnnot1.position(), rnaPosition, true);

        assertFalse(isValid);

        svPos1 = 13000;
        geneAnnot1.setPositionalData(chromosome, svPos1, (byte)1);

        // valid position
        rnaPosition = 13500;
        isValid = fusionAnalyser.isTranscriptBreakendViableForRnaBoundary(
                trans, false, geneAnnot1.position(), rnaPosition, true);

        assertTrue(isValid);


        // now test the negative strand

        long svPos2 = 12700;
        GeneAnnotation geneAnnot2 = createGeneAnnotation(1, true, geneName2, geneId2, -1, chromosome, svPos2, -1);

        transData = geneTransCache.getTranscriptData(geneId2, transName2);
        assertEquals(4, transData.exons().size());

        Transcript trans2 = extractTranscriptExonData(transData, geneAnnot2.position(), geneAnnot2);

        assertTrue(trans2 != null);

        // upstream

        rnaPosition = 11500; // 3rd exon end

        svPos2 = 11600;
        geneAnnot2.setPositionalData(chromosome, svPos2, (byte)1);

        isValid = fusionAnalyser.isTranscriptBreakendViableForRnaBoundary(
                trans2, true, geneAnnot2.position(), rnaPosition, true);

        assertTrue(isValid);

        // test downstream

        rnaPosition = 11600; // 3rd exon start

        svPos2 = 11700;
        geneAnnot2.setPositionalData(chromosome, svPos2, (byte)1);

        isValid = fusionAnalyser.isTranscriptBreakendViableForRnaBoundary(
                trans2, false, geneAnnot2.position(), rnaPosition, true);

        assertTrue(isValid);

        // before prev splice acceptor is invali
        svPos2 = 12700;
        geneAnnot2.setPositionalData(chromosome, svPos2, (byte)1);

        isValid = fusionAnalyser.isTranscriptBreakendViableForRnaBoundary(
                trans2, false, geneAnnot2.position(), rnaPosition, true);

        assertFalse(isValid);

        // invalid too far upstream of promotor
        rnaPosition = 12600; // 3rd exon start

        svPos2 = 130000;
        geneAnnot2.setPositionalData(chromosome, svPos2, (byte)1);

        isValid = fusionAnalyser.isTranscriptBreakendViableForRnaBoundary(
                trans2, false, geneAnnot2.position(), rnaPosition, true);

        assertFalse(isValid);

    }
}
