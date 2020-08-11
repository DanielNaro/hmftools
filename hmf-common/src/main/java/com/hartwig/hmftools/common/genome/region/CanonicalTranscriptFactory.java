package com.hartwig.hmftools.common.genome.region;

import java.util.Collection;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.genepanel.HmfGenePanelSupplier;

import org.jetbrains.annotations.NotNull;

public final class CanonicalTranscriptFactory {

    private CanonicalTranscriptFactory() {
    }

    @NotNull
    public static List<CanonicalTranscript> create37() {
        return create(HmfGenePanelSupplier.allGeneList37());
    }

    @NotNull
    public static List<CanonicalTranscript> create38() {
        return create(HmfGenePanelSupplier.allGeneList38());
    }

    @NotNull
    public static List<CanonicalTranscript> create(@NotNull Collection<HmfTranscriptRegion> regions) {
        final List<CanonicalTranscript> transcripts = Lists.newArrayList();
        for (final HmfTranscriptRegion region : regions) {
            final CanonicalTranscript transcript = create(region);

            transcripts.add(transcript);
        }

        return transcripts;
    }

    @VisibleForTesting
    @NotNull
    static CanonicalTranscript create(@NotNull HmfTranscriptRegion region) {
        long codingStart = region.codingStart();
        long codingEnd = region.codingEnd();
        long exonStart = 0;
        long exonEnd = 0;
        long exonBases = 0;
        int codingBases = 0;
        int codingExons = 0;

        for (final HmfExonRegion exon : region.exome()) {
            if (exonStart == 0) {
                exonStart = exon.start();
            }

            if (codingStart <= exon.end() && codingEnd >= exon.start()) {
                codingExons++;
                codingBases += Math.min(exon.end(), codingEnd) - Math.max(exon.start(), codingStart) + 1;
            }

            exonBases += exon.bases();
            exonEnd = exon.end();
        }

        return ImmutableCanonicalTranscript.builder()
                .from(region)
                .geneID(region.geneID())
                .geneStart(region.geneStart())
                .geneEnd(region.geneEnd())
                .gene(region.gene())
                .exons(region.exome().size())
                .codingExons(codingExons)
                .exonStart(exonStart)
                .exonEnd(exonEnd)
                .exonBases(exonBases)
                .strand(region.strand())
                .codingStart(codingStart)
                .codingEnd(codingEnd)
                .codingBases(Math.max(0, codingBases - 3))
                .build();
    }
}
