package com.hartwig.hmftools.svtools.rna_expression;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import static com.hartwig.hmftools.linx.types.SvVarData.SE_END;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_START;
import static com.hartwig.hmftools.svtools.rna_expression.RegionMatchType.EXON_BOUNDARY;
import static com.hartwig.hmftools.svtools.rna_expression.RegionMatchType.EXON_INTRON;
import static com.hartwig.hmftools.svtools.rna_expression.RegionMatchType.EXON_MATCH;
import static com.hartwig.hmftools.svtools.rna_expression.RegionMatchType.WITHIN_EXON;
import static com.hartwig.hmftools.svtools.rna_expression.RegionReadData.extractTransId;
import static com.hartwig.hmftools.svtools.rna_expression.RnaExpUtils.positionsOverlap;
import static com.hartwig.hmftools.svtools.rna_expression.TransMatchType.ALT;
import static com.hartwig.hmftools.svtools.rna_expression.TransMatchType.EXONIC;
import static com.hartwig.hmftools.svtools.rna_expression.TransMatchType.SPLICE_JUNCTION;
import static com.hartwig.hmftools.svtools.rna_expression.TransMatchType.UNKNOWN;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;

public class ReadRecord
{
    private final SAMRecord mSamRecord;

    public final String Id;
    public final String Chromosome;
    public final long PosStart;
    public final long PosEnd;

    public final String ReadBases;
    public final int Length; // of bases
    public final Cigar Cigar;

    public final List<long[]> mMappedCoords;
    private boolean mLowerInferredAdded;
    private boolean mUpperInferredAdded;
    private int mFragmentInsertSize;

    private final Map<RegionReadData,RegionMatchType> mMappedRegions; // regions related to this read and their match type

    private final Map<Integer,TransMatchType> mTranscriptClassification;

    private static final Logger LOGGER = LogManager.getLogger(ReadRecord.class);

    public static ReadRecord from(final SAMRecord record)
    {
        return new ReadRecord(
                record, record.getReadName(), record.getReferenceName(), record.getStart(), record.getEnd(),
                record.getReadString(), record.getCigar());
    }

    public ReadRecord(
            final SAMRecord record, final String id, final String chromosome, long posStart, long posEnd,
            final String readBases, @NotNull final Cigar cigar)
    {
        mSamRecord = record;

        Id = id;
        Chromosome = chromosome;
        PosStart = posStart;
        PosEnd = posEnd;
        ReadBases = readBases;
        Length = ReadBases.length();
        Cigar = cigar;

        mMappedCoords = Lists.newArrayList();
        generateMappedCoords();

        mMappedRegions = Maps.newHashMap();
        mTranscriptClassification = Maps.newHashMap();
        mLowerInferredAdded = false;
        mUpperInferredAdded = false;
        mFragmentInsertSize = mSamRecord != null ? mSamRecord.getInferredInsertSize() : 0;
    }

    public final SAMRecord samRecord() { return mSamRecord; }

    public long range() { return PosEnd - PosStart; }

    public void setFragmentInsertSize(int size) { mFragmentInsertSize = size; }
    public int fragmentInsertSize() { return mFragmentInsertSize; }

    public boolean isTranslocation()
    {
        return mSamRecord != null && !mSamRecord.getMateReferenceName().equals(mSamRecord.getReferenceName());
    }

    public boolean isLocalInversion()
    {
        return mSamRecord != null && mSamRecord.getReadNegativeStrandFlag() == mSamRecord.getMateNegativeStrandFlag();
    }

    public List<long[]> getMappedRegionCoords() { return mMappedCoords; }

    public boolean overlapsMappedReads(long posStart, long posEnd)
    {
        return mMappedCoords.stream().anyMatch(x -> positionsOverlap(posStart, posEnd, x[SE_START], x[SE_END]));
    }

