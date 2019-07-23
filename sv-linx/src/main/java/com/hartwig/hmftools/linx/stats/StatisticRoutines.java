package com.hartwig.hmftools.linx.stats;

import static com.hartwig.hmftools.common.io.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.linx.stats.SampleCategoryData.SAMPLE_CAT_1_INDEX;
import static com.hartwig.hmftools.linx.stats.SampleCategoryData.SAMPLE_CAT_2_INDEX;
import static com.hartwig.hmftools.linx.LinxConfig.DATA_OUTPUT_DIR;
import static com.hartwig.hmftools.linx.LinxConfig.LOG_DEBUG;
import static com.hartwig.hmftools.linx.LinxConfig.formOutputPath;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;

public class StatisticRoutines
{
    private ThreeVarCoOccurence mThreeVarCoOccurence;
    private SampleCountsCoOccurence mSampleCountsCoOccurence;

    private static String DRIVER_GENES_FILE = "driver_genes_file";
    private static String SAMPLE_COUNTS_FILE = "sample_counts_file";
    private static String SAMPLE_GENERIC_FILE = "sample_generic_file";

    private static final Logger LOGGER = LogManager.getLogger(StatisticRoutines.class);

    public static void main(@NotNull final String[] args) throws ParseException
    {
        final Options options = createBasicOptions();
        final CommandLine cmd = createCommandLine(args, options);

        if (cmd.hasOption(LOG_DEBUG))
        {
            Configurator.setRootLevel(Level.DEBUG);
        }

        String outputDir = formOutputPath(cmd.getOptionValue(DATA_OUTPUT_DIR));

        StatisticRoutines statsRoutines = new StatisticRoutines();
        statsRoutines.loadConfig(cmd, outputDir);
        statsRoutines.runStatistics();
        LOGGER.info("run complete");
    }

    private static Options createBasicOptions()
    {
        final Options options = new Options();
        options.addOption(DRIVER_GENES_FILE, true, "Drive genes file");
        options.addOption(SAMPLE_COUNTS_FILE, true, "Sample counts file");
        options.addOption(SAMPLE_GENERIC_FILE, true, "Sample data with 3 generic categories file");
        options.addOption(DATA_OUTPUT_DIR, true, "Output directory");
        return options;
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final String[] args, @NotNull final Options options) throws ParseException
    {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

    public StatisticRoutines()
    {
        mThreeVarCoOccurence = null;
        mSampleCountsCoOccurence = null;
    }

    public boolean loadConfig(final CommandLine cmd, final String outputDir)
    {
        boolean valid = true;

        if(cmd.hasOption(DRIVER_GENES_FILE) && cmd.hasOption(SAMPLE_COUNTS_FILE))
        {
            mSampleCountsCoOccurence = new SampleCountsCoOccurence();
            valid = mSampleCountsCoOccurence.loadData(
                    cmd.getOptionValue(SAMPLE_COUNTS_FILE), cmd.getOptionValue(DRIVER_GENES_FILE), outputDir);
        }
        else if(cmd.hasOption(SAMPLE_GENERIC_FILE))
        {
            mThreeVarCoOccurence = new ThreeVarCoOccurence();
            valid = mThreeVarCoOccurence.loadData(cmd.getOptionValue(SAMPLE_GENERIC_FILE), outputDir);
        }

        return valid;
    }

    public void runStatistics()
    {
        if(mSampleCountsCoOccurence != null)
            mSampleCountsCoOccurence.runTwoVariableStatistics();
        else if(mThreeVarCoOccurence != null)
            mThreeVarCoOccurence.runGenericThreeVariableStatisitics();
    }

}


