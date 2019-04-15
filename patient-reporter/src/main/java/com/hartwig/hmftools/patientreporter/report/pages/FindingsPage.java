package com.hartwig.hmftools.patientreporter.report.pages;

import static com.hartwig.hmftools.patientreporter.report.Commons.HEADER_TO_TABLE_VERTICAL_GAP;
import static com.hartwig.hmftools.patientreporter.report.Commons.SECTION_VERTICAL_GAP;
import static com.hartwig.hmftools.patientreporter.report.Commons.fontStyle;
import static com.hartwig.hmftools.patientreporter.report.Commons.linkStyle;
import static com.hartwig.hmftools.patientreporter.report.Commons.monospaceBaseTable;
import static com.hartwig.hmftools.patientreporter.report.Commons.sectionHeaderStyle;

import static net.sf.dynamicreports.report.builder.DynamicReports.cmp;
import static net.sf.dynamicreports.report.builder.DynamicReports.col;
import static net.sf.dynamicreports.report.builder.DynamicReports.hyperLink;

import com.hartwig.hmftools.patientreporter.AnalysedPatientReport;
import com.hartwig.hmftools.patientreporter.report.components.ChordSection;
import com.hartwig.hmftools.patientreporter.report.components.MicrosatelliteSection;
import com.hartwig.hmftools.patientreporter.report.components.MutationalBurdenSection;
import com.hartwig.hmftools.patientreporter.report.components.MutationalLoadSection;
import com.hartwig.hmftools.patientreporter.report.data.GeneCopyNumberDataSource;
import com.hartwig.hmftools.patientreporter.report.data.GeneDisruptionDataSource;
import com.hartwig.hmftools.patientreporter.report.data.GeneFusionDataSource;
import com.hartwig.hmftools.patientreporter.report.data.SomaticVariantDataSource;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;

import net.sf.dynamicreports.report.builder.component.ComponentBuilder;
import net.sf.dynamicreports.report.constant.HorizontalTextAlignment;

@Value.Immutable
@Value.Style(passAnnotations = NotNull.class,
             allParameters = true)
public abstract class FindingsPage {

    @NotNull
    abstract AnalysedPatientReport report();

    @NotNull
    public ComponentBuilder<?, ?> reportComponent() {
        return cmp.verticalList(somaticVariantReport(report()),
                cmp.verticalGap(SECTION_VERTICAL_GAP),
                geneCopyNumberReport(report()),
                cmp.verticalGap(SECTION_VERTICAL_GAP),
                geneFusionReport(report()),
                cmp.verticalGap(SECTION_VERTICAL_GAP),
                geneDisruptionReport(report()),
                cmp.verticalGap(SECTION_VERTICAL_GAP),
                chordReport(report()),
                cmp.verticalGap(SECTION_VERTICAL_GAP),
                microsatelliteReport(report()),
                cmp.verticalGap(SECTION_VERTICAL_GAP),
                tumorMutationalLoadReport(report()),
                cmp.verticalGap(SECTION_VERTICAL_GAP),
                tumorMutationalBurdenReport(report()));
    }

