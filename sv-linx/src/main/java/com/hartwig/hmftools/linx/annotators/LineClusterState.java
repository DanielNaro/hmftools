package com.hartwig.hmftools.linx.annotators;

import static java.lang.Math.max;
import static java.lang.Math.min;

import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.NEG_ORIENT;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.POS_ORIENT;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.BND;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.SGL;
import static com.hartwig.hmftools.linx.annotators.LineElementAnnotator.POLY_A_MOTIF;
import static com.hartwig.hmftools.linx.annotators.LineElementAnnotator.POLY_T_MOTIF;
import static com.hartwig.hmftools.linx.annotators.LineElementType.KNOWN;
import static com.hartwig.hmftools.linx.annotators.LineSuspectReason.BND_PAIR_NO_DB;
import static com.hartwig.hmftools.linx.annotators.LineSuspectReason.BND_PAIR_POLY_AT;
import static com.hartwig.hmftools.linx.annotators.LineSuspectReason.BND_SGL_REMOTE_DB_PLUS;
import static com.hartwig.hmftools.linx.annotators.LineSuspectReason.BND_SGL_REMOTE_DB_POLY_AT;
import static com.hartwig.hmftools.linx.annotators.LineSuspectReason.NONE;
import static com.hartwig.hmftools.linx.types.LinxConstants.MIN_DEL_LENGTH;

import java.util.List;
import java.util.stream.Collectors;

import com.hartwig.hmftools.linx.types.SvBreakend;
import com.hartwig.hmftools.linx.types.SvLinkedPair;

import org.apache.commons.compress.utils.Lists;

public class LineClusterState
{
    private final int mProximityDistance;

    private final List<SvBreakend> mBreakends;
    private final List<SvBreakend> mSourceBreakends;
    private final List<SvBreakend> mInsertBreakends;

    private boolean mHasRemoteDB;
    private LineSuspectReason mSuspectReason;

    public LineClusterState(int proximityDistance)
    {
        mBreakends = Lists.newArrayList();
        mSourceBreakends = Lists.newArrayList();
        mInsertBreakends = Lists.newArrayList();
        mProximityDistance = proximityDistance;
        mSuspectReason = NONE;
    }

    public void clear()
    {
        mBreakends.clear();
        mSourceBreakends.clear();
        mInsertBreakends.clear();
        mSuspectReason = NONE;
        mHasRemoteDB = false;
    }

    public boolean isSuspected() { return mSuspectReason != NONE; }
    public LineSuspectReason suspectReason() { return mSuspectReason; }

    public void addBreakend(final SvBreakend breakend)
    {
        if(!mBreakends.isEmpty())
        {
            final SvBreakend lastBreakend = mBreakends.get(mBreakends.size() - 1);

            if(breakend.position() - lastBreakend.position() > mProximityDistance)
                clear();
        }

        mBreakends.add(breakend);

        if(hasLineSourceMotif(breakend))
            mSourceBreakends.add(breakend);
        else if(hasLineInsertMotif(breakend))
            mInsertBreakends.add(breakend);

        mSuspectReason = checkSuspectedCriteria();
    }

    public boolean hasInsertBreakends() { return !mInsertBreakends.isEmpty(); }

    public String toString()
    {
        return String.format("%s: breakends(%d src=%d insert=%d) remoteDB(%s)",
                mSuspectReason, mBreakends.size(), mSourceBreakends.size(), mInsertBreakends.size(), mHasRemoteDB);
    }

    private static final int SPANNING_LOCAL_MIN_DISTANCE = 1000000;

    private LineSuspectReason checkSuspectedCriteria()
    {
        if(mSuspectReason != NONE)
            return mSuspectReason;

        // check 1: 2+ breakends within 5kb with poly-A/poly-T tails with expected orientations for a source site
        if(mSourceBreakends.size() >= 2)
            return BND_PAIR_POLY_AT;

        // 2+ BNDs which are not connected at their remote end to a known LINE site (ie within 5kb) with
        // - at least one not forming a short DB (< 30 bases) AND
        // - at least one breakend within 5kb having a poly-A tail with expected orientation for a source site
        final List<SvBreakend> spanningBreakends = mBreakends.stream()
                .filter(x -> x.type() == BND || (!x.getSV().isSglBreakend() && x.getSV().length() > SPANNING_LOCAL_MIN_DISTANCE))
                .filter(x -> !x.getOtherBreakend().hasLineElement(LineElementType.KNOWN))
                .collect(Collectors.toList());

        if(spanningBreakends.size() >= 2 && !mSourceBreakends.isEmpty())
        {
            if(spanningBreakends.stream().anyMatch(x -> x.getDBLink() == null || x.getDBLink().length() > MIN_DEL_LENGTH))
                return BND_PAIR_NO_DB;
        }

        // at least 1 BND with it’s remote breakend proximity clustered with ONLY 1 single breakend AND forming a short DB AND
        // - EITHER at least one breakend also within 5kb OR
        // - the remote single breakend having a poly-A/poly-T tail with expected orientation for an insertion site
        if(!spanningBreakends.isEmpty())
        {
            for(final SvBreakend breakend : spanningBreakends)
            {
                final SvBreakend otherBreakend = breakend.getOtherBreakend();
                final SvLinkedPair dbLink = otherBreakend.getDBLink();

                if(dbLink == null)
                    continue;

                final SvBreakend remoteOtherBreakend = dbLink.getOtherBreakend(otherBreakend);

                if(remoteOtherBreakend.type() != SGL || !isRemoteIsolatedDeletionBridge(dbLink))
                    continue;

                mHasRemoteDB = true;

                if(mBreakends.size() >= 2)
                    return BND_SGL_REMOTE_DB_PLUS;

                if(hasLineInsertMotif(remoteOtherBreakend))
                {
                    mInsertBreakends.add(remoteOtherBreakend);
                    return BND_SGL_REMOTE_DB_POLY_AT;
                }
            }
        }

        return NONE;
    }