    private void generateMappedCoords()
    {
        // first establish whether the read is split across 2 distant regions, and if so which it maps to
        int posOffset = 0;
        boolean continueRegion = false;

        for(CigarElement element : Cigar.getCigarElements())
        {
            if(element.getOperator() == CigarOperator.S)
            {
                // nothing to skip
            }
            else if(element.getOperator() == CigarOperator.D || element.getOperator() == CigarOperator.I)
            {
                // nothing to skip
                continueRegion = true;
            }
            else if(element.getOperator() == CigarOperator.N)
            {
                posOffset += element.getLength();
                continueRegion = false;
            }
            else if(element.getOperator() == CigarOperator.M)
            {
                long readStartPos = PosStart + posOffset;
                long readEndPos = readStartPos + element.getLength() - 1;

                if(continueRegion && !mMappedCoords.isEmpty())
                {
                    long[] lastRegion = mMappedCoords.get(mMappedCoords.size() - 1);
                    lastRegion[SE_END] = readEndPos;
                }
                else
                {
                    mMappedCoords.add(new long[] { readStartPos, readEndPos });
                }

                posOffset += element.getLength();
                continueRegion = false;
            }
        }
    }

    public void processOverlappingRegions(final List<RegionReadData> regions)
    {
        // process all regions for each transcript as a group to look for inconsistencies with the transcript definition
        List<Integer> transcripts = Lists.newArrayList();

        for(RegionReadData region : regions)
        {
            for(final String ref : region.getRefRegions())
            {
                final int transId = extractTransId(ref);

                if (!transcripts.contains(transId))
                    transcripts.add(transId);
            }

            RegionMatchType matchType = getRegionMatchType(region);
            mMappedRegions.put(region, matchType);

            boolean checkMissedJunctions = matchType == EXON_INTRON
                    || (Cigar.containsOperator(CigarOperator.S) && (matchType == EXON_BOUNDARY || matchType == EXON_MATCH));

            if(checkMissedJunctions)
                checkMissedJunctions(region);
        }

        for(int transId : transcripts)
        {
            TransMatchType transMatchType = UNKNOWN;

            List<RegionReadData> transRegions = regions.stream()
                    .filter(x -> x.getRefRegions().stream().anyMatch(y -> extractTransId(y) == transId))
                    .collect(Collectors.toList());

            // if any reads cross and exon-intron boundary, then mark the transcript as unspliced

            if(transRegions.size() == 1 && mappedRegionCount() == 1)
            {
                // simple case of a single exon and read section
                RegionReadData region = transRegions.get(0);
                RegionMatchType matchType = mMappedRegions.get(region);

                if (matchType == RegionMatchType.NONE)
                {
                    // should never happen since implies this read didn't hit the region at all
                    transMatchType = ALT;
                }
                else if (matchType == RegionMatchType.EXON_INTRON)
                {
                    transMatchType = TransMatchType.UNSPLICED;
                }
            }
            else if(mappedRegionCount() > transRegions.size())
            {
                transMatchType = ALT;
            }
            else
            {
                int minExonRank = 0;
                int maxExonRank = 0;

                Collections.sort(transRegions);

                for (int regionIndex = 0; regionIndex < transRegions.size(); ++regionIndex)
                {
                    RegionReadData region = transRegions.get(regionIndex);

                    int exonRank = region.getExonRank(transId);
                    maxExonRank = max(maxExonRank, exonRank);
                    minExonRank = minExonRank == 0 ? exonRank : min(exonRank, minExonRank);

                    int mappingIndex = getRegionMappingIndex(region);

                    if(mLowerInferredAdded)
                        --mappingIndex;

                    if (mappingIndex < 0 || mappingIndex != regionIndex)
                    {
                        transMatchType = ALT;
                        break;
                    }

                    RegionMatchType matchType = mMappedRegions.get(region);

                    if (matchType == RegionMatchType.EXON_INTRON)
                    {
                        transMatchType = TransMatchType.UNSPLICED;
                        break;
                    }
                    else
                    {
                        final long[] readSection = mMappedCoords.get(mappingIndex);
                        long readStartPos = readSection[SE_START];
                        long readEndPos = readSection[SE_END];

                        boolean missStart = readStartPos > region.start();
                        boolean missEnd = readEndPos < region.end();
                        if(regionIndex == 0)
                        {
                            if(missEnd)
                            {
                                transMatchType = ALT;
                                break;
                            }
                        }
                        else if(regionIndex == transRegions.size() - 1)
                        {
                            if(missStart)
                            {
                                transMatchType = ALT;
                                break;
                            }
                        }
                        else if(missStart || missEnd)
                        {
                            transMatchType = ALT;
                            break;
                        }
                    }
                }

                if (transMatchType == UNKNOWN)
                {
                    int expectedRegions = maxExonRank - minExonRank + 1;
                    if (transRegions.size() < expectedRegions)
                        transMatchType = ALT;
                }
            }

            if(transMatchType == UNKNOWN)
            {
                if(transRegions.size() > 1)
                {
                    transMatchType = SPLICE_JUNCTION;
                }
                else
                {
                    transMatchType = EXONIC;
                }
            }

            mTranscriptClassification.put(transId, transMatchType);
        }
    }

