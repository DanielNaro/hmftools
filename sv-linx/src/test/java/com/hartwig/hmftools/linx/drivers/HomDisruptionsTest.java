package com.hartwig.hmftools.linx.drivers;

import static com.hartwig.hmftools.linx.drivers.DriverEventType.HOM_DEL_DISRUPTION;
import static com.hartwig.hmftools.linx.drivers.DriverEventType.HOM_DUP_DISRUPTION;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.addGeneData;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.addTransExonData;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.createEnsemblGeneData;
import static com.hartwig.hmftools.linx.utils.GeneTestUtils.createTransExons;
import static com.hartwig.hmftools.linx.utils.SvTestUtils.createDel;
import static com.hartwig.hmftools.linx.utils.SvTestUtils.createDup;
import static com.hartwig.hmftools.linx.utils.SvTestUtils.createInv;

import static org.junit.Assert.assertEquals;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.variant.structural.annotation.EnsemblGeneData;
import com.hartwig.hmftools.common.variant.structural.annotation.TranscriptData;
import com.hartwig.hmftools.linx.gene.SvGeneTranscriptCollection;
import com.hartwig.hmftools.linx.types.SvCluster;
import com.hartwig.hmftools.linx.types.SvVarData;
import com.hartwig.hmftools.linx.utils.LinxTester;

import org.junit.Ignore;
import org.junit.Test;

public class HomDisruptionsTest
{
    @Test
    public void testDelDisruption()
    {
        LinxTester tester = new LinxTester();
        tester.logVerbose(true);

        tester.setNonClusterAllelePloidies(0.2, 0);

        SvGeneTranscriptCollection geneTransCache = new SvGeneTranscriptCollection();
        tester.initialiseFusions(geneTransCache);

        // must be a known TSG driver
        String geneName = "TP53";
        String geneId = "ENSG00000141510";
        String chromosome = "17";
        byte strand = 1;

        long transStart = 10000;
        long transEnd = 55000;

        List<EnsemblGeneData> geneList = Lists.newArrayList();
        geneList.add(createEnsemblGeneData(geneId, geneName, chromosome, strand, transStart, transEnd));

        List<TranscriptData> transDataList = Lists.newArrayList();

        int transId = 1;
        long[] exonStarts = new long[] { 10000, 20000, 30000, 40000, 50000};
        int[] exonPhases = new int[] {-1, 0, 0, 0, -1};

        TranscriptData transData = createTransExons(geneId, transId++, strand, exonStarts, exonPhases, 100, true);
        transDataList.add(transData);

        addTransExonData(geneTransCache, geneId, transDataList);
        addGeneData(geneTransCache, chromosome, geneList);

        DriverGeneAnnotator driverAnnotator = new DriverGeneAnnotator(null, geneTransCache, tester.Config, tester.CnDataLoader);
        driverAnnotator.setSamplePloidy(2);

        // make a chain of SVs which contain one or more deletion bridges affecting the gene
        SvVarData var1 = createInv(tester.nextVarId(), chromosome, 15000, 25000, 1);
        SvVarData var2 = createDup(tester.nextVarId(), chromosome, 15100, 35000);
        SvVarData var3 = createInv(tester.nextVarId(), chromosome, 25100, 35100, -1);

        tester.AllVariants.add(var1);
        tester.AllVariants.add(var2);
        tester.AllVariants.add(var3);

        tester.preClusteringInit();
        tester.Analyser.clusterAndAnalyse();

        geneTransCache.setSvGeneData(tester.AllVariants, false, false);
        tester.FusionAnalyser.annotateTranscripts(tester.AllVariants, false);

        driverAnnotator.annotateSVs(tester.SampleId, tester.Analyser.getState().getChrBreakendMap());

        assertEquals(1, tester.Analyser.getClusters().size());
        SvCluster cluster = tester.Analyser.getClusters().get(0);

        assertEquals(1, driverAnnotator.getDriverGeneDataList().size());
        DriverGeneData dgData = driverAnnotator.getDriverGeneDataList().get(0);

        assertEquals(1, dgData.getEvents().size());
        assertEquals(cluster, dgData.getEvents().get(0).getCluster());
        assertEquals(HOM_DEL_DISRUPTION, dgData.getEvents().get(0).Type);

        tester.clearClustersAndSVs();

        // add an unrelated DEL to set the correct copy number running out to telomere
        SvVarData del = createDel(tester.nextVarId(), chromosome, 400, 500);

        var1 = createInv(tester.nextVarId(), chromosome, 15000, 25000, 1);
        var2 = createInv(tester.nextVarId(), chromosome, 14990, 24990, -1);

        tester.AllVariants.add(del);
        tester.AllVariants.add(var1);
        tester.AllVariants.add(var2);

        tester.preClusteringInit();
        tester.Analyser.clusterAndAnalyse();

        geneTransCache.setSvGeneData(tester.AllVariants, false, false);
        tester.FusionAnalyser.annotateTranscripts(tester.AllVariants, false);

        driverAnnotator.annotateSVs(tester.SampleId, tester.Analyser.getState().getChrBreakendMap());

        assertEquals(2, tester.Analyser.getClusters().size());
        cluster = tester.findClusterWithSVs(Lists.newArrayList(var1, var2));

        assertEquals(1, driverAnnotator.getDriverGeneDataList().size());
        dgData = driverAnnotator.getDriverGeneDataList().get(0);

        assertEquals(1, dgData.getEvents().size());
        assertEquals(cluster, dgData.getEvents().get(0).getCluster());
        assertEquals(HOM_DEL_DISRUPTION, dgData.getEvents().get(0).Type);
    }

