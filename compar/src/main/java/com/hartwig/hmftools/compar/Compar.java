package com.hartwig.hmftools.compar;

import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.compar.Category.DISRUPTION;
import static com.hartwig.hmftools.compar.Category.DRIVER;
import static com.hartwig.hmftools.compar.Category.FUSION;
import static com.hartwig.hmftools.compar.Category.LINX_DATA;
import static com.hartwig.hmftools.compar.ComparConfig.CMP_LOGGER;
import static com.hartwig.hmftools.compar.ComparConfig.LOG_DEBUG;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.compar.driver.DriverComparer;
import com.hartwig.hmftools.compar.linx.DisruptionComparer;
import com.hartwig.hmftools.compar.linx.FusionComparer;
import com.hartwig.hmftools.compar.linx.LinxSvComparer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;

public class Compar
{
    private final ComparConfig mConfig;

    private final List<ItemComparer> mComparators;

    private BufferedWriter mDiffWriter;

    public Compar(final CommandLine cmd)
    {
        mConfig = new ComparConfig(cmd);

        mComparators = Lists.newArrayList();

        if(mConfig.Categories.contains(DRIVER))
            mComparators.add(new DriverComparer(mConfig));

        if(mConfig.Categories.contains(LINX_DATA))
            mComparators.add(new LinxSvComparer(mConfig));

        if(mConfig.Categories.contains(FUSION))
            mComparators.add(new FusionComparer(mConfig));

        if(mConfig.Categories.contains(DISRUPTION))
            mComparators.add(new DisruptionComparer(mConfig));

        //        if(mConfig.runClassifier(SNV))
//            mClassifiers.add(new SomaticClassifier(mConfig, mSampleDataCache, cmd));
//
//        if(mConfig.runClassifier(FEATURE))
//            mClassifiers.add(new FeatureClassifier(mConfig, mSampleDataCache));
//
//        if(mConfig.runClassifier(SAMPLE_TRAIT))
//            mClassifiers.add(new SampleTraitClassifier(mConfig, mSampleDataCache));
//
//        if(mConfig.runClassifier(SV))
//            mClassifiers.add(new SvClassifier(mConfig, mSampleDataCache));
//
//        if(mConfig.runClassifier(GENE_EXP))
//            mClassifiers.add(new GeneExpressionClassifier(mConfig, mSampleDataCache, cmd));
//
//        if(mConfig.runClassifier(ALT_SJ))
//            mClassifiers.add(new AltSjClassifier(mConfig, mSampleDataCache, cmd));

        // CMP_LOGGER.debug("{} classifiers loaded", mClassifiers.size());

        mDiffWriter = null;
    }

    public void run()
    {
        if(!mConfig.isValid())
        {
            CMP_LOGGER.error("invalid config");
            return;
        }

        if(mConfig.SampleIds.isEmpty())
        {
            CMP_LOGGER.error("no samples specified");
            return;
        }

        if(mConfig.SampleIds.size() == 1)
        {
            CMP_LOGGER.info("running comparison for {}", mConfig.SampleIds.get(0));
        }
        else
        {
            CMP_LOGGER.info("running comparison for {} sample(s)", mConfig.SampleIds.size());
        }

        initialiseOutputFiles();

        int sampleCount = 0;
        for(int i = 0; i < mConfig.SampleIds.size(); ++i)
        {
            final String sampleId = mConfig.SampleIds.get(i);

            CMP_LOGGER.debug("sample({}) running comparison", sampleId);

            processSample(sampleId);

            if(i > 0 && (i % 100) == 0)
            {
                CMP_LOGGER.info("processed {} samples", sampleCount);
            }
        }

        closeBufferedWriter(mDiffWriter);

        CMP_LOGGER.info("comparison complete");
    }

    private void processSample(final String sampleId)
    {
        final List<Mismatch> mismatches = Lists.newArrayList();

        for(ItemComparer comparator : mComparators)
        {
            comparator.processSample(sampleId, mismatches);
        }

        CMP_LOGGER.debug("sample({}) writing {} mismatches", sampleId, mismatches.size());
        writeSampleMismatches(sampleId, mismatches);
    }

    private void initialiseOutputFiles()
    {
        try
        {
            final String diffFilename = mConfig.OutputDir + "COMPAR_DIFFS.csv";

            mDiffWriter = createBufferedWriter(diffFilename, false);

            mDiffWriter.write(Mismatch.header());

            mDiffWriter.newLine();
        }
        catch(IOException e)
        {
            CMP_LOGGER.error("failed to write compar output: {}", e.toString());
        }
    }

    private void writeSampleMismatches(final String sampleId, final List<Mismatch> mismatches)
    {
        if(mismatches.isEmpty() || mDiffWriter == null)
            return;

        try
        {
            for(Mismatch mismatch : mismatches)
            {
                mDiffWriter.write(mismatch.toCsv());
                mDiffWriter.newLine();
            }
        }
        catch(IOException e)
        {
            CMP_LOGGER.error("failed to write sample data: {}", e.toString());
        }
    }

    public static void main(@NotNull final String[] args) throws ParseException
    {
        Options options = new Options();
        ComparConfig.addCmdLineArgs(options);

        final CommandLine cmd = createCommandLine(args, options);

        if (cmd.hasOption(LOG_DEBUG))
        {
            Configurator.setRootLevel(Level.DEBUG);
        }

        Compar compar = new Compar(cmd);
        compar.run();
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final String[] args, @NotNull final Options options) throws ParseException
    {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

}
