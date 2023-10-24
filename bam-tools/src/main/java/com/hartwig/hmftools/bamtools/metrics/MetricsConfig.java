package com.hartwig.hmftools.bamtools.metrics;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.genome.bed.BedFileReader;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.metrics.BamMetricsSummary;
import com.hartwig.hmftools.common.region.BaseRegion;
import com.hartwig.hmftools.common.region.ChrBaseRegion;
import com.hartwig.hmftools.common.region.SpecificRegions;
import com.hartwig.hmftools.common.utils.config.ConfigBuilder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hartwig.hmftools.bamtools.common.CommonUtils.*;
import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeSource.REF_GENOME;
import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion.V37;
import static com.hartwig.hmftools.common.region.ChrBaseRegion.loadChrBaseRegions;
import static com.hartwig.hmftools.common.utils.TaskExecutor.parseThreads;
import static com.hartwig.hmftools.common.utils.config.CommonConfig.*;
import static com.hartwig.hmftools.common.utils.file.FileDelimiters.TSV_EXTENSION;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.*;

public class MetricsConfig
{
    public final String SampleId;
    public final String BamFile;
    public final String RefGenomeFile;
    public final RefGenomeVersion RefGenVersion;

    public final int MapQualityThreshold;
    public final int BaseQualityThreshold;
    public final int MaxCoverage;

    public final int PartitionSize;

    public final List<ChrBaseRegion> UnmappableRegions;

    public final Map<String,List<BaseRegion>> TargetRegions;
    public final boolean OnlyTargetRegions;

    // metrics capture config
    public final boolean ExcludeZeroCoverage;
    public final boolean WriteOldStyle;

    public final String OutputDir;
    public final String OutputId;

    public final int Threads;

    public final SpecificRegions SpecificChrRegions;

    // debug
    public final List<String> LogReadIds;
    public final boolean PerfDebug;

    private boolean mIsValid;

    private static final String MAP_QUAL_THRESHOLD = "map_qual_threshold";
    private static final String BASE_QUAL_THRESHOLD = "base_qual_threshold";
    private static final String MAX_COVERAGE = "max_coverage";
    private static final String EXCLUDE_ZERO_COVERAGE = "exclude_zero_coverage";
    private static final String WRITE_OLD_STYLE = "write_old_style";
    private static final String ONLY_TARGET = "only_target";

    private static final int DEFAULT_MAP_QUAL_THRESHOLD = 20;
    private static final int DEFAULT_BASE_QUAL_THRESHOLD = 10;
    private static final int DEFAULT_MAX_COVERAGE = 250;

    public MetricsConfig(final ConfigBuilder configBuilder)
    {
        mIsValid = true;

        SampleId =  configBuilder.getValue(SAMPLE);
        BamFile =  configBuilder.getValue(BAM_FILE);
        RefGenomeFile =  configBuilder.getValue(REF_GENOME);

        if(configBuilder.hasValue(OUTPUT_DIR))
        {
            OutputDir = parseOutputDir(configBuilder);
        }
        else
        {
            OutputDir = checkAddDirSeparator(Paths.get(BamFile).getParent().toString());
        }

        OutputId =  configBuilder.getValue(OUTPUT_ID);

        if(BamFile == null || OutputDir == null || RefGenomeFile == null)
        {
            BT_LOGGER.error("missing config: bam({}) refGenome({}) outputDir({})",
                    BamFile != null, RefGenomeFile != null, OutputDir != null);
            mIsValid = false;
        }

        RefGenVersion = RefGenomeVersion.from(configBuilder);

        BT_LOGGER.info("refGenome({}), bam({})", RefGenVersion, BamFile);
        BT_LOGGER.info("output({})", OutputDir);

        PartitionSize = configBuilder.getInteger(PARTITION_SIZE);

        MapQualityThreshold = configBuilder.getInteger(MAP_QUAL_THRESHOLD);
        BaseQualityThreshold = configBuilder.getInteger(BASE_QUAL_THRESHOLD);
        MaxCoverage = configBuilder.getInteger(MAX_COVERAGE);
        ExcludeZeroCoverage = configBuilder.hasFlag(EXCLUDE_ZERO_COVERAGE);
        WriteOldStyle = configBuilder.hasFlag(WRITE_OLD_STYLE);

        TargetRegions = loadChrBaseRegions(configBuilder.getValue(REGIONS_FILE));
        OnlyTargetRegions = !TargetRegions.isEmpty() && configBuilder.hasFlag(ONLY_TARGET);

        UnmappableRegions = Lists.newArrayList();
        loadUnmappableRegions();

        SpecificChrRegions = SpecificRegions.from(configBuilder);

        mIsValid &= SpecificChrRegions != null;

        LogReadIds = parseLogReadIds(configBuilder);

        Threads = parseThreads(configBuilder);

        PerfDebug = configBuilder.hasFlag(PERF_DEBUG);
    }