    @Test
    public void testDupDisruption()
    {
        LinxTester tester = new LinxTester();
        tester.logVerbose(true);

        tester.setNonClusterAllelePloidies(0.2, 0);

        SvGeneTranscriptCollection geneTransCache = new SvGeneTranscriptCollection();
        tester.initialiseFusions(geneTransCache);

        // must be a known TSG driver
        String geneName = "TP53";
        String geneId = "ENSG00000141510";
        String chromosome = "17";
        byte strand = 1;

        long transStart = 10000;
        long transEnd = 55000;

        List<EnsemblGeneData> geneList = Lists.newArrayList();
        geneList.add(createEnsemblGeneData(geneId, geneName, chromosome, strand, transStart, transEnd));

        List<TranscriptData> transDataList = Lists.newArrayList();

        int transId = 1;
        long[] exonStarts = new long[] { 10000, 20000, 30000, 40000, 50000};
        int[] exonPhases = new int[] {-1, 0, 0, 0, -1};

        TranscriptData transData = createTransExons(geneId, transId++, strand, exonStarts, exonPhases, 100, true);
        transDataList.add(transData);

        addTransExonData(geneTransCache, geneId, transDataList);
        addGeneData(geneTransCache, chromosome, geneList);

        DriverGeneAnnotator driverAnnotator = new DriverGeneAnnotator(null, geneTransCache, tester.Config, tester.CnDataLoader);
        driverAnnotator.setSamplePloidy(2);

        // the DUP must be disruptive, ie duplicating at least 1 exon
        SvVarData var1 = createDup(tester.nextVarId(), chromosome, 15000, 35000);

        tester.AllVariants.add(var1);

        tester.preClusteringInit();
        tester.Analyser.clusterAndAnalyse();

        geneTransCache.setSvGeneData(tester.AllVariants, false, false);
        tester.FusionAnalyser.annotateTranscripts(tester.AllVariants, false);

        driverAnnotator.annotateSVs(tester.SampleId, tester.Analyser.getState().getChrBreakendMap());

        assertEquals(1, tester.Analyser.getClusters().size());
        SvCluster cluster = tester.Analyser.getClusters().get(0);

        assertEquals(1, driverAnnotator.getDriverGeneDataList().size());
        DriverGeneData dgData = driverAnnotator.getDriverGeneDataList().get(0);

        assertEquals(1, dgData.getEvents().size());
        assertEquals(cluster, dgData.getEvents().get(0).getCluster());
        assertEquals(HOM_DUP_DISRUPTION, dgData.getEvents().get(0).Type);
    }

}
