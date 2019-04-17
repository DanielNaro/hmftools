package com.hartwig.hmftools.patientreporter.variants;

import static com.hartwig.hmftools.patientreporter.PatientReporterTestUtil.testSequencedReportData;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.drivercatalog.DriverCategory;
import com.hartwig.hmftools.common.variant.CodingEffect;
import com.hartwig.hmftools.common.variant.EnrichedSomaticVariant;
import com.hartwig.hmftools.common.variant.ImmutableEnrichedSomaticVariant;
import com.hartwig.hmftools.common.variant.SomaticVariantTestBuilderFactory;
import com.hartwig.hmftools.patientreporter.SequencedReportData;
import com.hartwig.hmftools.patientreporter.germline.GermlineVariant;
import com.hartwig.hmftools.patientreporter.germline.ImmutableGermlineVariant;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;

public class SomaticVariantAnalyzerTest {

    private static final String PASS_FILTER = "PASS";

    private static final CodingEffect SPLICE = CodingEffect.SPLICE;
    private static final CodingEffect MISSENSE = CodingEffect.MISSENSE;
    private static final CodingEffect SYNONYMOUS = CodingEffect.SYNONYMOUS;
    private static final String RIGHT_GENE = "RIGHT";
    private static final String WRONG_GENE = "WRONG";

    @Test
    public void onlyReportsAndCountsRelevantVariants() {
        List<EnrichedSomaticVariant> variants =
                Lists.newArrayList(builder().gene(RIGHT_GENE).canonicalCodingEffect(MISSENSE).worstCodingEffect(MISSENSE).build(),
                        builder().gene(RIGHT_GENE).canonicalCodingEffect(SYNONYMOUS).worstCodingEffect(SYNONYMOUS).build(),
                        builder().gene(RIGHT_GENE).canonicalCodingEffect(SPLICE).worstCodingEffect(SPLICE).build(),
                        builder().gene(WRONG_GENE).canonicalCodingEffect(MISSENSE).worstCodingEffect(MISSENSE).build(),
                        builder().gene(WRONG_GENE).canonicalCodingEffect(SYNONYMOUS).worstCodingEffect(SYNONYMOUS).build());

        List<GermlineVariant> germlineVariant = Lists.newArrayList(ImmutableGermlineVariant.builder()
                .passFilter(true)
                .gene("BRCA2")
                .hgvsCodingImpact("c.5946delT")
                .hgvsProteinImpact("p.Ser1982fs")
                .totalReadCount(112)
                .alleleReadCount(67)
                .germlineStatus("HET")
                .adjustedCopyNumber(3D)
                .adjustedVAF(1.0)
                .minorAllelePloidy(1D)
                .biallelic(true)
                .build());

        final SequencedReportData reporterData = testSequencedReportData();


        SomaticVariantAnalysis analysis = SomaticVariantAnalyzer.run(variants,
                Sets.newHashSet(RIGHT_GENE),
                Maps.newHashMap(),
                Sets.newHashSet(),
                testSequencedReportData().actionabilityAnalyzer(),
                null, germlineVariant, Sets.newHashSet(), reporterData.germlineGenesReporting(), Strings.EMPTY, Lists.newArrayList());

        assertEquals(2, analysis.tumorMutationalLoad());
        assertEquals(2, analysis.reportableSomaticVariants().size());

        Map<String, DriverCategory> driverCategoryMap = Maps.newHashMap();
        driverCategoryMap.put(RIGHT_GENE, DriverCategory.ONCO);
        SomaticVariantAnalysis analysisOnco = SomaticVariantAnalyzer.run(variants,
                Sets.newHashSet(RIGHT_GENE),
                driverCategoryMap,
                Sets.newHashSet(),
                testSequencedReportData().actionabilityAnalyzer(),
                null, germlineVariant, Sets.newHashSet(), reporterData.germlineGenesReporting(), Strings.EMPTY, Lists.newArrayList());

        assertEquals(2, analysisOnco.tumorMutationalLoad());
        assertEquals(1, analysisOnco.reportableSomaticVariants().size());
    }

    @NotNull
    private static ImmutableEnrichedSomaticVariant.Builder builder() {
        return SomaticVariantTestBuilderFactory.createEnriched().filter(PASS_FILTER);
    }
}