    @NotNull
    private static ComponentBuilder<?, ?> somaticVariantReport(@NotNull AnalysedPatientReport report) {
        final String drupEligibilityAddition = "Marked genes (*) are included in the DRUP study and indicate potential "
                + "eligibility in DRUP. Please note that the marking is NOT based on the specific mutation reported for "
                + "this sample, but only on a gene-level.";
        final String germline = "Marked variant(s) (#) are also present in the germline of the patient. Referral to a genetic specialist "
                + "should be advised.";
        final String geneticus = " Marked genes (+) are notify to clinical geneticus";

        final ComponentBuilder<?, ?> table =
                !report.somaticVariants().isEmpty()
                        ? cmp.subreport(monospaceBaseTable().fields(SomaticVariantDataSource.fields())
                        .columns(col.column("Gene", SomaticVariantDataSource.GENE_FIELD),
                                col.column("Variant", SomaticVariantDataSource.VARIANT_FIELD).setFixedWidth(90),
                                col.column("Impact", SomaticVariantDataSource.IMPACT_FIELD).setFixedWidth(80),
                                col.column("Read Depth", SomaticVariantDataSource.READ_DEPTH_FIELD),
                                col.column("Hotspot", SomaticVariantDataSource.IS_HOTSPOT_FIELD),
                                col.column("Ploidy (VAF)", SomaticVariantDataSource.PLOIDY_VAF_FIELD).setFixedWidth(80),
                                col.column("Clonality", SomaticVariantDataSource.CLONAL_STATUS_FIELD),
                                col.column("Biallelic", SomaticVariantDataSource.BIALLELIC_FIELD),
                                col.column("Driver", SomaticVariantDataSource.DRIVER_FIELD)))
                        .setDataSource(SomaticVariantDataSource.fromVariants(report.somaticVariants(), report.hasReliablePurityFit()))
                        : cmp.text("None").setStyle(fontStyle().setHorizontalTextAlignment(HorizontalTextAlignment.CENTER));

        return cmp.verticalList(cmp.text("Somatic Variants").setStyle(sectionHeaderStyle()),
                cmp.verticalGap(HEADER_TO_TABLE_VERTICAL_GAP),
                table,
                cmp.verticalGap(15),
                cmp.horizontalList(cmp.horizontalGap(10),
                        cmp.text("*").setStyle(fontStyle()).setWidth(2),
                        cmp.text(drupEligibilityAddition).setStyle(fontStyle().setFontSize(8))),
                cmp.verticalGap(5),
                cmp.horizontalList(cmp.horizontalGap(10),
                        cmp.text("#").setStyle(fontStyle()).setWidth(2),
                        cmp.text(germline).setStyle(fontStyle().setFontSize(8))),
                cmp.verticalGap(5),
                cmp.horizontalList(cmp.horizontalGap(10),
                        cmp.text("+").setStyle(fontStyle()).setWidth(2),
                        cmp.text(geneticus).setStyle(fontStyle().setFontSize(8))));
    }

    @NotNull
    private static ComponentBuilder<?, ?> geneCopyNumberReport(@NotNull AnalysedPatientReport report) {
        final ComponentBuilder<?, ?> table =
                !report.geneCopyNumbers().isEmpty()
                        ? cmp.subreport(monospaceBaseTable().fields(GeneCopyNumberDataSource.copyNumberFields())
                        .columns(col.column("Chromosome", GeneCopyNumberDataSource.CHROMOSOME),
                                col.column("Chromosome band", GeneCopyNumberDataSource.CHROMOSOME_BAND),
                                col.column("Gene", GeneCopyNumberDataSource.GENE_FIELD),
                                col.column("Type", GeneCopyNumberDataSource.GAIN_OR_LOSS_FIELD),
                                col.column("Copies", GeneCopyNumberDataSource.COPY_NUMBER_FIELD))
                        .setDataSource(GeneCopyNumberDataSource.fromCopyNumbers(report.geneCopyNumbers(), report.hasReliablePurityFit())))
                        : cmp.text("None").setStyle(fontStyle().setHorizontalTextAlignment(HorizontalTextAlignment.CENTER));

        return cmp.verticalList(cmp.text("Somatic Gains & Losses").setStyle(sectionHeaderStyle()),
                cmp.verticalGap(HEADER_TO_TABLE_VERTICAL_GAP),
                table);
    }

