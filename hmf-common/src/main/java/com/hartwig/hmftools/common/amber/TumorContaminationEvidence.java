package com.hartwig.hmftools.common.amber;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.hotspot.SAMSlicer;
import com.hartwig.hmftools.common.position.GenomePositionSelector;
import com.hartwig.hmftools.common.position.GenomePositionSelectorFactory;
import com.hartwig.hmftools.common.region.GenomeRegion;
import com.hartwig.hmftools.common.region.GenomeRegions;

import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;

public class TumorContaminationEvidence implements Callable<TumorContaminationEvidence> {

    private final String contig;
    private final String bamFile;
    private final SamReaderFactory samReaderFactory;
    private final Map<BaseDepth, ModifiableBaseDepth> evidenceMap;
    private final GenomePositionSelector<ModifiableBaseDepth> selector;
    private final BaseDepthFactory bafFactory;
    private final SAMSlicer supplier;

    public TumorContaminationEvidence(int typicalReadDepth, int minMappingQuality, int minBaseQuality, final String contig,
            final String bamFile, final SamReaderFactory samReaderFactory, final List<BaseDepth> baseDepths) {
        this.bafFactory = new BaseDepthFactory(minBaseQuality);
        this.contig = contig;
        this.bamFile = bamFile;
        this.samReaderFactory = samReaderFactory;
        this.evidenceMap = Maps.newHashMap();

        final List<ModifiableBaseDepth> tumorRecords = Lists.newArrayList();
        for (BaseDepth baseDepth : baseDepths) {
            ModifiableBaseDepth modifiableBaseDepth = BaseDepthFactory.create(baseDepth);
            evidenceMap.put(baseDepth, modifiableBaseDepth);
            tumorRecords.add(modifiableBaseDepth);
        }
        this.selector = GenomePositionSelectorFactory.create(tumorRecords);

        final GenomeRegions builder = new GenomeRegions(contig, typicalReadDepth);
        baseDepths.forEach(x -> builder.addPosition(x.position()));
        this.supplier = new SAMSlicer(minMappingQuality, builder.build());
    }

    @NotNull
    public String contig() {
        return contig;
    }

    @NotNull
    public List<TumorContamination> evidence() {
        final List<TumorContamination> result = Lists.newArrayList();
        for (final Map.Entry<BaseDepth, ModifiableBaseDepth> entry : evidenceMap.entrySet()) {
            final BaseDepth normal = entry.getKey();
            final BaseDepth tumor = entry.getValue();
            if (tumor.altSupport() != 0) {
                result.add(ImmutableTumorContamination.builder().from(normal).normal(normal).tumor(tumor).build());
            }
        }

        return result;
    }

    @Override
    public TumorContaminationEvidence call() throws Exception {

        try (SamReader reader = samReaderFactory.open(new File(bamFile))) {
            supplier.slice(reader, this::record);
        }

        return this;
    }

    private void record(@NotNull final SAMRecord record) {
        selector.select(asRegion(record), bafEvidence -> bafFactory.addEvidence(bafEvidence, record));
    }

    @NotNull
    private static GenomeRegion asRegion(@NotNull final SAMRecord record) {
        return GenomeRegions.create(record.getContig(), record.getAlignmentStart(), record.getAlignmentEnd());
    }
}
