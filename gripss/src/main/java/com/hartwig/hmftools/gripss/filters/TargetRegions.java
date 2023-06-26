package com.hartwig.hmftools.gripss.filters;

import static com.hartwig.hmftools.common.genome.bed.NamedBedFile.readBedFile;
import static com.hartwig.hmftools.common.utils.sv.BaseRegion.positionWithin;
import static com.hartwig.hmftools.gripss.GripssConfig.GR_LOGGER;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.genome.bed.NamedBed;
import com.hartwig.hmftools.common.utils.config.ConfigBuilder;

public class TargetRegions
{
    private final Map<String,List<NamedBed>> mTargetRegions;

    private static final String TARGET_REGIONS_BED = "target_regions_bed";

    public TargetRegions(final ConfigBuilder configBuilder)
    {
        mTargetRegions = Maps.newHashMap();

        if(configBuilder.hasValue(TARGET_REGIONS_BED))
            loadTargetRegionsBed(configBuilder.getValue(TARGET_REGIONS_BED));
    }

    public boolean hasTargetRegions() { return !mTargetRegions.isEmpty(); }

    public boolean inTargetRegions(final String chromsome, int position)
    {
        final List<NamedBed> chrRegions = mTargetRegions.get(chromsome);

        if(chrRegions == null)
            return false;

        return chrRegions.stream().anyMatch(x -> positionWithin(position, x.start(), x.end()));
    }

    private void loadTargetRegionsBed(final String bedFile)
    {
        if(bedFile == null)
            return;

        try
        {
            List<NamedBed> namedBedRecords = readBedFile(bedFile);

            for(NamedBed namedBed : namedBedRecords)
            {
                List<NamedBed> chrRegions = mTargetRegions.get(namedBed.chromosome());

                if(chrRegions == null)
                {
                    chrRegions = Lists.newArrayList();
                    mTargetRegions.put(namedBed.chromosome(), chrRegions);
                }

                chrRegions.add(namedBed);
            }

            GR_LOGGER.info("loaded {} target regions from file({})",
                    mTargetRegions.values().stream().mapToInt(x -> x.size()).sum(), bedFile);
        }
        catch (IOException e)
        {
            GR_LOGGER.error("failed to load target regions BED file: {}", e.toString());
        }
    }

    public static void addConfig(final ConfigBuilder configBuilder)
    {
        configBuilder.addPath(TARGET_REGIONS_BED, false, "Target regions BED file");
    }
}
