package com.hartwig.hmftools.compar;

public enum MismatchType
{
    REF_ONLY,
    NEW_ONLY,
    VALUE,
    INVALID_REF, // from a missing or invalid input source
    INVALID_NEW,
    INVALID_BOTH;
}
