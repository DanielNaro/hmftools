package com.hartwig.hmftools.svprep.tools;

import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeSource.REF_GENOME;
import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeSource.addRefGenomeConfig;
import static com.hartwig.hmftools.common.utils.TaskExecutor.addThreadOptions;
import static com.hartwig.hmftools.common.utils.TaskExecutor.parseThreads;
import static com.hartwig.hmftools.common.utils.sv.ChrBaseRegion.SPECIFIC_REGIONS;
import static com.hartwig.hmftools.common.utils.sv.ChrBaseRegion.addSpecificChromosomesRegionsConfig;
import static com.hartwig.hmftools.common.utils.sv.ChrBaseRegion.loadSpecificRegions;
import static com.hartwig.hmftools.svprep.SvCommon.SV_LOGGER;
import static com.hartwig.hmftools.svprep.SvConstants.DEFAULT_CHR_PARTITION_SIZE;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.utils.config.ConfigBuilder;
import com.hartwig.hmftools.common.utils.sv.ChrBaseRegion;

import org.apache.commons.cli.ParseException;

public class HighDepthConfig
{
    public final String BamFile;
    public final String RefGenome;
    public final String OutputFile;
    public final RefGenomeVersion RefGenVersion;
    public final List<ChrBaseRegion> SpecificRegions;
    public final int PartitionSize;
    public final int HighDepthThreshold;
    public final int Threads;

    private static final String BAM_FILE = "bam_file";
    private static final String OUTPUT_FILE = "output_file";
    private static final String PARTITION_SIZE = "partition_size";
    private static final String HIGH_DEPTH_THRESHOLD = "high_depth_threshold";

    public static final int DEFAULT_HIGH_DEPTH_THRESHOLD = 200;
    public static final int HIGH_DEPTH_REGION_MAX_GAP = 100;

    public HighDepthConfig(final ConfigBuilder configBuilder)
    {
        BamFile = configBuilder.getValue(BAM_FILE);
        OutputFile = configBuilder.getValue(OUTPUT_FILE);
        RefGenome = configBuilder.getValue(REF_GENOME);
        RefGenVersion = RefGenomeVersion.from(configBuilder);
        Threads = parseThreads(configBuilder);
        PartitionSize = configBuilder.getInteger(PARTITION_SIZE);
        HighDepthThreshold = configBuilder.getInteger(HIGH_DEPTH_THRESHOLD);

        SpecificRegions = Lists.newArrayList();

        try
        {
            SpecificRegions.addAll(loadSpecificRegions(configBuilder.getValue(SPECIFIC_REGIONS)));
        }
        catch(ParseException e)
        {
            SV_LOGGER.error("failed to load specific regions");
        }
    }

    public static void addConfig(final ConfigBuilder configBuilder)
    {
        configBuilder.addPathItem(BAM_FILE, true, "BAM file to slice for high-depth");
        configBuilder.addConfigItem(OUTPUT_FILE, true, "Output file");
        addRefGenomeConfig(configBuilder, true);

        configBuilder.addIntegerItem(
                HIGH_DEPTH_THRESHOLD, false, "Level for indicating high-depth", DEFAULT_HIGH_DEPTH_THRESHOLD);

        configBuilder.addIntegerItem(PARTITION_SIZE, false, "Partition size, default", DEFAULT_CHR_PARTITION_SIZE);
        addThreadOptions(configBuilder);
        addSpecificChromosomesRegionsConfig(configBuilder);
    }
}
