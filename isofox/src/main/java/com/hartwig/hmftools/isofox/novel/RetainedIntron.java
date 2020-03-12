package com.hartwig.hmftools.isofox.novel;

import static com.hartwig.hmftools.linx.analysis.SvUtilities.appendStrList;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.variant.structural.annotation.EnsemblGeneData;
import com.hartwig.hmftools.isofox.common.RegionReadData;
import com.hartwig.hmftools.isofox.common.TransExonRef;

public class RetainedIntron
{
    private final List<RegionReadData> mRegions;
    private final boolean mIsStart;
    private final long mPosition;

    private int mFragmentCount;
    private int mSplicedFragmentCount;
    private int mReadDepth;

    private final String mTranscriptInfo;

    public RetainedIntron(final List<RegionReadData> regions, boolean isStart)
    {
        mRegions = regions;
        mIsStart = isStart;

        mFragmentCount = 0;
        mSplicedFragmentCount = 0;
        mReadDepth = 0;

        mPosition = isStart ? regions.get(0).start() : regions.get(0).end();

        List<String> trancriptInfo = Lists.newArrayList();

        for(RegionReadData region : mRegions)
        {
            for (final TransExonRef transRef : region.getTransExonRefs())
            {
                trancriptInfo.add(String.format("%s-%s", transRef.TransName, transRef.ExonRank));
            }
        }

        mTranscriptInfo = appendStrList(trancriptInfo, ';');
    }

    public final List<RegionReadData> regions() { return mRegions; }
    public boolean isStart() { return mIsStart; }
    public long position() { return mPosition; }

    public String type(boolean isForwardStrand) { return (isForwardStrand == mIsStart) ? "ACCEPTOR" : "DONOR"; }

    public String transcriptInfo() { return mTranscriptInfo; }

    public void addFragmentCount(boolean spliceSupport)
    {
        ++mFragmentCount;

        if(spliceSupport)
            ++mSplicedFragmentCount;
    }

    public int getFragmentCount() { return mFragmentCount; }
    public int getSplicedFragmentCount() { return mSplicedFragmentCount; }

    public void addReadDepth() { ++mReadDepth; }
    public int getDepth() { return mReadDepth; }

    public boolean matches(final RetainedIntron other)
    {
        return other.position() == mPosition && other.isStart() == mIsStart;
    }

    public String toString()
    {
        return String.format("pos(%s @ %s) regions(%d)", mPosition, mIsStart ? "start" : "end", mRegions.size());
    }
}
