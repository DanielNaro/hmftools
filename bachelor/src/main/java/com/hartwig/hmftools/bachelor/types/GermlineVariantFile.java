package com.hartwig.hmftools.bachelor.types;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.variant.CodingEffect;

import org.jetbrains.annotations.NotNull;

public class GermlineVariantFile
{
    private static final String DELIMITER = "\t";

    private static final String FILE_EXTENSION = ".bachelor.germline_variant.tsv";

    @NotNull
    public static String generateFilename(@NotNull final String basePath, @NotNull final String sample)
    {
        return basePath + File.separator + sample + FILE_EXTENSION;
    }

    @NotNull
    public static List<GermlineVariant> read(final String filePath) throws IOException
    {
        return fromLines(Files.readAllLines(new File(filePath).toPath()));
    }

    public static void write(@NotNull final String filename, @NotNull List<GermlineVariant> dataList) throws IOException
    {
        Files.write(new File(filename).toPath(), toLines(dataList));
    }

    @NotNull
    static List<String> toLines(@NotNull final List<GermlineVariant> dataList)
    {
        final List<String> lines = Lists.newArrayList();
        lines.add(header());
        dataList.stream().map(GermlineVariantFile::toString).forEach(lines::add);
        return lines;
    }

    @NotNull
    static List<GermlineVariant> fromLines(@NotNull List<String> lines)
    {
        return lines.stream()
                .skip(1)
                .map(GermlineVariantFile::fromString).collect(toList());
    }

    @NotNull
    private static String header() {
        return new StringJoiner(DELIMITER)
                .add("chromosome")
                .add("position")
                .add("filter")
                .add("type")
                .add("ref")
                .add("alts")
                .add("gene")
                .add("cosmicId")
                .add("dbsnpId")
                .add("transcriptId")
                .add("effects")
                .add("codingEffect")
                .add("microhomology")
                .add("repeatSequence")
                .add("repeatCount")
                .add("alleleReadCount")
                .add("totalReadCount")
                .add("adjustedVaf")
                .add("adjustedCopyNumber")
                .add("trinucleotideContext")
                .add("hgvsProtein")
                .add("hgvsCoding")
                .add("biallelic")
                .add("hotspot")
                .add("mappability")
                .add("minorAllelePloidy")
                .add("program")
                .add("variantId")
                .add("annotations")
                .add("phredScore")
                .add("isHomozygous")
                .add("matchType")
                .add("codonInfo")
                .add("clinvarMatch")
                .add("clinvarSignificance")
                .add("clinvarSignificanceInfo")
                .toString();
    }

    @NotNull
    public static String toString(@NotNull final GermlineVariant data)
    {
        return new StringJoiner(DELIMITER)
                .add(String.valueOf(data.chromosome()))
                .add(String.valueOf(data.position()))
                .add(String.valueOf(data.filter()))
                .add(String.valueOf(data.type()))
                .add(String.valueOf(data.ref()))
                .add(String.valueOf(data.alts()))
                .add(String.valueOf(data.gene()))
                .add(String.valueOf(data.cosmicId()))
                .add(String.valueOf(data.dbsnpId()))
                .add(String.valueOf(data.transcriptId()))
                .add(String.valueOf(data.effects()))
                .add(String.valueOf(data.codingEffect()))
                .add(String.valueOf(data.microhomology()))
                .add(String.valueOf(data.repeatSequence()))
                .add(String.valueOf(data.repeatCount()))
                .add(String.valueOf(data.alleleReadCount()))
                .add(String.valueOf(data.totalReadCount()))
                .add(String.valueOf(data.adjustedVaf()))
                .add(String.valueOf(data.adjustedCopyNumber()))
                .add(String.valueOf(data.trinucleotideContext()))
                .add(String.valueOf(data.hgvsProtein()))
                .add(String.valueOf(data.hgvsCoding()))
                .add(String.valueOf(data.biallelic()))
                .add(String.valueOf(data.hotspot()))
                .add(String.valueOf(data.mappability()))
                .add(String.valueOf(data.minorAllelePloidy()))
                .add(String.valueOf(data.program()))
                .add(String.valueOf(data.variantId()))
                .add(String.valueOf(data.annotations()))
                .add(String.valueOf(data.phredScore()))
                .add(String.valueOf(data.isHomozygous()))
                .add(String.valueOf(data.matchType()))
                .add(String.valueOf(data.codonInfo()))
                .add(String.valueOf(data.clinvarMatch()))
                .add(String.valueOf(data.clinvarSignificance()))
                .add(String.valueOf(data.clinvarSignificanceInfo()))
                .toString();
    }

    @NotNull
    private static GermlineVariant fromString(@NotNull final String data)
    {
        String[] values = data.split(DELIMITER);

        int index = 0;

        return ImmutableGermlineVariant.builder()
                .chromosome(values[index++])
                .position(Long.valueOf(values[index++]))
                .filter(values[index++])
                .type(values[index++])
                .ref(values[index++])
                .alts(values[index++])
                .gene(values[index++])
                .cosmicId(values[index++])
                .dbsnpId(values[index++])
                .transcriptId(values[index++])
                .effects(values[index++])
                .codingEffect(CodingEffect.valueOf(values[index++]))
                .microhomology(values[index++])
                .repeatSequence(values[index++])
                .repeatCount(Integer.valueOf(values[index++]))
                .alleleReadCount(Integer.valueOf(values[index++]))
                .totalReadCount(Integer.valueOf(values[index++]))
                .adjustedVaf(Double.valueOf(values[index++]))
                .adjustedCopyNumber(Double.valueOf(values[index++]))
                .trinucleotideContext(values[index++])
                .hgvsProtein(values[index++])
                .hgvsCoding(values[index++])
                .biallelic(Boolean.valueOf(values[index++]))
                .hotspot(Boolean.valueOf(values[index++]))
                .mappability(Double.valueOf(values[index++]))
                .minorAllelePloidy(Double.valueOf(values[index++]))
                .program(values[index++])
                .variantId(values[index++])
                .annotations(values[index++])
                .phredScore(Integer.valueOf(values[index++]))
                .isHomozygous(Boolean.valueOf(values[index++]))
                .matchType(values[index++])
                .codonInfo(values[index++])
                .clinvarMatch(Boolean.valueOf(values[index++]))
                .clinvarSignificance(index < values.length ? values[index++] : "")
                .clinvarSignificanceInfo(index < values.length ? values[index++] : "")
                .build();
    }
}
