package com.hartwig.hmftools.orange.cohort.mapping;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public final class CohortConstants {

    public static final String COHORT_PAN_CANCER = "Pan-cancer";
    public static final String COHORT_OTHER = "Other";
    public static final String COHORT_UNKNOWN = "Unknown";

    public static final List<Set<String>> DOID_COMBINATIONS_TO_MAP_TO_OTHER = Lists.newArrayList();

    static {
        // The combination of liver cancer and bile duct is occasionally used but not easily mapped.
        DOID_COMBINATIONS_TO_MAP_TO_OTHER.add(Sets.newHashSet("686", "4947"));

        // Kidney cancer is a tough one to spread out without affecting either Kidney or Urothelial tract itself.
        DOID_COMBINATIONS_TO_MAP_TO_OTHER.add(Sets.newHashSet("263"));

        // Combination of Urethra cancer and renal cell cancer cannot easily be mapped.
        DOID_COMBINATIONS_TO_MAP_TO_OTHER.add(Sets.newHashSet("734", "2671", "4450"));
    }

    private CohortConstants() {
    }
}
