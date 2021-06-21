package com.hartwig.hmftools.sage;

import static com.hartwig.hmftools.sage.SageCommon.SG_LOGGER;
import static com.hartwig.hmftools.sage.coverage.GeneCoverage.populateCoverageBuckets;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.hartwig.hmftools.common.genome.chromosome.Chromosome;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.genome.chromosome.MitochondrialChromosome;
import com.hartwig.hmftools.common.utils.version.VersionInfo;
import com.hartwig.hmftools.sage.config.SageConfig;
import com.hartwig.hmftools.sage.coverage.Coverage;
import com.hartwig.hmftools.sage.coverage.GeneDepthFile;
import com.hartwig.hmftools.sage.pipeline.ChromosomePipeline;
import com.hartwig.hmftools.sage.quality.QualityRecalibrationMap;
import com.hartwig.hmftools.sage.quality.QualityRecalibrationSupplier;
import com.hartwig.hmftools.sage.vcf.SageVCF;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;

public class SageApplication implements AutoCloseable
{
    private final SageVCF mVcfFile;
    private final SageConfig mConfig;
    private final ExecutorService mExecutorService;
    private final IndexedFastaSequenceFile mRefGenome;
    private final QualityRecalibrationSupplier mQualityRecalibrationSupplier;

    private final ReferenceData mRefData;

    private SageApplication(final Options options, final String... args) throws IOException, ParseException
    {
        final VersionInfo version = new VersionInfo("sage.version");
        SG_LOGGER.info("SAGE version: {}", version.version());

        final CommandLine cmd = createCommandLine(args, options);
        mConfig = new SageConfig(false, version.version(), cmd);

        if(!mConfig.isValid())
        {
            System.exit(1);
            SG_LOGGER.error("invalid config, exiting");
        }

        mRefData = new ReferenceData(mConfig);

        if(!mRefData.load())
        {
            System.exit(1);
            SG_LOGGER.error("invalid reference data, exiting");
        }

        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("SAGE-%d").build();
        mExecutorService = Executors.newFixedThreadPool(mConfig.Threads, namedThreadFactory);
        mRefGenome = new IndexedFastaSequenceFile(new File(mConfig.RefGenomeFile));
        mQualityRecalibrationSupplier = new QualityRecalibrationSupplier(mExecutorService, mRefGenome, mConfig);

        mVcfFile = new SageVCF(mRefGenome, mConfig);
        SG_LOGGER.info("Writing to file: {}", mConfig.OutputFile);
    }

    private void run() throws InterruptedException, ExecutionException, IOException
    {
        long timeStamp = System.currentTimeMillis();
        final Coverage coverage = createCoverage();

        final Map<String, QualityRecalibrationMap> recalibrationMap = mQualityRecalibrationSupplier.get();
        final SAMSequenceDictionary dictionary = dictionary();
        for(final SAMSequenceRecord samSequenceRecord : dictionary.getSequences())
        {
            final String contig = samSequenceRecord.getSequenceName();
            if(mConfig.Chromosomes.isEmpty() || mConfig.Chromosomes.contains(contig))
            {
                if(HumanChromosome.contains(contig) || MitochondrialChromosome.contains(contig))
                {
                    try(final ChromosomePipeline pipeline = createChromosomePipeline(contig, coverage, recalibrationMap))
                    {
                        pipeline.process();
                    }
                    System.gc();
                }
            }
        }

        for(String sample : coverage.samples())
        {
            String filename = mConfig.geneCoverageFile(sample);
            GeneDepthFile.write(filename, coverage.depth(sample));
        }

        long timeTaken = System.currentTimeMillis() - timeStamp;
        SG_LOGGER.info("Completed in {} seconds", timeTaken / 1000);
    }

    private SAMSequenceDictionary dictionary() throws IOException
    {
        final String bam = mConfig.ReferenceBams.isEmpty() ? mConfig.TumorBams.get(0) : mConfig.ReferenceBams.get(0);

        SamReader tumorReader = SamReaderFactory.makeDefault()
                .validationStringency(mConfig.Stringency)
                .referenceSource(new ReferenceSource(mRefGenome))
                .open(new File(bam));

        SAMSequenceDictionary dictionary = tumorReader.getFileHeader().getSequenceDictionary();

        tumorReader.close();

        return dictionary;
    }

    private ChromosomePipeline createChromosomePipeline(
            final String contig, @NotNull final Coverage coverage,
            Map<String, QualityRecalibrationMap> qualityRecalibrationMap) throws IOException
    {
        final Chromosome chromosome =
                HumanChromosome.contains(contig) ? HumanChromosome.fromString(contig) : MitochondrialChromosome.fromString(contig);

        return new ChromosomePipeline(
                contig, mConfig, mExecutorService, mRefData.Hotspots.get(chromosome), mRefData.PanelWithHotspots.get(chromosome),
                mRefData.HighConfidence.get(chromosome), qualityRecalibrationMap, coverage, mVcfFile::write);
    }

    private Coverage createCoverage()
    {
        populateCoverageBuckets();

        Set<String> samples = Sets.newHashSet();
        if(!mConfig.CoverageBed.isEmpty())
        {
            samples.addAll(mConfig.TumorIds);
        }

        return new Coverage(samples, mRefData.CoveragePanel.values());
    }

    @Override
    public void close() throws IOException
    {
        mVcfFile.close();
        mRefGenome.close();
        mExecutorService.shutdown();
    }

    public static void main(final String... args) throws IOException, InterruptedException, ExecutionException
    {
        final Options options = SageConfig.createSageOptions();

        try(final SageApplication application = new SageApplication(options, args))
        {
            application.run();
        }
        catch(ParseException e)
        {
            SG_LOGGER.warn(e);
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("SageApplication", options);
            System.exit(1);
        }
    }

    static CommandLine createCommandLine(@NotNull String[] args, @NotNull Options options) throws ParseException
    {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }
}