package com.hartwig.hmftools.orange;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.hartwig.hmftools.common.chord.ChordTestFactory;
import com.hartwig.hmftools.common.doid.DoidTestFactory;
import com.hartwig.hmftools.common.flagstat.FlagstatTestFactory;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.hla.ImmutableLilacSummaryData;
import com.hartwig.hmftools.common.hla.LilacAllele;
import com.hartwig.hmftools.common.hla.LilacSummaryData;
import com.hartwig.hmftools.common.isofox.IsofoxTestFactory;
import com.hartwig.hmftools.common.lilac.LilacTestFactory;
import com.hartwig.hmftools.common.linx.LinxFusion;
import com.hartwig.hmftools.common.linx.LinxTestFactory;
import com.hartwig.hmftools.common.metrics.WGSMetricsTestFactory;
import com.hartwig.hmftools.common.peach.PeachGenotype;
import com.hartwig.hmftools.common.peach.PeachTestFactory;
import com.hartwig.hmftools.common.rna.AltSpliceJunctionContext;
import com.hartwig.hmftools.common.rna.AltSpliceJunctionType;
import com.hartwig.hmftools.common.rna.GeneExpression;
import com.hartwig.hmftools.common.rna.NovelSpliceJunction;
import com.hartwig.hmftools.common.rna.RnaFusion;
import com.hartwig.hmftools.common.rna.RnaStatistics;
import com.hartwig.hmftools.common.sv.StructuralVariantType;
import com.hartwig.hmftools.common.virus.AnnotatedVirus;
import com.hartwig.hmftools.common.virus.ImmutableAnnotatedVirus;
import com.hartwig.hmftools.common.virus.ImmutableVirusInterpreterData;
import com.hartwig.hmftools.common.virus.VirusBreakendQCStatus;
import com.hartwig.hmftools.common.virus.VirusInterpreterData;
import com.hartwig.hmftools.common.virus.VirusLikelihoodType;
import com.hartwig.hmftools.orange.algo.ImmutableOrangePlots;
import com.hartwig.hmftools.orange.algo.ImmutableOrangeReport;
import com.hartwig.hmftools.orange.algo.ImmutableOrangeSample;
import com.hartwig.hmftools.orange.algo.OrangePlots;
import com.hartwig.hmftools.orange.algo.OrangeReport;
import com.hartwig.hmftools.orange.algo.OrangeSample;
import com.hartwig.hmftools.orange.algo.cuppa.TestCuppaFactory;
import com.hartwig.hmftools.orange.algo.isofox.ImmutableIsofoxInterpretedData;
import com.hartwig.hmftools.orange.algo.isofox.IsofoxInterpretedData;
import com.hartwig.hmftools.orange.algo.linx.ImmutableLinxInterpretedData;
import com.hartwig.hmftools.orange.algo.linx.LinxInterpretedData;
import com.hartwig.hmftools.orange.algo.linx.TestLinxInterpretationFactory;
import com.hartwig.hmftools.orange.algo.purple.ImmutablePurpleInterpretedData;
import com.hartwig.hmftools.orange.algo.purple.PurpleInterpretedData;
import com.hartwig.hmftools.orange.algo.purple.TestPurpleInterpretationFactory;
import com.hartwig.hmftools.orange.algo.purple.TestPurpleVariantFactory;
import com.hartwig.hmftools.orange.algo.wildtype.TestWildTypeFactory;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

public final class TestOrangeReportFactory {

    private static final String TEST_SAMPLE = "TEST";
    private static final String DUMMY_IMAGE = Resources.getResource("test_images/white.png").getPath();

    private TestOrangeReportFactory() {
    }

    @NotNull
    public static ImmutableOrangeReport.Builder builder() {
        return ImmutableOrangeReport.builder()
                .sampleId(TEST_SAMPLE)
                .experimentDate(LocalDate.of(2021, 11, 19))
                .refGenomeVersion(RefGenomeVersion.V37)
                .tumorSample(createMinimalOrangeSample())
                .purple(TestPurpleInterpretationFactory.createMinimalTestPurpleData())
                .linx(TestLinxInterpretationFactory.createMinimalTestLinxData())
                .lilac(ImmutableLilacSummaryData.builder().qc(Strings.EMPTY).build())
                .virusInterpreter(ImmutableVirusInterpreterData.builder().build())
                .chord(ChordTestFactory.createMinimalTestChordAnalysis())
                .cuppa(TestCuppaFactory.createMinimalCuppaData())
                .plots(createMinimalOrangePlots());
    }

    @NotNull
    public static OrangeReport createMinimalTestReport() {
        return builder().build();
    }

