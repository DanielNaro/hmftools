package com.hartwig.hmftools.linx.drivers;

import static com.hartwig.hmftools.linx.types.SvVarData.SE_END;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_PAIR;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_START;

import com.hartwig.hmftools.linx.cn.HomLossEvent;
import com.hartwig.hmftools.linx.cn.LohEvent;
import com.hartwig.hmftools.linx.types.SvBreakend;
import com.hartwig.hmftools.linx.types.SvCluster;

public class DriverGeneEvent
{
    public final DriverEventType Type;

    private SvCluster mCluster;

    private LohEvent mLohEvent;
    private HomLossEvent mHomLossEvent;
    private DriverAmpData mAmpData;

    private SvBreakend[] mBreakendPair;
    private String mSvInfo;

    // types of SVs which caused this event
    public static final String SV_DRIVER_TYPE_TI = "TI";
    public static final String SV_DRIVER_TYPE_FOLDBACK = "FOLDBACK";
    public static final String SV_DRIVER_TYPE_MAX_PLOIDY = "MAX_PLOIDY";
    public static final String SV_DRIVER_TYPE_NET_PLOIDY = "NET_PLOIDY";
    public static final String SV_DRIVER_TYPE_DUP = "DUP";
    public static final String SV_DRIVER_TYPE_DM = "DM";
    public static final String SV_DRIVER_TYPE_DEL = "DEL";
    public static final String SV_DRIVER_TYPE_ARM_SV = "ARM_SV";
    public static final String SV_DRIVER_TYPE_CENTRO_SV = "CENTRO_SV";
    public static final String SV_DRIVER_TYPE_COMPLEX_CLUSTER = "COMPLEX";

    public DriverGeneEvent(DriverEventType type)
    {
        Type = type;

        mCluster = null;
        mLohEvent = null;
        mHomLossEvent = null;
        mAmpData = null;
        mBreakendPair = new SvBreakend[SE_PAIR];

        mSvInfo = "";
    }

    public final LohEvent getLohEvent() { return mLohEvent; }
    public void setLohEvent(final LohEvent event) { mLohEvent = event; }

    public final HomLossEvent getHomLossEvent() { return mHomLossEvent; }
    public void setHomLossEvent(final HomLossEvent event) { mHomLossEvent = event; }

    public final DriverAmpData getAmpData() { return mAmpData; }
    public void setAmpData(final DriverAmpData data) { mAmpData = data; }

    public void setCluster(final SvCluster cluster) { mCluster = cluster; }

    public final SvCluster getCluster()
    {
        if(mCluster != null)
            return mCluster;

        if(mBreakendPair[SE_START] != null)
            return mBreakendPair[SE_START].getCluster();
        else if(mBreakendPair[SE_END] != null)
            return mBreakendPair[SE_END].getCluster();
        else
            return null;
    }

    public final SvBreakend[] getBreakendPair() { return mBreakendPair; }

    public final String getSvInfo() { return mSvInfo; }
    public void setSvInfo(final String info) { mSvInfo = info; }

    public void addSvBreakendPair(final SvBreakend beStart, final SvBreakend beEnd, final String info)
    {
        mBreakendPair[SE_START] = beStart;
        mBreakendPair[SE_END] = beEnd;
        mSvInfo = info;

        if(beStart != null)
            mCluster = beStart.getCluster();
        else if(beEnd != null)
            mCluster = beEnd.getCluster();
    }

}
