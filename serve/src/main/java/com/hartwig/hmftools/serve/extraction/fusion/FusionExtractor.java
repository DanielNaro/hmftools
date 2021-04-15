package com.hartwig.hmftools.serve.extraction.fusion;

import static com.hartwig.hmftools.serve.extraction.fusion.FusionAnnotationConfig.EXONIC_FUSIONS_MAP;

import java.util.List;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import com.hartwig.hmftools.common.fusion.KnownFusionCache;
import com.hartwig.hmftools.common.serve.classification.EventType;
import com.hartwig.hmftools.serve.extraction.util.GeneChecker;

import org.apache.commons.compress.utils.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FusionExtractor {

    private static final Logger LOGGER = LogManager.getLogger(FusionExtractor.class);

    @NotNull
    private final GeneChecker geneChecker;
    @NotNull
    private final KnownFusionCache knownFusionCache;
    @NotNull
    private final Set<String> exonicDelDupFusionKeyPhrases;

    public FusionExtractor(@NotNull final GeneChecker geneChecker, @NotNull final KnownFusionCache knownFusionCache,
            @NotNull final Set<String> exonicDelDupFusionKeyPhrases) {
        this.geneChecker = geneChecker;
        this.knownFusionCache = knownFusionCache;
        this.exonicDelDupFusionKeyPhrases = exonicDelDupFusionKeyPhrases;
    }

    @Nullable
    public KnownFusionPair extract(@NotNull String gene, @NotNull EventType type, @NotNull String event) {
        KnownFusionPair pair = null;
        if (type == EventType.FUSION_PAIR) {
            if (EXONIC_FUSIONS_MAP.containsKey(event)) {
                pair = fromConfiguredPair(EXONIC_FUSIONS_MAP.get(event), gene);
            } else if (hasExonicDelDupKeyPhrase(event)) {
                pair = fromExonicDelDup(gene, event);
            } else {
                pair = fromStandardFusionPairEvent(event);
            }
        } else if (type == EventType.FUSION_PAIR_AND_EXON) {
            pair = fromExonicDelDup(gene, event);
        }

        return validate(pair, type);
    }

    private boolean hasExonicDelDupKeyPhrase(@NotNull String event) {
        for (String keyPhrase : exonicDelDupFusionKeyPhrases) {
            if (event.contains(keyPhrase)) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    private static KnownFusionPair fromExonicDelDup(@NotNull String gene, @NotNull String event) {
        ExonicDelDupType exonicDelDupType = FusionAnnotationConfig.DEL_DUP_TYPE_PER_GENE.get(gene);
        if (exonicDelDupType == null) {
            LOGGER.warn("No exonic del dup type configured for gene '{}'", gene);
            return null;
        }

        Integer exonIndex = extractExonIndex(event);
        if (exonIndex == null) {
            return null;
        }

        int exonUp;
        int exonDown;
        switch (exonicDelDupType) {
            case FULL_EXONIC_DELETION: {
                exonUp = exonIndex - 1;
                exonDown = exonIndex + 1;
                break;
            }
            case PARTIAL_EXONIC_DELETION: {
                exonUp = exonIndex;
                exonDown = exonIndex;
                break;
            }
            default: {
                throw new IllegalStateException("Unrecognized del dup type: " + exonicDelDupType);
            }
        }

        return ImmutableKnownFusionPair.builder()
                .geneUp(gene)
                .minExonUp(exonUp)
                .maxExonUp(exonUp)
                .geneDown(gene)
                .minExonDown(exonDown)
                .maxExonDown(exonDown)
                .build();
    }

    @Nullable
    private static Integer extractExonIndex(@NotNull String event) {
        List<Integer> exons = Lists.newArrayList();
        String[] words = event.split(" ");
        for (String word : words) {
            if (isInteger(word)) {
                exons.add(Integer.valueOf(word));
            }
        }
        if (exons.size() > 1) {
            LOGGER.warn("Multiple exon indices extracted from '{}' while expecting 1", event);
            return null;
        } else if (exons.isEmpty()) {
            LOGGER.warn("No exon index could be resolved from '{}'", event);
            return null;
        }

        return exons.get(0);
    }

    @Nullable
    @VisibleForTesting
    static KnownFusionPair fromStandardFusionPairEvent(@NotNull String event) {
        String[] fusionArray = event.split("-");
        String geneUp = null;
        String geneDown = null;
        if (fusionArray.length == 2) {
            geneUp = fusionArray[0];
            geneDown = fusionArray[1].split(" ")[0];
        } else if (fusionArray.length == 3) {
            // Assume one of the genes looks like "NAME-NUMBER"
            String part3 = fusionArray[2].split(" ")[0];
            if (isInteger(fusionArray[1])) {
                geneUp = fusionArray[0] + "-" + fusionArray[1];
                geneDown = part3;
            } else if (isInteger(part3)) {
                geneUp = fusionArray[0];
                geneDown = fusionArray[1] + "-" + part3;
            }
        }

        if (geneUp == null || geneDown == null) {
            LOGGER.warn("Could not resolve fusion pair from '{}'", event);
            return null;
        }

        return ImmutableKnownFusionPair.builder().geneUp(geneUp.replaceAll("\\s+", "")).geneDown(geneDown.replaceAll("\\s+", "")).build();
    }

    private static boolean isInteger(@NotNull String string) {
        try {
            Integer.parseInt(string);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @NotNull
    private static KnownFusionPair fromConfiguredPair(@NotNull KnownFusionPair configuredPair, @NotNull String gene) {
        KnownFusionPair pair = ImmutableKnownFusionPair.builder().from(configuredPair).build();
        if (!pair.geneUp().equals(gene) || !pair.geneDown().equals(gene)) {
            LOGGER.warn("Preconfigured fusion '{}' does not match on gene level: {}", configuredPair, gene);
            return null;
        }

        return pair;
    }

    @Nullable
    private KnownFusionPair validate(@Nullable KnownFusionPair pair, @NotNull EventType type) {
        if (pair == null) {
            return null;
        }

        if (geneChecker.isValidGene(pair.geneUp()) && geneChecker.isValidGene(pair.geneDown())) {
            if (!isIncludedSomewhereInFusionCache(pair.geneUp(), pair.geneDown())) {
                LOGGER.warn("Fusion '{}-{}' is not part of the known fusion cache", pair.geneUp(), pair.geneDown());
            }
            return pair;
        }

        return null;
    }

    private boolean isIncludedSomewhereInFusionCache(@NotNull String fiveGene, @NotNull String threeGene) {
        return knownFusionCache.hasExonDelDup(fiveGene) || knownFusionCache.hasPromiscuousFiveGene(fiveGene)
                || knownFusionCache.hasPromiscuousThreeGene(threeGene) || knownFusionCache.hasKnownFusion(fiveGene, threeGene)
                || knownFusionCache.hasKnownIgFusion(fiveGene, threeGene) || knownFusionCache.hasPromiscuousIgFusion(fiveGene);
    }
}