    public boolean isValid()
    {
        if(!mIsValid)
            return false;

        mIsValid = checkFileExists(BamFile) && checkFileExists(RefGenomeFile);
        return mIsValid;
    }

    private void loadUnmappableRegions()
    {
        String filename = getUnmappableRegionsFileName(RefGenVersion);

        final InputStream inputStream = MetricsConfig.class.getResourceAsStream(filename);

        try
        {
            UnmappableRegions.addAll(
                    BedFileReader.loadBedFile(new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.toList())));
        }
        catch(Exception e)
        {
            BT_LOGGER.error("failed to load unmapped regions file({}): {}", filename, e.toString());
            System.exit(1);
        }
    }

    static String getUnmappableRegionsFileName(RefGenomeVersion refGenomeVersion) {
        return refGenomeVersion.getUnmappableRegionsFileName();
    }

    public String formFilename(final String fileType)
    {
        String filename = OutputDir + SampleId + BamMetricsSummary.BAM_METRICS_FILE_ID;

        filename += "." + fileType;

        if(OutputId != null)
            filename += "." + OutputId;

        return filename + TSV_EXTENSION;
    }

    public static void addConfig(final ConfigBuilder configBuilder)
    {
        addCommonCommandOptions(configBuilder);

        configBuilder.addInteger(PARTITION_SIZE, "Partition size", DEFAULT_CHR_PARTITION_SIZE);
        configBuilder.addInteger(MAP_QUAL_THRESHOLD, "Map quality threshold", DEFAULT_MAP_QUAL_THRESHOLD);
        configBuilder.addInteger(BASE_QUAL_THRESHOLD, "Base quality threshold", DEFAULT_BASE_QUAL_THRESHOLD);
        configBuilder.addInteger(MAX_COVERAGE, "Max coverage", DEFAULT_MAX_COVERAGE);
        configBuilder.addFlag(ONLY_TARGET, "Only capture metrics within the specific regions file");
        configBuilder.addFlag(EXCLUDE_ZERO_COVERAGE, "Exclude bases with zero coverage");
        configBuilder.addFlag(WRITE_OLD_STYLE, "Write data in same format as Picard CollectWgsMetrics");
        configBuilder.addConfigItem(LOG_READ_IDS, LOG_READ_IDS_DESC);
        configBuilder.addFlag(PERF_DEBUG, PERF_DEBUG_DESC);
    }

    @VisibleForTesting
    public MetricsConfig(int maxCoveage)
    {
        mIsValid = true;

        SampleId = "SAMPLE_ID";
        BamFile = null;
        RefGenomeFile = null;
        RefGenVersion = V37;
        OutputDir = null;
        OutputId = null;

        PartitionSize = DEFAULT_CHR_PARTITION_SIZE;
        MapQualityThreshold = DEFAULT_MAP_QUAL_THRESHOLD;
        BaseQualityThreshold = DEFAULT_BASE_QUAL_THRESHOLD;
        MaxCoverage = maxCoveage;
        ExcludeZeroCoverage = false;
        WriteOldStyle = false;

        SpecificChrRegions = new SpecificRegions();
        LogReadIds = Collections.emptyList();
        UnmappableRegions = Collections.emptyList();
        TargetRegions = Maps.newHashMap();
        OnlyTargetRegions = false;

        Threads = 0;
        PerfDebug = false;
    }
}
