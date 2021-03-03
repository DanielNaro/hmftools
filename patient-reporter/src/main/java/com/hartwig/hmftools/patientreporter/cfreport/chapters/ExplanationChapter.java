package com.hartwig.hmftools.patientreporter.cfreport.chapters;

import com.hartwig.hmftools.patientreporter.cfreport.ReportResources;
import com.hartwig.hmftools.patientreporter.cfreport.components.TableUtil;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.UnitValue;

import org.jetbrains.annotations.NotNull;

public class ExplanationChapter implements ReportChapter {

    public ExplanationChapter() { }

    @NotNull
    @Override
    public String name() {
        return "Report explanation";
    }

    @Override
    public void render(@NotNull Document reportDocument) {
        Table table = new Table(UnitValue.createPercentArray(new float[] { 10, 1, 10, 1, 10, }));
        table.setWidth(contentWidth());

        table.addCell(TableUtil.createLayoutCell().add(createSectionTitle("Details on the report in general")));
        table.addCell(TableUtil.createLayoutCell());
        table.addCell(TableUtil.createLayoutCell().add(createSectionTitle("Details on the reported clinical evidence")));
        table.addCell(TableUtil.createLayoutCell());
        table.addCell(TableUtil.createLayoutCell().add(createSectionTitle("Details on reported somatic variants")));

        table.addCell(TableUtil.createLayoutCell()
                .add(createContentDiv(new String[] { "The analysis is based on reference genome version GRCh37.",
                        "Transcripts used for reporting can be found on https://resources.hartwigmedicalfoundation.nl in directory "
                                + "Patient-Reporting and are generally the canonical transcripts as defined by Ensembl.",
                        "Variant detection in samples with lower tumor content is less sensitive. In case of a low tumor "
                                + "purity (below 20%) likelihood of failing to detect potential variants increases.",
                        "The (implied) tumor purity is the percentage of tumor cells in the tumor material based on analysis of "
                                + "whole genome data." })));
        table.addCell(TableUtil.createLayoutCell());
        table.addCell(TableUtil.createLayoutCell()
                .add(createContentDiv(new String[] {
                        "The CGI, OncoKB and CIViC knowledgebases are used to annotate variants of all types with "
                                + "clinical evidence, with a hyperlink to the specific evidence items. NOTE: If a certain "
                                + "evidence item or drug-biomarker is missing from the knowledgebases it will also not be "
                                + "included in this report.",
                        "More information on (CGI) biomarkers can be found on https://www.cancergenomeinterpreter.org/biomarkers",
                        "Clinical trials are matched against the iClusion database (https://iclusion.org) including a "
                                + "link to the specific trial." })));
        table.addCell(TableUtil.createLayoutCell());
        table.addCell(TableUtil.createLayoutCell()
                .add(createContentDiv(new String[] {
                        "The 'Read Depth' displays the raw number of reads supporting the variant versus the total "
                                + "number of reads on the mutated position.",
                        "The 'Copies' field indicates the number of alleles present in the tumor on this particular mutated position.",
                        "The 'tVAF' field displays the variant allele frequency corrected for tumor purity",
                        "The 'Biallelic' field indicates whether the variant is present across all alleles in the tumor "
                                + "(and is including variants with loss-of-heterozygosity).",
                        "The 'Driver' field is based on the driver probability calculated based on the HMF database. A "
                                + "variant in a gene with High driver likelihood is likely to be positively selected for "
                                + "during the oncogenic process." })));

        table.addCell(TableUtil.createLayoutCell(1, 5).setHeight(30));

        table.addCell(TableUtil.createLayoutCell().add(createSectionTitle("Details on reported gene copy numbers")));
        table.addCell(TableUtil.createLayoutCell());
        table.addCell(TableUtil.createLayoutCell().add(createSectionTitle("Details on reported gene fusions")));
        table.addCell(TableUtil.createLayoutCell());
        table.addCell(TableUtil.createLayoutCell().add(createSectionTitle("Details on reported gene disruptions")));

        table.addCell(TableUtil.createLayoutCell()
                .add(createContentDiv(new String[] { "The lowest copy number value along the exonic regions of the canonical transcript is "
                        + "determined as a measure for the gene's copy number.",
                        "Copy numbers are corrected for the implied tumor purity and represent the number of copies in the tumor DNA.",
                        "Any gene with less than 0.5 copies along the entire canonical transcript is reported as a full loss.",
                        "Any gene where only a part along the canonical transcript has less than 0.5 copies is reported "
                                + "as a partial loss.",
                        "Any gene with more copies than 3 times the average tumor ploidy along the entire canonical transcript is reported "
                                + "as a full gain.",
                        "Any gene where only a part of the canonical transcript has more copies than 3 times the average tumor ploidy "
                                + "is reported as a partial gain",
                })));
        table.addCell(TableUtil.createLayoutCell());
        table.addCell(TableUtil.createLayoutCell()
                .add(createContentDiv(new String[] { "The canonical, or otherwise longest transcript validly fused is reported.",
                        "Fusions are restricted to those in the HMF known fusion list and can be found on "
                                + "https://resources.hartwigmedicalfoundation.nl in directory Patient-Reporting",
                        "We additionally select fusions where one partner is promiscuous in either 5' or 3' position.",
                "The 'Driver' field is set to HIGH in case the fusion is a known pathogenic fusion, or otherwise a fusion where "
                        + "the promiscuous partner is fused in an exon range that is typically observed in literature. \n"
                        + "All other fusions get assigned a LOW driver likelihood."})));
        table.addCell(TableUtil.createLayoutCell());
        table.addCell(TableUtil.createLayoutCell()
                .add(createContentDiv(new String[] {
                        "Genes are reported as being disrupted if their canonical transcript has been disrupted",
                        "The range of the disruption is indicated by the intron/exon/promoter region of the break point "
                                + "and the direction the disruption faces.",
                        "The type of disruption can be INV (inversion), DEL (deletion), DUP (duplication), INS "
                                + "(insertion), SGL (single) or BND (translocation).",
                        "A gene for which no wild type exists anymore in the tumor DNA due to disruption(s) "
                                + "is reported in a separate section called 'homozygous disruptions'" })));

        table.addCell(TableUtil.createLayoutCell(1, 5).setHeight(30));

        table.addCell(TableUtil.createLayoutCell().add(createSectionTitle("Details on reported viral insertions")));
        table.addCell(TableUtil.createLayoutCell());
        table.addCell(TableUtil.createLayoutCell().add(createSectionTitle("")));
        table.addCell(TableUtil.createLayoutCell());
        table.addCell(TableUtil.createLayoutCell().add(createSectionTitle("")));

        table.addCell(TableUtil.createLayoutCell()
                .add(createContentDiv(new String[] { "Currently only integrated virus DNA can be detected.",
                        "The list of viruses that are considered can be found on https://resources.hartwigmedicalfoundation.nl."})));
        table.addCell(TableUtil.createLayoutCell());
        table.addCell(TableUtil.createLayoutCell().add(createContentDiv(new String[] { "" })));
        table.addCell(TableUtil.createLayoutCell());
        table.addCell(TableUtil.createLayoutCell().add(createContentDiv(new String[] { "" })));

        reportDocument.add(table);
    }

    @NotNull
    private static Paragraph createSectionTitle(@NotNull String sectionTitle) {
        return new Paragraph(sectionTitle).addStyle(ReportResources.smallBodyHeadingStyle());
    }

    @NotNull
    private static Div createContentDiv(@NotNull String[] contentParagraphs) {
        Div div = new Div();
        for (String s : contentParagraphs) {
            div.add(new Paragraph(s).addStyle(ReportResources.smallBodyTextStyle()).setFixedLeading(ReportResources.BODY_TEXT_LEADING));
        }
        return div;
    }
}
