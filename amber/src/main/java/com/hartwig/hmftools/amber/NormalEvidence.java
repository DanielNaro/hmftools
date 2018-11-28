package com.hartwig.hmftools.amber;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.hartwig.hmftools.common.amber.ModifiableNormalBAF;
import com.hartwig.hmftools.common.amber.NormalBAFFactory;
import com.hartwig.hmftools.common.collect.Multimaps;
import com.hartwig.hmftools.common.hotspot.SAMSupplier;
import com.hartwig.hmftools.common.position.GenomePositionSelector;
import com.hartwig.hmftools.common.position.GenomePositionSelectorFactory;
import com.hartwig.hmftools.common.region.GenomeRegion;
import com.hartwig.hmftools.common.region.GenomeRegionBuilder;
import com.hartwig.hmftools.common.region.GenomeRegionFactory;

import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;

public class NormalEvidence implements Callable<NormalEvidence> {

    private final String contig;
    private final String bamFile;
    private final SamReaderFactory samReaderFactory;
    private final List<GenomeRegion> bafRegions;
    private final List<ModifiableNormalBAF> evidence;
    private final GenomePositionSelector<ModifiableNormalBAF> selector;
    private final NormalBAFFactory bafFactory;

    NormalEvidence(int minBaseQuality, final String contig, final String bamFile, final SamReaderFactory samReaderFactory, final List<GenomeRegion> bafRegions) {
        this.bafFactory = new NormalBAFFactory(minBaseQuality);
        this.contig = contig;
        this.bamFile = bamFile;
        this.samReaderFactory = samReaderFactory;
        final GenomeRegionBuilder builder = new GenomeRegionBuilder(contig, 1000);
        bafRegions.forEach(x -> builder.addPosition(x.start()));
        this.bafRegions = builder.build();

        this.evidence = bafRegions.stream().map(NormalBAFFactory::create).collect(Collectors.toList());
        this.selector = GenomePositionSelectorFactory.create(Multimaps.fromPositions(evidence));
    }

    @NotNull
    public String contig() {
        return contig;
    }

    @NotNull
    public List<ModifiableNormalBAF> getEvidence() {
        return evidence.stream().filter(x -> x.readDepth() > 0).collect(Collectors.toList());
    }

    @Override
    public NormalEvidence call() throws Exception {

        final SAMSupplier supplier = new SAMSupplier(bafRegions);
        try (SamReader reader = samReaderFactory.open(new File(bamFile))) {
            supplier.readOnce(reader, this::record);
        }

        return this;
    }

    private void record(@NotNull final SAMRecord record) {
        selector.select(asRegion(record), bafEvidence -> bafFactory.addEvidence(bafEvidence, record));
    }

    @NotNull
    private static GenomeRegion asRegion(@NotNull final SAMRecord record) {
        return GenomeRegionFactory.create(record.getContig(), record.getAlignmentStart(), record.getAlignmentEnd());
    }
}
