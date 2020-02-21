package com.hartwig.hmftools.sage.sam;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.region.GenomeRegion;
import com.hartwig.hmftools.common.genome.region.GenomeRegions;

import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;

public class SamSlicer {

    private static final int MAX_N = 100;

    private final int minMappingQuality;
    private final Collection<GenomeRegion> regions;

    SamSlicer(final int minMappingQuality, @NotNull final GenomeRegion slice) {
        this.minMappingQuality = minMappingQuality;
        this.regions = Collections.singletonList(slice);
    }

    SamSlicer(final int minMappingQuality, @NotNull final GenomeRegion slice, @NotNull final List<GenomeRegion> panel) {
        this.minMappingQuality = minMappingQuality;
        this.regions = Lists.newArrayList();

        for (final GenomeRegion panelRegion : panel) {
            if (slice.chromosome().equals(panelRegion.chromosome()) && panelRegion.start() <= slice.end()
                    && panelRegion.end() >= slice.start()) {

                final GenomeRegion overlap = GenomeRegions.create(slice.chromosome(),
                        Math.max(panelRegion.start(), slice.start()),
                        Math.min(panelRegion.end(), slice.end()));

                regions.add(overlap);
            }
        }
    }

    public void slice(@NotNull final SamReader samReader, @NotNull final Consumer<SAMRecord> consumer) {

        final QueryInterval[] queryIntervals = createIntervals(regions, samReader.getFileHeader());

        try (final SAMRecordIterator iterator = samReader.queryOverlapping(queryIntervals)) {
            while (iterator.hasNext()) {
                final SAMRecord record = iterator.next();
                if (samRecordMeetsQualityRequirements(record) && samRecordMeetsCigarCriteria(record)) {
                    consumer.accept(record);
                }
            }
        }
    }

    @NotNull
    private static QueryInterval[] createIntervals(@NotNull final Collection<GenomeRegion> regions, @NotNull final SAMFileHeader header) {
        final List<QueryInterval> queryIntervals = Lists.newArrayList();
        for (final GenomeRegion region : regions) {
            int sequenceIndex = header.getSequenceIndex(region.chromosome());
            if (sequenceIndex > -1) {
                queryIntervals.add(new QueryInterval(sequenceIndex, (int) region.start(), (int) region.end()));
            }
        }
        return QueryInterval.optimizeIntervals(queryIntervals.toArray(new QueryInterval[queryIntervals.size()]));
    }

    private boolean samRecordMeetsQualityRequirements(@NotNull final SAMRecord record) {
        return record.getMappingQuality() >= minMappingQuality && !record.getReadUnmappedFlag() && !record.getDuplicateReadFlag() && !record
                .isSecondaryOrSupplementary();
    }

    private boolean samRecordMeetsCigarCriteria(@NotNull final SAMRecord record) {
        int totalN = 0;
        for (CigarElement cigarElement : record.getCigar()) {
           if (cigarElement.getOperator().equals(CigarOperator.N)) {
               totalN += cigarElement.getLength();
           }
        }
        return totalN <= MAX_N;
    }
}