    @NotNull
    private static ComponentBuilder<?, ?> geneFusionReport(@NotNull AnalysedPatientReport report) {
        final ComponentBuilder<?, ?> table =
                !report.geneFusions().isEmpty()
                        ? cmp.subreport(monospaceBaseTable().fields(GeneFusionDataSource.geneFusionFields())
                        .columns(col.column("Fusion", GeneFusionDataSource.FUSION_FIELD),
                                col.column("5' Transcript", GeneFusionDataSource.START_TRANSCRIPT_FIELD)
                                        .setHyperLink(hyperLink(GeneFusionDataSource.transcriptUrl(GeneFusionDataSource.START_TRANSCRIPT_FIELD)))
                                        .setStyle(linkStyle()),
                                col.column("3' Transcript", GeneFusionDataSource.END_TRANSCRIPT_FIELD)
                                        .setHyperLink(hyperLink(GeneFusionDataSource.transcriptUrl(GeneFusionDataSource.END_TRANSCRIPT_FIELD)))
                                        .setStyle(linkStyle()),
                                col.column("5' End", GeneFusionDataSource.START_CONTEXT_FIELD),
                                col.column("3' Start", GeneFusionDataSource.END_CONTEXT_FIELD),
                                col.column("Copies", GeneFusionDataSource.COPIES_FIELD),
                                col.column("Source", GeneFusionDataSource.SOURCE_FIELD)
                                        .setHyperLink(hyperLink(GeneFusionDataSource.sourceHyperlink()))
                                        .setStyle(linkStyle()))
                        .setDataSource(GeneFusionDataSource.fromGeneFusions(report.geneFusions(), report.hasReliablePurityFit())))
                        : cmp.text("None").setStyle(fontStyle().setHorizontalTextAlignment(HorizontalTextAlignment.CENTER));

        return cmp.verticalList(cmp.text("Somatic Gene Fusions").setStyle(sectionHeaderStyle()),
                cmp.verticalGap(HEADER_TO_TABLE_VERTICAL_GAP),
                table);
    }

    @NotNull
    private static ComponentBuilder<?, ?> chordReport(@NotNull AnalysedPatientReport report) {
        return ChordSection.build(report.chordAnalysis().hrdValue(), report.hasReliablePurityFit());
    }

    @NotNull
    private static ComponentBuilder<?, ?> microsatelliteReport(@NotNull AnalysedPatientReport report) {
        return MicrosatelliteSection.build(report.microsatelliteIndelsPerMb(), report.hasReliablePurityFit());
    }

    @NotNull
    private static ComponentBuilder<?, ?> tumorMutationalLoadReport(@NotNull AnalysedPatientReport report) {
        return MutationalLoadSection.build(report.tumorMutationalLoad(), report.hasReliablePurityFit());
    }

    @NotNull
    private static ComponentBuilder<?, ?> tumorMutationalBurdenReport(@NotNull AnalysedPatientReport report) {
        return MutationalBurdenSection.build(report.tumorMutationalBurden(), report.hasReliablePurityFit());
    }

    @NotNull
    private static ComponentBuilder<?, ?> geneDisruptionReport(@NotNull AnalysedPatientReport report) {
        final ComponentBuilder<?, ?> table = report.geneDisruptions().size() > 0
                ? cmp.subreport(monospaceBaseTable().fields(GeneDisruptionDataSource.geneDisruptionFields())
                .columns(col.column("Location", GeneDisruptionDataSource.LOCATION_FIELD),
                        col.column("Gene", GeneDisruptionDataSource.GENE_FIELD),
                        col.column("Disrupted Range", GeneDisruptionDataSource.RANGE_FIELD).setFixedWidth(120),
                        col.column("Type", GeneDisruptionDataSource.TYPE_FIELD),
                        col.column("Copies", GeneDisruptionDataSource.COPIES_FIELD),
                        col.column("Gene Min Copies", GeneDisruptionDataSource.GENE_MIN_COPIES).setFixedWidth(80),
                        col.column("Gene Max Copies", GeneDisruptionDataSource.GENE_MAX_COPIES).setFixedWidth(80))
                .setDataSource(GeneDisruptionDataSource.fromGeneDisruptions(report.geneDisruptions(), report.hasReliablePurityFit())))
                : cmp.text("None").setStyle(fontStyle().setHorizontalTextAlignment(HorizontalTextAlignment.CENTER));

        return cmp.verticalList(cmp.text("Somatic Gene Disruptions").setStyle(sectionHeaderStyle()),
                cmp.verticalGap(HEADER_TO_TABLE_VERTICAL_GAP),
                table);
    }
}
