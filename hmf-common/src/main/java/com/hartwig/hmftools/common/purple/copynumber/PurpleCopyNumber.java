package com.hartwig.hmftools.common.purple.copynumber;

import com.hartwig.hmftools.common.genome.region.GenomeRegion;
import com.hartwig.hmftools.common.purple.segment.SegmentSupport;
import com.hartwig.hmftools.common.utils.Doubles;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class PurpleCopyNumber implements GenomeRegion {

    public abstract int bafCount();

    public abstract double averageActualBAF();

    public abstract double averageObservedBAF();

    public abstract double averageTumorCopyNumber();

    public abstract int depthWindowCount();

    public abstract SegmentSupport segmentStartSupport();

    public abstract SegmentSupport segmentEndSupport();

    public abstract CopyNumberMethod method();

    public abstract double gcContent();

    public double minorAlleleCopyNumber() {
        return Doubles.lessThan(averageActualBAF(), 0.50) ? 0 : Math.max(0, (1 - averageActualBAF()) * averageTumorCopyNumber());
    }

    public double majorAlleleCopyNumber() {
        return averageTumorCopyNumber() - minorAlleleCopyNumber();
    }

    public long length() {
        return end() - start() + 1;
    }

    public abstract long minStart();

    public abstract long maxStart();

    public boolean svSupport() {
        switch (segmentStartSupport()) {
            case NONE:
            case INF:
            case UNKNOWN:
                return false;
        }
        switch (segmentEndSupport()) {
            case NONE:
            case INF:
            case UNKNOWN:
                return false;
        }

        return true;
    }
}