    public static final List<RegionReadData> getUniqueValidRegion(final ReadRecord read1, final ReadRecord read2)
    {
        final List<RegionReadData> regions = read1.getMappedRegions().entrySet().stream()
                .filter(x -> validRegionMatchType(x.getValue()))
                .map(x -> x.getKey()).collect(Collectors.toList());

        final List<RegionReadData> regions2 = read2.getMappedRegions().entrySet().stream()
                .filter(x -> validRegionMatchType(x.getValue()))
                .map(x -> x.getKey()).collect(Collectors.toList());

        for(RegionReadData region : regions2)
        {
            if (!regions.contains(region))
                regions.add(region);
        }

        return regions;
    }

    public static boolean hasSkippedExons(final List<RegionReadData> regions, int transId, int longFragmentLimit)
    {
        int minExonRank = -1;
        int maxExonRank = 0;
        int regionCount = 0;

        long minRegionPos = -1;
        long maxRegionPos = 0;

        for(RegionReadData region : regions)
        {
            if(!region.hasTransId(transId))
                continue;

            ++regionCount;
            int exonRank = region.getExonRank(transId);

            maxExonRank = max(maxExonRank, exonRank);
            minExonRank = minExonRank == -1 ? exonRank : min(exonRank, exonRank);
            minRegionPos = minRegionPos == -1 ? region.start() : min(minRegionPos, region.start());
            maxRegionPos = max(maxRegionPos, region.end());
        }

        int expectedRegions = maxExonRank - minExonRank + 1;
        return regionCount < expectedRegions && maxRegionPos - minRegionPos > longFragmentLimit * 2;
    }

    public static boolean validTranscriptType(TransMatchType transType)
    {
        return transType == EXONIC || transType == SPLICE_JUNCTION;
    }

    public static boolean validRegionMatchType(RegionMatchType matchType)
    {
        return matchType == EXON_BOUNDARY || matchType == WITHIN_EXON || matchType == EXON_MATCH;
    }

    public int getRegionMappingIndex(final RegionReadData region)
    {
        for(int i = 0; i < mMappedCoords.size(); ++i)
        {
            final long[] readSection = mMappedCoords.get(i);
            long readStartPos = readSection[SE_START];
            long readEndPos = readSection[SE_END];

            if (!(readStartPos > region.end() || readEndPos < region.start()))
                return i;
        }

        return -1;
    }

    public RegionMatchType getRegionMatchType(final RegionReadData region)
    {
        int mappingIndex = getRegionMappingIndex(region);
        if (mappingIndex < 0)
            return RegionMatchType.NONE;

        return getRegionMatchType(region, mappingIndex);
    }

