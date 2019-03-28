package com.hartwig.hmftools.svanalysis.types;

import static com.hartwig.hmftools.svanalysis.types.SvChain.CHAIN_LENGTH;
import static com.hartwig.hmftools.svanalysis.types.SvChain.CHAIN_LINK_COUNT;

import com.hartwig.hmftools.common.variant.structural.annotation.Transcript;

public class RnaFusionData
{
    // data from Star Fusion predictions output:
    public final String Name;
    public final String GeneUp;
    public final String GeneDown;
    public final String ChrUp;
    public final String ChrDown;
    public final long PositionUp;
    public final long PositionDown;
    public final byte StrandUp;
    public final byte StrandDown;

    public final int JunctionReadCount;
    public final int SpanningFragCount;
    public final String SpliceType;

    public static String RNA_SPLICE_TYPE_ONLY_REF = "ONLY_REF_SPLICE";

    // annotations and matching results

    private Transcript mTransUp;
    private Transcript mTransDown;

    // canonical exon positions
    private int mExonMinRankUp;
    private int mExonMaxRankUp;
    private int mExonMinRankDown;
    private int mExonMaxRankDown;

    private boolean mViableFusion; // the pair of transcripts satisfied standard fusion rules
    private boolean mTransViableUp; // the transcript fell in the correct location relative to the RNA position
    private boolean mTransViableDown;
    private boolean mTransCorrectLocationUp; // the transcript fell on the correct side and orientation for the RNA position
    private boolean mTransCorrectLocationDown;
    private int mExonsSkippedUp; // where no valid breakend was found, record the number of exons skipped between the breakend and the RNA
    private int mExonsSkippedDown;

    // SVA match data
    private SvBreakend mBreakendUp;
    private SvBreakend mBreakendDown;

    private String mClusterInfoUp;
    private String mClusterInfoDown;
    private String mChainInfo;

    public RnaFusionData(final String name, final String geneUp, final String geneDown, final String chrUp, final String chrDown,
            long posUp, long posDown, byte strandUp, byte strandDown, int junctionReadCount, int spanningFragCount, final String spliceType)
    {
        Name = name;
        GeneUp = geneUp;
        GeneDown = geneDown;
        ChrUp = chrUp;
        ChrDown = chrDown;
        PositionUp = posUp;
        PositionDown = posDown;
        StrandUp = strandUp;
        StrandDown = strandDown;
        JunctionReadCount = junctionReadCount;
        SpanningFragCount = spanningFragCount;
        SpliceType = spliceType;

        mExonMinRankUp = 0;
        mExonMaxRankUp = 0;
        mExonMinRankDown = 0;
        mExonMaxRankDown = 0;

        mTransUp = null;
        mTransDown = null;
        mBreakendUp = null;
        mBreakendDown = null;

        mViableFusion = false;
        mTransViableUp = false;
        mTransViableDown = false;
        mTransCorrectLocationUp = false;
        mTransCorrectLocationDown = false;
        mExonsSkippedUp = 0;
        mExonsSkippedDown = 0;

        mClusterInfoUp = "";
        mClusterInfoDown = "";
        mChainInfo = "0;0";
    }

    public void setExonUpRank(int min, int max)
    {
        mExonMaxRankUp = max;
        mExonMinRankUp = min;
    }

    public void setExonDownRank(int min, int max)
    {
        mExonMaxRankDown = max;
        mExonMinRankDown = min;
    }

    public int exonMinRankUp() { return mExonMinRankUp; }
    public int exonMaxRankUp() {return mExonMaxRankUp; }
    public int exonMinRankDown() { return mExonMinRankDown; }
    public int exonMaxRankDown() { return mExonMaxRankDown; }

    public void setViableFusion(boolean toggle) { mViableFusion = toggle; }

    public void setTranscriptData(boolean isUpstream, final Transcript trans, final SvBreakend breakend,
            boolean matchedRnaBoundary, boolean correctLocation, int exonsSkipped)
    {
        if(isUpstream)
        {
            mTransUp = trans;
            mBreakendUp = breakend;
            mTransViableUp = matchedRnaBoundary;
            mTransCorrectLocationUp = correctLocation;
            mExonsSkippedUp = exonsSkipped;
        }
        else
        {
            mTransDown = trans;
            mBreakendDown = breakend;
            mTransViableDown = matchedRnaBoundary;
            mTransCorrectLocationDown = correctLocation;
            mExonsSkippedDown = exonsSkipped;
        }
    }

    public final Transcript getTrans(boolean isUpstream) { return isUpstream ? mTransUp : mTransDown; }
    public final SvBreakend getBreakend(boolean isUpstream) { return isUpstream ? mBreakendUp : mBreakendDown; }
    public final boolean isTransViable(boolean isUpstream) { return isUpstream ? mTransViableUp : mTransViableDown; }
    public final boolean isTransCorrectLocation(boolean isUpstream) { return isUpstream ? mTransCorrectLocationDown : mTransCorrectLocationDown; }
    public final int getExonsSkipped(boolean isUpstream) { return isUpstream ? mExonsSkippedUp : mExonsSkippedDown; }

    public boolean isViableFusion() { return mViableFusion; }

    public final String getClusterInfo(boolean isUpstream)
    {
        return isUpstream ? mClusterInfoUp : mClusterInfoDown;
    }

    public final String getChainInfo()
    {
        return mChainInfo;
    }


    public void setFusionClusterChainInfo()
    {
        if(mBreakendUp == null && mBreakendDown == null)
            return;

        if(mBreakendUp != null && mBreakendDown != null)
        {
            SvVarData varUp = mBreakendUp.getSV();
            SvCluster clusterUp = varUp.getCluster();
            SvVarData varDown = mBreakendDown.getSV();
            SvCluster clusterDown = varDown.getCluster();

            SvChain matchingChain = null;

            if(varUp != varDown && clusterUp == clusterDown)
            {
                // check for a matching chain if the clusters are the same
                matchingChain = clusterUp.findSameChainForSVs(varUp, varDown);

                if (matchingChain != null)
                {
                    final int chainData[] =
                            matchingChain.breakendsAreChained(varUp, !mBreakendUp.usesStart(), varDown, !mBreakendDown.usesStart());
                    mChainInfo = String.format("%d;%d", chainData[CHAIN_LINK_COUNT], chainData[CHAIN_LENGTH]);
                }
            }

            setFusionClusterInfo(mBreakendUp, true, matchingChain);
            setFusionClusterInfo(mBreakendDown, false, matchingChain);
        }
        else
        {
            SvBreakend breakend = mBreakendUp != null ? mBreakendUp : mBreakendDown;
            setFusionClusterInfo(breakend, mBreakendUp != null, null);
        }
    }

    private void setFusionClusterInfo(final SvBreakend breakend, boolean isUpstream, SvChain matchingChain)
    {
        // data: ClusterId;ClusterCount;ChainId;ChainCount
        SvCluster cluster = breakend.getSV().getCluster();

        SvChain chain = matchingChain != null ? matchingChain : cluster.findChain(breakend.getSV());

        final String clusterInfo = String.format("%d;%d;%d;%d",
                cluster.id(), cluster.getSvCount(),
                chain != null ? chain.id() : cluster.getChainId(breakend.getSV()), chain != null ? chain.getSvCount() : 1);

        if(isUpstream)
            mClusterInfoUp = clusterInfo;
        else
            mClusterInfoDown = clusterInfo;
    }

}
