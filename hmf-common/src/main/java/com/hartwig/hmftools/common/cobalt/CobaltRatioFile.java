package com.hartwig.hmftools.common.cobalt;

import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.checkAddDirSeparator;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.createGzipBufferedWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.hartwig.hmftools.common.genome.chromosome.Chromosome;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.purple.Gender;
import com.hartwig.hmftools.common.utils.file.DelimFileReader;
import com.hartwig.hmftools.common.utils.file.DelimFileWriter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CobaltRatioFile
{
    enum Column
    {
        chromosome,
        position,
        referenceReadDepth,
        tumorReadDepth,
        referenceGCRatio,
        tumorGCRatio,
        referenceGCDiploidRatio,
        referenceGCContent,
        tumorGCContent
    }

    private static final DecimalFormat FORMAT = new DecimalFormat("#.####", new DecimalFormatSymbols(Locale.ENGLISH));

    private static final String EXTENSION = ".cobalt.ratio.tsv.gz";

    @Deprecated
    public static final String TUMOR_ONLY_REFERENCE_SAMPLE = "DIPLOID";

    @NotNull
    public static String generateFilename(final String basePath, final String sample)
    {
        return checkAddDirSeparator(basePath) + sample + EXTENSION;
    }

    @NotNull
    public static String generateFilenameForReading(final String basePath, final String sample)
    {
        return generateFilename(basePath, sample);
    }

    public static ListMultimap<Chromosome,CobaltRatio> read(final String filename) throws IOException
    {
        Map<Chromosome,List<CobaltRatio>> chrRatiosMap = read(filename, null, true);

        final ListMultimap<Chromosome,CobaltRatio> result = ArrayListMultimap.create();

        for(Map.Entry<Chromosome,List<CobaltRatio>> entry : chrRatiosMap.entrySet())
        {
            HumanChromosome chromosome = HumanChromosome.fromString(entry.getKey().toString());
            entry.getValue().forEach(x -> result.put(chromosome, x));
        }

        return result;
    }

    public static Map<Chromosome,List<CobaltRatio>> readWithGender(final String filename, final Gender gender, boolean hasTumor)
            throws IOException
    {
        return read(filename, gender, hasTumor);
    }

    private static Map<Chromosome,List<CobaltRatio>> read(final String filename, final Gender gender, boolean hasTumor)
    {
        Map<Chromosome,List<CobaltRatio>> chrRatiosMap = new HashMap<>();

        try(DelimFileReader reader = new DelimFileReader(filename))
        {
            List<CobaltRatio> ratios = null;
            String currentChromosome = null;

            for(DelimFileReader.Row row : reader)
            {
                String chromosome = row.get(Column.chromosome);

                if(currentChromosome == null || !currentChromosome.equals(chromosome))
                {
                    currentChromosome = chromosome;
                    ratios = new ArrayList<>();
                    chrRatiosMap.put(HumanChromosome.fromString(chromosome), ratios);
                }
                else
                {
                    // use the same String object for better performance
                    chromosome = currentChromosome;
                }

                double refReadDepth = row.getDouble(Column.referenceReadDepth);

                double initialRefGCRatio = row.getDouble(Column.referenceGCRatio);
                double initialRefGCDiploidRatio = row.getDouble(Column.referenceGCDiploidRatio);

                if(refReadDepth == -1)
                {
                    // revert to a default ref ratio where no information is available (ie in tumor/panel only)
                    initialRefGCRatio = 1;
                    initialRefGCDiploidRatio = 1;
                }

                double refGcRatio = genderAdjustedDiploidRatio(gender, chromosome, initialRefGCRatio);
                double refGcDiploadRatio = genderAdjustedDiploidRatio(gender, chromosome, initialRefGCDiploidRatio);
                double tumorGCRatio = hasTumor ? row.getDouble(Column.tumorGCRatio) : refGcDiploadRatio;
                double tumorReadDepth = row.getDouble(Column.tumorReadDepth);
                double refGcPercent = row.getDouble(Column.referenceGCContent);
                double tumorGcPercent = row.getDouble(Column.tumorGCContent);

                CobaltRatio ratio = ImmutableCobaltRatio.builder()
                        .chromosome(chromosome)
                        .position(row.getInt(Column.position))
                        .referenceReadDepth(refReadDepth)
                        .tumorReadDepth(tumorReadDepth)
                        .tumorGCRatio(tumorGCRatio)
                        .referenceGCRatio(refGcRatio)
                        .referenceGCDiploidRatio(refGcDiploadRatio)
                        .referenceGcContent(refGcPercent)
                        .tumorGcContent(tumorGcPercent)
                        .build();

                ratios.add(ratio);
            }
        }

        return chrRatiosMap;
    }

    private static double genderAdjustedDiploidRatio(@Nullable final Gender gender, final String contig, double initialRatio)
    {
        if(gender == null || !HumanChromosome.contains(contig))
        {
            return initialRatio;
        }

        HumanChromosome chromosome = HumanChromosome.fromString(contig);
        if(chromosome.equals(HumanChromosome._X))
        {
            return gender.equals(Gender.FEMALE) ? 1 : 0.5;
        }

        if(chromosome.equals(HumanChromosome._Y))
        {
            return gender.equals(Gender.FEMALE) ? 0 : 0.5;
        }

        return initialRatio;
    }
    public static void write(final String fileName, Collection<CobaltRatio> ratios) throws IOException
    {
        List<CobaltRatio> sorted = new ArrayList<>(ratios);
        Collections.sort(sorted);
        DelimFileWriter delim = new DelimFileWriter();
        try(BufferedWriter writer = createGzipBufferedWriter(fileName))
        {
            delim.write(writer, Column.values(), sorted,
                (ratio, row) -> {
                    row.set(Column.chromosome, ratio.chromosome());
                    row.set(Column.position, ratio.position());
                    row.set(Column.referenceReadDepth, ratio.referenceReadDepth(), FORMAT);
                    row.set(Column.tumorReadDepth, ratio.tumorReadDepth(), FORMAT);
                    row.set(Column.referenceGCRatio, ratio.referenceGCRatio(), FORMAT);
                    row.set(Column.tumorGCRatio, ratio.tumorGCRatio(), FORMAT);
                    row.set(Column.referenceGCDiploidRatio, ratio.referenceGCDiploidRatio(), FORMAT);
                    row.set(Column.referenceGCContent, ratio.referenceGcContent(), FORMAT);
                    row.set(Column.tumorGCContent, ratio.tumorGcContent(), FORMAT);
                });
        }
    }
}