    @NotNull
    public static OrangeReport createProperTestReport() {
        return builder().addConfiguredPrimaryTumor(DoidTestFactory.createDoidNode("1", "cancer type"))
                .platinumVersion("v5.30")
                .refSample(createMinimalOrangeSample())
                .germlineMVLHPerGene(createTestGermlineMVLHPerGene())
                .purple(createTestPurpleData())
                .linx(createTestLinxData())
                .addWildTypeGenes(TestWildTypeFactory.create("gene"))
                .isofox(createTestIsofoxData())
                .lilac(createTestLilacData())
                .virusInterpreter(createTestVirusInterpreterData())
                .peach(createTestPeachData())
                .build();
    }

    @NotNull
    private static OrangeSample createMinimalOrangeSample() {
        return ImmutableOrangeSample.builder()
                .metrics(WGSMetricsTestFactory.createMinimalTestWGSMetrics())
                .flagstat(FlagstatTestFactory.createMinimalTestFlagstat())
                .build();
    }

    @NotNull
    private static OrangePlots createMinimalOrangePlots() {
        return ImmutableOrangePlots.builder()
                .sageTumorBQRPlot(DUMMY_IMAGE)
                .purpleInputPlot(DUMMY_IMAGE)
                .purpleFinalCircosPlot(DUMMY_IMAGE)
                .purpleClonalityPlot(DUMMY_IMAGE)
                .purpleCopyNumberPlot(DUMMY_IMAGE)
                .purpleVariantCopyNumberPlot(DUMMY_IMAGE)
                .purplePurityRangePlot(DUMMY_IMAGE)
                .build();
    }

    @NotNull
    private static Map<String, Double> createTestGermlineMVLHPerGene() {
        Map<String, Double> germlineMVLHPerGene = Maps.newHashMap();
        germlineMVLHPerGene.put("gene", 0.01);
        return germlineMVLHPerGene;
    }

    @NotNull
    private static PurpleInterpretedData createTestPurpleData() {
        return ImmutablePurpleInterpretedData.builder()
                .from(TestPurpleInterpretationFactory.createMinimalTestPurpleData())
                .addReportableSomaticVariants(TestPurpleVariantFactory.builder()
                        .gene("ARID1A")
                        .canonicalImpact(TestPurpleVariantFactory.impactBuilder()
                                .hgvsCodingImpact("c.1920+9571_1920+9596delAGTGAACCGTTGACTAGAGTTTGGTT")
                                .build())
                        .build())
                .addReportableSomaticVariants(TestPurpleVariantFactory.builder()
                        .gene("USH2A")
                        .canonicalImpact(TestPurpleVariantFactory.impactBuilder()
                                .hgvsCodingImpact("c.8558+420_8558+442delCCGATACGATGAAAGAAAAGAGC")
                                .build())
                        .build())
                .addReportableSomaticVariants(TestPurpleVariantFactory.builder()
                        .gene("USH2A")
                        .canonicalImpact(TestPurpleVariantFactory.impactBuilder().hgvsCodingImpact("c.11712-884A>T").build())
                        .addLocalPhaseSets(42256)
                        .build())
                .allGermlineVariants(Lists.newArrayList())
                .reportableGermlineVariants(Lists.newArrayList())
                .additionalSuspectGermlineVariants(Lists.newArrayList())
                .allGermlineDeletions(Lists.newArrayList())
                .reportableGermlineDeletions(Lists.newArrayList())
                .build();
    }

    @NotNull
    private static LinxInterpretedData createTestLinxData() {
        LinxFusion fusion = LinxTestFactory.createMinimalTestFusion();
        return ImmutableLinxInterpretedData.builder()
                .from(TestLinxInterpretationFactory.createMinimalTestLinxData())
                .addReportableSomaticFusions(fusion)
                .addReportableSomaticFusions(fusion)
                .addReportableSomaticFusions(fusion)
                .addReportableSomaticFusions(fusion)
                .addReportableSomaticFusions(fusion)
                .addReportableSomaticFusions(fusion)
                .addReportableSomaticFusions(fusion)
                .addReportableSomaticFusions(fusion)
                .allGermlineStructuralVariants(Lists.newArrayList())
                .allGermlineBreakends(Lists.newArrayList())
                .allGermlineDisruptions(Lists.newArrayList())
                .reportableGermlineDisruptions(Lists.newArrayList())
                .build();
    }

    @NotNull
    private static LilacSummaryData createTestLilacData() {
        List<LilacAllele> alleles = Lists.newArrayList();
        alleles.add(LilacTestFactory.builder().allele("Allele 1").build());
        alleles.add(LilacTestFactory.builder().allele("Allele 2").somaticInframeIndel(1D).build());

        return ImmutableLilacSummaryData.builder().qc("PASS").alleles(alleles).build();
    }