    private RegionMatchType getRegionMatchType(final RegionReadData region, int mappingIndex)
    {
        if(mappingIndex < 0 || mappingIndex >= mMappedCoords.size())
            return RegionMatchType.NONE;

        final long[] readSection = mMappedCoords.get(mappingIndex);
        long readStartPos = readSection[SE_START];
        long readEndPos = readSection[SE_END];

        if (readEndPos < region.start() || readStartPos > region.end())
            return RegionMatchType.NONE;

        if (readStartPos < region.start() || readEndPos > region.end())
            return RegionMatchType.EXON_INTRON;

        if (readStartPos > region.start() && readEndPos < region.end())
            return WITHIN_EXON;

        if (readStartPos == region.start() && readEndPos == region.end())
            return EXON_MATCH;

        return EXON_BOUNDARY;
    }

    public static void markRegionBases(final List<long[]> readCoords, final RegionReadData region)
    {
        int[] regionBaseDepth = region.refBasesMatched();

        if(regionBaseDepth == null)
            return;

        for(final long[] readSection : readCoords)
        {
            long readStartPos = readSection[SE_START];
            long readEndPos = readSection[SE_END];

            if (readStartPos > region.end() || readEndPos < region.start())
                continue;

            // process this overlap
            int regionBaseIndex = readStartPos > region.start() ? (int)(readStartPos - region.start()) : 0;
            int overlap = (int)(min(readEndPos, region.end()) - max(readStartPos, region.start())) + 1;

            if(regionBaseIndex + overlap > regionBaseDepth.length)
            {
                LOGGER.error("region({}) read coords({} -> {}) regionBaseIndex({}) overlap({}) regionLength({})",
                        region, readStartPos, readEndPos, regionBaseIndex, overlap, regionBaseDepth.length);
                return;
            }

            for(int j = regionBaseIndex; j < regionBaseIndex + overlap; ++j)
            {
                ++regionBaseDepth[j];
            }
        }
    }

    public static int calcFragmentLength(final ReadRecord read1, final ReadRecord read2)
    {
        int insertSize = abs(read1.fragmentInsertSize());

        if(!read1.containsSplit() && !read2.containsSplit())
            return insertSize;

        // find unique split lengths and remove them

        List<Integer> splitLengths = read1.Cigar.getCigarElements().stream()
                .filter(x -> x.getOperator() == CigarOperator.N).map(x -> x.getLength()).collect(Collectors.toList());

        for(final CigarElement element : read2.Cigar.getCigarElements())
        {
            if(element.getOperator() == CigarOperator.N && !splitLengths.contains(element.getLength()))
                splitLengths.add(element.getLength());
        }

        int totalSplitLength = splitLengths.stream().mapToInt(x -> x).sum();

        return insertSize - totalSplitLength;
    }

    public boolean containsSplit()
    {
        return Cigar != null && Cigar.containsOperator(CigarOperator.N);
    }

    private static int MIN_BASE_MATCH = 2;

