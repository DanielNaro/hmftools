package com.hartwig.hmftools.svanalysis.annotators;

import static htsjdk.tribble.AbstractFeatureReader.getFeatureReader;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.region.ReplicationOriginRegion;
import com.hartwig.hmftools.svanalysis.types.SvBreakend;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import htsjdk.tribble.readers.LineIterator;

public class ReplicationOriginAnnotator
{
    Map<String, List<ReplicationOriginRegion>> mReplicationOrigins; // regions by chromosome

    private static final Logger LOGGER = LogManager.getLogger(ReplicationOriginAnnotator.class);

    public ReplicationOriginAnnotator()
    {
        mReplicationOrigins = new HashMap();
    }

    public void loadReplicationOrigins(final String filename)
    {
        if(filename.isEmpty())
            return;

        try
        {
            String currentChr = "";
            List<ReplicationOriginRegion> regionList = null;
            int regionCount = 0;

            final AbstractFeatureReader<BEDFeature, LineIterator> reader = getFeatureReader(filename, new BEDCodec(), false);

            for (final BEDFeature bedFeature : reader.iterator())
            {
                String chromosome = bedFeature.getContig();

                if(chromosome.contains("chr"))
                {
                    chromosome = chromosome.replaceAll("chr", "");
                }

                if (!chromosome.equals(currentChr))
                {
                    currentChr = chromosome;
                    regionList = mReplicationOrigins.get(chromosome);

                    if (regionList == null)
                    {
                        regionList = Lists.newArrayList();
                        mReplicationOrigins.put(currentChr, regionList);
                    }
                }

                ++regionCount;

                double originValue = Double.parseDouble(bedFeature.getName()) / 100;

                regionList.add(new ReplicationOriginRegion(
                        chromosome,
                        bedFeature.getStart(),
                        bedFeature.getEnd(),
                        originValue));
            }

            LOGGER.debug("loaded {} replication origins", regionCount);
        }
        catch(IOException exception)
        {
            LOGGER.error("Failed to read replication origin BED file({})", filename);
        }
    }

    public void setReplicationOrigins(final Map<String, List<SvBreakend>> chrBreakendMap)
    {
        for (final Map.Entry<String, List<SvBreakend>> entry : chrBreakendMap.entrySet())
        {
            final String chromosome = entry.getKey();
            final List<SvBreakend> breakendList = entry.getValue();

            List<ReplicationOriginRegion> regions = mReplicationOrigins.get(chromosome);

            if(regions == null || regions.isEmpty())
                continue;

            int regionIndex = 0;
            ReplicationOriginRegion currentRegion = regions.get(regionIndex);

            for(final SvBreakend breakend : breakendList)
            {
                while(currentRegion.End < breakend.position())
                {
                    ++regionIndex;

                    if(regionIndex >= regions.size())
                    {
                        // the origins data may not extend all the way to the telomeres or cover all SV breakends
                        currentRegion = null;
                        break;
                    }

                    currentRegion = regions.get(regionIndex);
                }

                if(currentRegion != null)
                {
                    breakend.getSV().setReplicationOrigin(breakend.usesStart(), currentRegion.OriginValue);
                }
                else
                {
                    break;
                }
            }
        }
    }

    // general purpose method
    public double getReplicationOriginValue(final String chromosome, long position)
    {
        if(mReplicationOrigins.isEmpty())
            return 0;

        List<ReplicationOriginRegion> regions = mReplicationOrigins.get(chromosome);

        if(regions == null || regions.isEmpty())
            return 0;

        for(final ReplicationOriginRegion region : regions)
        {
            if(region.Start <= position && region.End >= position)
            {
                return region.OriginValue;

            }
        }

        return 0;
    }

}
