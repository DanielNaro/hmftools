package com.hartwig.hmftools.bamtools.metrics;

import static java.lang.String.format;

import static com.hartwig.hmftools.bamtools.common.CommonUtils.BT_LOGGER;
import static com.hartwig.hmftools.common.metrics.WGSMetricsFile.DELIM;
import static com.hartwig.hmftools.common.metrics.WGSMetricsFile.GENOME_TERRITORY_COLUMN;
import static com.hartwig.hmftools.common.metrics.WGSMetricsFile.HET_SNP_Q_COLUMN;
import static com.hartwig.hmftools.common.metrics.WGSMetricsFile.HET_SNP_SENSITIVITY_COLUMN;
import static com.hartwig.hmftools.common.metrics.WGSMetricsFile.MAD_COVERAGE_COLUMN;
import static com.hartwig.hmftools.common.metrics.WGSMetricsFile.MEAN_COVERAGE_COLUMN;
import static com.hartwig.hmftools.common.metrics.WGSMetricsFile.MEDIAN_COVERAGE_COLUMN;
import static com.hartwig.hmftools.common.metrics.WGSMetricsFile.PCT_EXC_ADAPTER_COLUMN;
import static com.hartwig.hmftools.common.metrics.WGSMetricsFile.PCT_EXC_TOTAL_COLUMN;
import static com.hartwig.hmftools.common.metrics.WGSMetricsFile.SD_COVERAGE_COLUMN;
import static com.hartwig.hmftools.common.utils.FileWriterUtils.createBufferedWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.metrics.WGSMetricsFile;

public final class MetricsWriter
{
    public static void writeResults(final Metrics metrics, final MetricsConfig config)
    {
        if(config.WriteOldStyle)
        {
            writeOldStyleFile(metrics, config);
        }
        else
        {
            writeMetrics(metrics, config);
            writeCoverageFrequency(metrics, config);
        }
    }

    private static final List<Integer> COVERAGE_LEVELS = Lists.newArrayList(1, 5, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 90, 100);

    private static String metricsHeader()
    {
        StringJoiner header = new StringJoiner(DELIM);

        header.add(GENOME_TERRITORY_COLUMN);
        header.add(MEAN_COVERAGE_COLUMN);
        header.add(SD_COVERAGE_COLUMN);
        header.add(MEDIAN_COVERAGE_COLUMN);
        header.add(MAD_COVERAGE_COLUMN);

        header.add(PCT_EXC_ADAPTER_COLUMN);

        for(FilterType type : FilterType.values())
        {
            if(type == FilterType.UNFILTERED)
                continue;

            header.add(type.fileColumn());
        }

        header.add(PCT_EXC_TOTAL_COLUMN);

        for(Integer coverage : COVERAGE_LEVELS)
        {
            header.add(format("PCT_%dX", coverage));
        }

        return header.toString();
    }

    private static String metricsTsv(final Metrics metrics)
    {
        StringJoiner tsvData = new StringJoiner(DELIM);

        final Statistics statistics = metrics.statistics();

        long genomeTerritory = metrics.genomeTerritory();

        tsvData.add(String.valueOf(genomeTerritory));
        tsvData.add(format("%.3f", statistics.Mean));
        tsvData.add(format("%.3f", statistics.StandardDeviation));
        tsvData.add(format("%.0f", statistics.Median));
        tsvData.add(format("%d", statistics.MedianAbsoluteDeviation));

        tsvData.add(format("%.5f", 0.0));

        double filteredPercTotal = 0;
        for(FilterType type : FilterType.values())
        {
            if(type == FilterType.UNFILTERED)
                continue;

            double typePercent = metrics.calcFilteredPercentage(type);
            filteredPercTotal += typePercent;
            tsvData.add(format("%.5f", typePercent));
        }

        tsvData.add(format("%.5f", filteredPercTotal));

        for(Integer coverage : COVERAGE_LEVELS)
        {
            tsvData.add(format("%.5f", metrics.calcCoverageFrequency(coverage, genomeTerritory)));
        }

        // HET SNP values are uncalculated

        return tsvData.toString();
    }

    private static void writeMetrics(final Metrics metrics, final MetricsConfig config)
    {
        try
        {
            // write summary metrics
            String filename = config.formFilename("metrics");
            BufferedWriter writer = createBufferedWriter(filename, false);

            writer.write(metricsHeader());
            writer.newLine();
            writer.write(metricsTsv(metrics));
            writer.newLine();

            writer.close();
        }
        catch(IOException e)
        {
            BT_LOGGER.error("failed to write metrics file: {}", e.toString());
        }
    }

    private static void writeCoverageFrequency(final Metrics metrics, final MetricsConfig config)
    {
        try
        {
            // write coverage frequency for unfiltered aligned bases
            String filename = config.formFilename("coverage");
            BufferedWriter writer = createBufferedWriter(filename, false);

            writer.write("Coverage,Frequency");
            writer.newLine();

            for(int i = 0; i < metrics.CoverageFrequency.length; ++i)
            {
                writer.write(format("%d,%d", i, metrics.CoverageFrequency[i]));
                writer.newLine();
            }

            writer.close();
        }
        catch(IOException e)
        {
            BT_LOGGER.error("failed to write coverage frequency file: {}", e.toString());
        }
    }

    private static void writeOldStyleFile(final Metrics metrics, final MetricsConfig config)
    {
        try
        {
            // write summary metrics
            String filename;

            if(config.SampleId != null && !config.SampleId.isEmpty())
            {
                filename = WGSMetricsFile.generateFilename(config.OutputDir, config.SampleId);
            }
            else
            {
                String filePrefix = config.BamFile.substring(0, config.BamFile.indexOf(".bam"));
                filename = filePrefix + WGSMetricsFile.FILE_EXTENSION;
            }

            BufferedWriter writer = createBufferedWriter(filename, false);

            writer.write("## METRICS CLASS");
            writer.newLine();

            writer.write(metricsHeader());
            writer.newLine();
            writer.write(metricsTsv(metrics));
            writer.newLine();
            writer.newLine();

            writer.write("## HISTOGRAM");
            writer.newLine();

            writer.write("coverage" + DELIM + "quality_coverage_count");
            writer.newLine();

            for(int i = 0; i < metrics.CoverageFrequency.length; ++i)
            {
                writer.write(format("%d%s%d", i, DELIM, metrics.CoverageFrequency[i]));
                writer.newLine();
            }

            writer.close();
        }
        catch(IOException e)
        {
            BT_LOGGER.error("failed to write metrics file: {}", e.toString());
        }

    }

}