    private void checkMissedJunctions(final RegionReadData region)
    {
        // check for reads either soft-clipped or apparently unspliced, where the extra bases can match with the next exon

        // check start of read
        long[] readSection = mLowerInferredAdded ? mMappedCoords.get(1) : mMappedCoords.get(0);
        long readStartPos = readSection[SE_START];
        long readEndPos = readSection[SE_END];

        int extraBaseLength = 0;

        if(region.start() > readStartPos && readEndPos > region.start())
        {
            extraBaseLength = (int)(region.start() - readStartPos);
        }

        if(Cigar.getFirstCigarElement().getOperator() == CigarOperator.S && readStartPos == region.start())
        {
            extraBaseLength += Cigar.getFirstCigarElement().getLength();
        }

        // allow a single base match if only 1 region matches
        if(extraBaseLength >= 1)
        {
            // first check for a match with the next exon on the lower side
            final String extraBases = ReadBases.substring(0, extraBaseLength);

            final List<RegionReadData> matchedRegions = region.getPreRegions().stream()
                    .filter(x -> matchesOtherRegionBases(extraBases, x, false)).collect(Collectors.toList());

            if(matchedRegions.size() == 1 || (matchedRegions.size() > 1 && extraBaseLength >= MIN_BASE_MATCH))
            {
                mMappedRegions.put(region, EXON_BOUNDARY);

                for(RegionReadData preRegion : matchedRegions)
                {
                    // add matched coordinates for this exon and it as a region
                    mMappedRegions.put(preRegion, EXON_BOUNDARY);
                    addInferredMappingRegion(true, preRegion.end() - extraBaseLength + 1, preRegion.end());
                }
            }
        }

        readSection = mUpperInferredAdded ? mMappedCoords.get(mMappedCoords.size() - 2) : mMappedCoords.get(mMappedCoords.size() - 1);
        readStartPos = readSection[SE_START];
        readEndPos = readSection[SE_END];

        extraBaseLength = 0;

        if(readEndPos > region.end() && readStartPos < region.end())
        {
            extraBaseLength = (int)(readEndPos - region.end());
        }

        if(Cigar.getLastCigarElement().getOperator() == CigarOperator.S && readEndPos == region.end())
        {
            extraBaseLength += Cigar.getLastCigarElement().getLength();
        }

        if(extraBaseLength >= 1)
        {
            // now check for a match to the next exon up
            final String extraBases = ReadBases.substring(ReadBases.length() - extraBaseLength, ReadBases.length());

            final List<RegionReadData> matchedRegions = region.getPostRegions().stream()
                    .filter(x -> matchesOtherRegionBases(extraBases, x, true)).collect(Collectors.toList());

            if(matchedRegions.size() == 1 || (matchedRegions.size() > 1 && extraBaseLength >= MIN_BASE_MATCH))
            {
                mMappedRegions.put(region, EXON_BOUNDARY);

                for(RegionReadData postRegion : matchedRegions)
                {
                    mMappedRegions.put(postRegion, EXON_BOUNDARY);
                    addInferredMappingRegion(false, postRegion.start(), postRegion.start() + extraBaseLength - 1);
                }
            }
        }
    }

    private static boolean matchesOtherRegionBases(final String extraBases, final RegionReadData otherRegion, boolean matchToStart)
    {
        int otherRegionLength = otherRegion.length();

        if(extraBases.length() > otherRegionLength)
            return false;

        final String otherRegionBases = matchToStart ? otherRegion.refBases().substring(0, extraBases.length())
                : otherRegion.refBases().substring(otherRegionLength - extraBases.length(), otherRegionLength);

        return (otherRegionBases.equals(extraBases));
    }

    private int mappedRegionCount()
    {
        // discount any inferred regions
        return mMappedCoords.size() - (mLowerInferredAdded ? 1 : 0) - (mUpperInferredAdded ? 1 : 0);
    }

    public boolean inferredCoordAdded(boolean isLower) { return isLower ? mLowerInferredAdded : mUpperInferredAdded; }

    private void addInferredMappingRegion(boolean isLower, long posStart, long posEnd)
    {
        if(isLower)
        {
            if (!mLowerInferredAdded)
            {
                mLowerInferredAdded = true;
                mMappedCoords.add(0, new long[] { posStart, posEnd });
            }
            else
            {
                // lengthen the new region if required
                long[] newSection = mMappedCoords.get(0);
                newSection[SE_START] = min(newSection[SE_START], posStart);
            }
        }
        else
        {
            if(!mUpperInferredAdded)
            {
                mUpperInferredAdded = true;
                mMappedCoords.add(new long[] {posStart, posEnd});
            }
            else
            {
                long[] newSection = mMappedCoords.get(mMappedCoords.size() - 1);
                newSection[SE_END] = max(newSection[SE_END], posEnd);
            }
        }
    }

    public final Map<RegionReadData,RegionMatchType> getMappedRegions() { return mMappedRegions; }

    public final Map<Integer,TransMatchType> getTranscriptClassifications() { return mTranscriptClassification; }

    public TransMatchType getTranscriptClassification(int transId)
    {
        TransMatchType transType = mTranscriptClassification.get(transId);
        return transType != null ? transType : UNKNOWN;
    }

    public String toString()
    {
        return String.format("range(%s: %d -> %d, range=%d) length(%d) cigar(%s) id(%s)",
                Chromosome, PosStart, PosEnd, range(), Length, Cigar != null ? Cigar.toString() : "", Id);
    }

}
