package com.hartwig.hmftools.isofox.common;

public class TransExonRef
{
    public final int TransId;
    public final String TransName;
    public final int ExonRank;

    public TransExonRef(final int transId, final String transName, final int exonRank)
    {
        TransId = transId;
        TransName = transName;
        ExonRank = exonRank;
    }

    public boolean matches(final TransExonRef other)
    {
        return other.TransId == TransId && other.ExonRank == ExonRank;
    }

    public String toString()
    {
        return String.format("%d:%s:%d", TransId, TransName, ExonRank);
    }

}