    @NotNull
    private static IsofoxInterpretedData createTestIsofoxData() {
        RnaStatistics statistics = IsofoxTestFactory.rnaStatisticsBuilder().totalFragments(120000).duplicateFragments(60000).build();

        GeneExpression highExpression = IsofoxTestFactory.geneExpressionBuilder()
                .geneName("MYC")
                .tpm(126.27)
                .medianTpmCancer(41)
                .percentileCancer(0.91)
                .medianTpmCohort(37)
                .percentileCohort(0.93)
                .build();

        GeneExpression lowExpression = IsofoxTestFactory.geneExpressionBuilder()
                .geneName("CDKN2A")
                .tpm(5.34)
                .medianTpmCancer(18.32)
                .percentileCancer(0.04)
                .medianTpmCohort(16)
                .percentileCohort(0.07)
                .build();

        RnaFusion novelKnownFusion = IsofoxTestFactory.rnaFusionBuilder()
                .name("PTPRK_RSPO3")
                .chromosomeUp("6")
                .positionUp(128841405)
                .chromosomeDown("6")
                .positionDown(127469792)
                .svType(StructuralVariantType.INV)
                .junctionTypeUp("KNOWN")
                .junctionTypeDown("KNOWN")
                .depthUp(73)
                .depthDown(49)
                .splitFragments(8)
                .realignedFrags(0)
                .discordantFrags(1)
                .cohortFrequency(3)
                .build();

        RnaFusion novelPromiscuousFusion = IsofoxTestFactory.rnaFusionBuilder()
                .name("NAP1L4_BRAF")
                .chromosomeUp("11")
                .positionUp(2972480)
                .chromosomeDown("7")
                .positionDown(140487380)
                .svType(StructuralVariantType.BND)
                .junctionTypeUp("KNOWN")
                .junctionTypeDown("KNOWN")
                .depthUp(9)
                .depthDown(19)
                .splitFragments(5)
                .realignedFrags(2)
                .discordantFrags(3)
                .cohortFrequency(1)
                .build();

        NovelSpliceJunction novelSkippedExon = IsofoxTestFactory.novelSpliceJunctionBuilder()
                .chromosome("1")
                .geneName("ALK")
                .junctionStart(50403003)
                .junctionEnd(60403003)
                .type(AltSpliceJunctionType.SKIPPED_EXONS)
                .depthStart(12)
                .depthEnd(14)
                .regionStart(AltSpliceJunctionContext.SPLICE_JUNC)
                .regionEnd(AltSpliceJunctionContext.SPLICE_JUNC)
                .fragmentCount(5)
                .cohortFrequency(3)
                .build();

        NovelSpliceJunction novelIntron = IsofoxTestFactory.novelSpliceJunctionBuilder()
                .chromosome("1")
                .geneName("ALK")
                .junctionStart(50403003)
                .junctionEnd(60403003)
                .type(AltSpliceJunctionType.NOVEL_INTRON)
                .depthStart(24)
                .depthEnd(12)
                .regionStart(AltSpliceJunctionContext.EXONIC)
                .regionEnd(AltSpliceJunctionContext.EXONIC)
                .fragmentCount(43)
                .cohortFrequency(12)
                .build();

        return ImmutableIsofoxInterpretedData.builder()
                .summary(statistics)
                .addAllAllGeneExpressions(Lists.newArrayList(highExpression, lowExpression))
                .addReportableHighExpression(highExpression)
                .addReportableLowExpression(lowExpression)
                .addAllAllFusions(Lists.newArrayList(novelKnownFusion, novelPromiscuousFusion))
                .addReportableNovelKnownFusions(novelKnownFusion)
                .addReportableNovelPromiscuousFusions(novelPromiscuousFusion)
                .addAllAllNovelSpliceJunctions(Lists.newArrayList(novelSkippedExon, novelIntron))
                .addReportableSkippedExons(novelSkippedExon)
                .addReportableNovelExonsIntrons(novelIntron)
                .build();
    }

    @NotNull
    private static VirusInterpreterData createTestVirusInterpreterData() {
        List<AnnotatedVirus> reportableViruses = Lists.newArrayList();

        reportableViruses.add(ImmutableAnnotatedVirus.builder()
                .taxid(1)
                .name("virus A")
                .qcStatus(VirusBreakendQCStatus.NO_ABNORMALITIES)
                .integrations(3)
                .interpretation("BAD ONE")
                .percentageCovered(87D)
                .meanCoverage(42D)
                .expectedClonalCoverage(3D)
                .reported(true)
                .virusDriverLikelihoodType(VirusLikelihoodType.UNKNOWN)
                .build());

        return ImmutableVirusInterpreterData.builder().reportableViruses(reportableViruses).build();
    }

    @NotNull
    private static List<PeachGenotype> createTestPeachData() {
        List<PeachGenotype> genotypes = Lists.newArrayList();
        genotypes.add(PeachTestFactory.builder().gene("DPYD").haplotype("haplotype").build());
        return genotypes;
    }
}