    private static final int INS_SEQ_BUFFER = 5; // ploy A or T motif must be within this distance of the start or end of the sequence

    public static boolean hasLineSourceMotif(final SvBreakend breakend)
    {
        /* For non-SGL SVs:
        1. Orientation has to match still (ie. polyA for positive breakend & poly T for negative breakend)
        2. Tail can be either at the start or the end of the insert sequence (in practice it will normally always be both
        3. If considering the end breakend of the break junction then need to flip As and Ts
        */
        boolean flipOrientation = flipExpectedSequence(breakend);
        boolean eitherEnd = !breakend.getSV().isSglBreakend();
        return hasLinePolyAorTMotif(breakend.getSV().getSvData().insertSequence(), breakend.orientation(), !flipOrientation, eitherEnd);
    }

    public static boolean hasLineInsertMotif(final SvBreakend breakend)
    {
        boolean flipOrientation = flipExpectedSequence(breakend);
        boolean eitherEnd = !breakend.getSV().isSglBreakend();
        return hasLinePolyAorTMotif(breakend.getSV().getSvData().insertSequence(), breakend.orientation(), flipOrientation, eitherEnd);
    }

    private static boolean flipExpectedSequence(final SvBreakend breakend)
    {
        return !breakend.usesStart() && breakend.orientation() == breakend.getOtherBreakend().orientation();
    }

    public static boolean hasLinePolyAorTMotif(final String insSequence, byte orientation, boolean isSource, boolean eitherEnd)
    {
        /*
        We define a poly-A tail as a repeat of 11 or more A within 5 bases from the end of the insert sequence.
        The orientation of the breakend relative to the insertion can help distinguish between the source and insertion site for a mobile element.
        At the mobile element source site, the poly-A tail positive oriented breakends will have the poly-A at the start of the insert sequence, or poly-T
        at the end of the insert sequence for negative oriented breakends (if sourced from the reverse strand).

        Conversely at the insertion site, negative oriented breakends will have poly-A tails at the end of the insert sequence and positive
        oriented breakends have poly-T at the start of the insert sequence (if inserted on the reverse strand)
         */

        int insSeqLength = insSequence.length();

        if(insSeqLength < POLY_A_MOTIF.length())
            return false;

        final String requiredSequence = (orientation == POS_ORIENT) == isSource ? POLY_A_MOTIF : POLY_T_MOTIF;

        if(orientation == POS_ORIENT || eitherEnd)
        {
            final String startSeq = insSequence.substring(0, min(insSeqLength, POLY_A_MOTIF.length() + INS_SEQ_BUFFER));

            if(startSeq.contains(requiredSequence))
                return true;
        }

        if(orientation == NEG_ORIENT || eitherEnd)
        {
            final String endSeq = insSequence.substring(max(0, insSeqLength - (POLY_A_MOTIF.length() + INS_SEQ_BUFFER)), insSeqLength);

            if(endSeq.contains(requiredSequence))
                return true;
        }

        return false;
    }

    private boolean isRemoteIsolatedDeletionBridge(final SvLinkedPair dbPair)
    {
        if(dbPair == null || dbPair.length() > MIN_DEL_LENGTH)
            return false;

        if(dbPair.firstBreakend().hasLineElement(KNOWN) || dbPair.secondBreakend().hasLineElement(KNOWN))
            return false;

        // no other breakends can be within the proximity cut off
        final List<SvBreakend> breakendList = dbPair.first().getCluster().getChrBreakendMap().get(dbPair.chromosome());

        final SvBreakend lowerBreakend = dbPair.getBreakend(true);
        int startIndex = lowerBreakend.getClusterChrPosIndex();

        if(startIndex > 0)
        {
            if(lowerBreakend.position() - breakendList.get(startIndex - 1).position() < mProximityDistance)
                return false;
        }

        final SvBreakend upperBreakend = dbPair.getBreakend(false);
        int endIndex = upperBreakend.getClusterChrPosIndex();

        if(endIndex < breakendList.size() - 1)
        {
            if(breakendList.get(endIndex + 1).position() - upperBreakend.position() < mProximityDistance)
                return false;
        }

        return true;
    }
}
