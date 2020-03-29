package com.hartwig.hmftools.isofox.common;

import static java.lang.Math.max;
import static java.lang.Math.min;

import static com.hartwig.hmftools.common.utils.Strings.appendStr;
import static com.hartwig.hmftools.isofox.IsofoxConfig.ISF_LOGGER;
import static com.hartwig.hmftools.isofox.common.GeneReadData.generateCommonExonicRegions;
import static com.hartwig.hmftools.isofox.common.RegionReadData.findExonRegion;
import static com.hartwig.hmftools.isofox.common.RegionReadData.generateExonicRegions;
import static com.hartwig.hmftools.isofox.common.RnaUtils.positionsOverlap;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_PAIR;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.ensemblcache.ExonData;
import com.hartwig.hmftools.common.ensemblcache.TranscriptData;

public class GeneCollection
{
    private final int mId;
    private final List<GeneReadData> mGenes;
    private final String mChromosome;
    private final long[] mRegionBounds;

    private final Map<Integer,GeneReadData> mTransIdsGeneMap;

    private final List<RegionReadData> mExonRegions; // set of unique exons ie with differing start and end positions
    private final List<long[]> mCommonExonicRegions; // merge any overlapping exons, to form a set of exonic regions for the gene
    private final List<TranscriptData> mTranscripts;

    // summary results
    private final Map<Integer,int[][]> mTranscriptReadCounts; // count of fragments support types for each transcript, and whether unique
    private final Map<String,Double> mFitAllocations; // results from the expected rate vs counts fit routine
    private double mFitResiduals;

    public GeneCollection(int id, final List<GeneReadData> genes)
    {
        mId = id;
        mGenes = genes;

        mRegionBounds = new long[SE_PAIR];

        mChromosome = genes.get(0).GeneData.Chromosome;

        mTransIdsGeneMap = Maps.newHashMap();

        mExonRegions = Lists.newArrayList();
        mTranscripts = Lists.newArrayList();
        mCommonExonicRegions = Lists.newArrayList();

        buildCache();

        mTranscriptReadCounts = Maps.newHashMap();
        mFitAllocations = Maps.newHashMap();
        mFitResiduals = 0;
    }

    public int id() { return mId; }
    public String chrId() { return String.format("%s:%d", mChromosome, mId); }
    public final String chromosome() { return mChromosome; }
    public final List<GeneReadData> genes() { return mGenes; }
    public final long[] regionBounds() { return mRegionBounds; }

    public final List<TranscriptData> getTranscripts() { return mTranscripts; }
    public void setTranscripts(final List<TranscriptData> transDataList) { mTranscripts.addAll(transDataList); }

    public final List<RegionReadData> getExonRegions() { return mExonRegions; }
    public List<long[]> getCommonExonicRegions() { return mCommonExonicRegions; }

    public int getStrand(int transId)
    {
        final GeneReadData gene = mTransIdsGeneMap.get(transId);
        return gene != null ? gene.GeneData.Strand : 0;
    }

    public String geneNames() { return geneNames(10); }

    public String geneNames(int maxCount)
    {
        if(mGenes.size() == 1)
            return mGenes.get(0).name();

        StringBuilder geneNames = new StringBuilder(mGenes.get(0).name());

        for(int i = 1; i < min(mGenes.size(), maxCount); ++i)
        {
            geneNames.append(";" + mGenes.get(i).name());
        }

        return geneNames.toString();
    }

    private void buildCache()
    {
        for(final GeneReadData gene : mGenes)
        {
            for(final TranscriptData transData : gene.getTranscripts())
            {
                mTransIdsGeneMap.put(transData.TransId, gene);
                mTranscripts.add(transData);

                mRegionBounds[SE_START] = mRegionBounds[SE_START] == 0 ? transData.TransStart : min(mRegionBounds[SE_START], transData.TransStart);
                mRegionBounds[SE_END] = max(mRegionBounds[SE_END], transData.TransEnd);
            }

            generateExonicRegions(mChromosome, mExonRegions, gene.getTranscripts());

            // cache the relevant set of exon regions back into the gene for convenience
            for(final TranscriptData transData : gene.getTranscripts())
            {
                for (final ExonData exon : transData.exons())
                {
                    final RegionReadData exonReadData = findExonRegion(mExonRegions, exon.ExonStart, exon.ExonEnd);
                    if (exonReadData == null)
                    {
                        ISF_LOGGER.error("genes({}) failed to create exonic regions", geneNames());
                        return;
                    }

                    gene.addExonRegion(exonReadData);
                }
            }
        }

        generateCommonExonicRegions(mExonRegions, mCommonExonicRegions);
    }

    public boolean hasGeneData(int transId) { return mTransIdsGeneMap.containsKey(transId); }

    public GeneReadData findGeneData(int transId)
    {
        return mTransIdsGeneMap.get(transId);
    }

    public List<GeneReadData> findGenesCoveringRange(long posStart, long posEnd)
    {
        return mGenes.stream()
                .filter(x -> positionsOverlap(x.GeneData.GeneStart, x.GeneData.GeneEnd, posStart, posEnd))
                .collect(Collectors.toList());
    }

    public static final int TRANS_COUNT = 0;
    public static final int UNIQUE_TRANS_COUNT = 1;

    public int[][] getTranscriptReadCount(final int transId)
    {
        int[][] counts = mTranscriptReadCounts.get(transId);
        return counts != null ? counts : new int[FragmentMatchType.MAX_FRAG_TYPE][UNIQUE_TRANS_COUNT+1];
    }

    public void addTranscriptReadMatch(int transId, boolean isUnique, FragmentMatchType type)
    {
        int[][] counts = mTranscriptReadCounts.get(transId);
        if(counts == null)
        {
            counts = new int[FragmentMatchType.MAX_FRAG_TYPE][UNIQUE_TRANS_COUNT+1];
            mTranscriptReadCounts.put(transId,  counts);
        }

        if(isUnique)
        {
            ++counts[FragmentMatchType.typeAsInt(type)][UNIQUE_TRANS_COUNT];
        }

        ++counts[FragmentMatchType.typeAsInt(type)][TRANS_COUNT];
    }

    public void setFitResiduals(double residuals) { mFitResiduals = residuals; }
    public double getFitResiduals() { return mFitResiduals; }

    public Map<String,Double> getFitAllocations() { return mFitAllocations; }

    public double getFitAllocation(final String transName)
    {
        Double allocation = mFitAllocations.get(transName);
        return allocation != null ? allocation : 0;
    }

    public void logOverlappingGenes(final List<GeneReadData> overlappingGenes)
    {
        String geneNamesStr = "";
        int transcriptCount = 0;
        long minRange = -1;
        long maxRange = 0;

        for(GeneReadData geneReadData : overlappingGenes)
        {
            geneNamesStr = appendStr(geneNamesStr, geneReadData.GeneData.GeneId, ';');
            transcriptCount += geneReadData.getTranscripts().size();
            maxRange =  max(maxRange, geneReadData.GeneData.GeneEnd);
            minRange =  minRange < 0 ? geneReadData.GeneData.GeneStart : min(minRange, geneReadData.GeneData.GeneStart);
        }

        // Time,Chromosome,GeneCount,TranscriptCount,RangeStart,RangeEnd,GeneNames
        ISF_LOGGER.info("GENE_OVERLAP: {},{},{},{},{},{}", // chr({}) genes({}) transcripts({}) range({} -> {}),
                mChromosome, overlappingGenes.size(), transcriptCount, minRange, maxRange, geneNamesStr);
    }
}
