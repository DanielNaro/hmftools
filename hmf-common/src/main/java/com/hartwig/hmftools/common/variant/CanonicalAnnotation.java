package com.hartwig.hmftools.common.variant;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.drivercatalog.dnds.DndsDriverGeneLikelihoodSupplier;
import com.hartwig.hmftools.common.genome.region.CanonicalTranscript;
import com.hartwig.hmftools.common.genome.region.CanonicalTranscriptFactory;
import com.hartwig.hmftools.common.genome.region.TranscriptRegion;
import com.hartwig.hmftools.common.variant.cosmic.CosmicAnnotation;
import com.hartwig.hmftools.common.variant.snpeff.SnpEffAnnotation;

import org.jetbrains.annotations.NotNull;

public class CanonicalAnnotation {

    private static final String CDKN2A_P14ARF_TRANSCRIPT = "ENST00000361570";

    @NotNull
    private final Set<String> driverCatalogGenes;
    @NotNull
    private final Map<String, String> canonicalTranscriptGeneMap;

    public CanonicalAnnotation() {
        this(CanonicalTranscriptFactory.create37());
    }

    public CanonicalAnnotation(@NotNull final List<CanonicalTranscript> transcripts) {
        this.driverCatalogGenes = Sets.newHashSet(DndsDriverGeneLikelihoodSupplier.tsgLikelihood().keySet());
        this.driverCatalogGenes.addAll(DndsDriverGeneLikelihoodSupplier.oncoLikelihood().keySet());

        // The p14Arf transcript for CDKN2A is included in our canonical transcript map.
        // We need to filter it out since this map assumes only "real" canonical transcripts.
        // See also DEV-783
        this.canonicalTranscriptGeneMap = transcripts.stream()
                .filter(canonicalTranscript -> !canonicalTranscript.transcriptID().equals(CDKN2A_P14ARF_TRANSCRIPT))
                .collect(Collectors.toMap(TranscriptRegion::transcriptID, TranscriptRegion::gene));
    }

    @NotNull
    Optional<CosmicAnnotation> canonicalCosmicAnnotation(@NotNull final List<CosmicAnnotation> cosmicAnnotations) {
        return pickCanonicalFavourDriverGene(cosmicAnnotations);
    }

    @NotNull
    public Optional<SnpEffAnnotation> canonicalSnpEffAnnotation(@NotNull final List<SnpEffAnnotation> allAnnotations) {
        final List<SnpEffAnnotation> transcriptAnnotations =
                allAnnotations.stream().filter(SnpEffAnnotation::isTranscriptFeature).collect(Collectors.toList());
        return pickCanonicalFavourDriverGene(transcriptAnnotations);
    }

    @VisibleForTesting
    @NotNull
    <T extends TranscriptAnnotation> Optional<T> pickCanonicalFavourDriverGene(@NotNull List<T> annotations) {
        List<T> canonicalAnnotations = annotations.stream()
                .filter(annotation -> canonicalTranscriptGeneMap.keySet().contains(trimEnsembleVersion(annotation.transcript())))
                .collect(Collectors.toList());

        if (!canonicalAnnotations.isEmpty()) {
            Optional<T> canonicalOnDriverGene =
                    canonicalAnnotations.stream().filter(annotation -> driverCatalogGenes.contains(annotation.gene())).findFirst();
            if (canonicalOnDriverGene.isPresent()) {
                return canonicalOnDriverGene;
            }

            return Optional.of(canonicalAnnotations.get(0));
        }

        return Optional.empty();
    }

    @NotNull
    static String trimEnsembleVersion(@NotNull final String transcriptId) {
        if (transcriptId.startsWith("EN") && transcriptId.contains(".")) {
            return transcriptId.substring(0, transcriptId.indexOf("."));
        }

        return transcriptId;
    }

}
