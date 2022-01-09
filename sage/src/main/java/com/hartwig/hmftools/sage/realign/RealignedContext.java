package com.hartwig.hmftools.sage.realign;

public class RealignedContext
{
    public final RealignedType Type;
    public final int RepeatCount;

    public RealignedContext(final RealignedType type, final int repeatCount)
    {
        Type = type;
        RepeatCount = repeatCount;
    }
}
