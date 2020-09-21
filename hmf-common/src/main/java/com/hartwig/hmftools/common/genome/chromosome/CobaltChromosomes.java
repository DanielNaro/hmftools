package com.hartwig.hmftools.common.genome.chromosome;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.cobalt.MedianRatio;
import com.hartwig.hmftools.common.purple.gender.Gender;
import com.hartwig.hmftools.common.utils.Doubles;

import org.jetbrains.annotations.NotNull;

public class CobaltChromosomes {

    static final int MIN_RATIO_COUNT = 1000;
    static final double TWO_X_CUTOFF = 0.65;
    static final double TWO_Y_CUTOFF = 0.75;
    static final double Y_CUTOFF = 0.05;
    static final double MOSIAC_X_CUTOFF = 0.8;
    static final double TRISOMY_CUTOFF = 1.4;

    private final Gender gender;
    private final Set<GermlineAberration> aberrations;
    private final Map<String, CobaltChromosome> chromosomeMap;

    /**
     * Gender:
     * FEMALE := X >= 0.65 && Y < 0.05
     * MALE := !FEMALE
     * <p>
     * <p>
     * Chromosomal Aberrations:
     * MOSAIC_X := FEMALE && X < min(0.8, minAutosomeMedianDepthRatio*)
     * KLINEFELTER (XXY) := MALE && X >= 0.65
     * XYY SYNDROME := MALE && Y >= 0.75
     * TRISOMY_[X,21,13,18,15] := CHR >= 1.4
     * <p>
     * Expected Ratios:
     * FEMALE -> autosome = 1, X = 1, Y = 0
     * MALE -> autosome = 1, allosome = 0.5
     * MOSAIC_X -> X = median X ratio
     * KLINEFELTER (XXY) -> X = 1, Y = 0.5
     * XYY -> X = 0.5, Y = 1
     * TRISOMY_[X,21,13,18,15] -> CHR >= 1.5
     */

    public CobaltChromosomes(final Collection<MedianRatio> unfiltered) {
        final List<MedianRatio> ratios = unfiltered.stream().filter(x -> x.count() >= MIN_RATIO_COUNT).collect(Collectors.toList());

        this.chromosomeMap = Maps.newHashMap();
        this.aberrations = Sets.newHashSet();
        final double minAutosomeRatio =
                ratios.stream().filter(x -> isAutosome(x.chromosome())).mapToDouble(MedianRatio::medianRatio).min().orElse(1);

        final double yMedian = contigRatio("Y", ratios);
        final double xMedian = contigRatio("X", ratios);
        final boolean isFemale = Doubles.greaterOrEqual(xMedian, TWO_X_CUTOFF) && Doubles.lessThan(yMedian, Y_CUTOFF);
        gender = (isFemale) ? Gender.FEMALE : Gender.MALE;

        for (MedianRatio ratio : ratios) {
            final String contig = ratio.chromosome();
            boolean isX = isContig(contig, "X");
            boolean isY = isContig(contig, "Y");

            final GermlineAberration aberration = aberration(isFemale, contig, ratio.medianRatio(), minAutosomeRatio);
            final double typicalRatio = typicalRatio(isFemale, contig);
            final double actualRatio = actualRatio(aberration, typicalRatio, ratio.medianRatio());

            if (aberration != GermlineAberration.NONE) {
                aberrations.add(aberration);
            }

            if (Doubles.positive(typicalRatio)) {
                CobaltChromosome chromosome = ImmutableCobaltChromosome.builder()
                        .contig(ratio.chromosome())
                        .typicalRatio(typicalRatio)
                        .actualRatio(actualRatio)
                        .isAllosome(isX || isY)
                        .isAutosome(!isX && !isY)
                        .mosiac(aberration == GermlineAberration.MOSAIC_X)
                        .build();

                chromosomeMap.put(contig, chromosome);
            }
        }

        if (aberrations.isEmpty()) {
            aberrations.add(GermlineAberration.NONE);
        }
    }

    public boolean hasGermlineAberrations() {
        return !noAberrations();
    }

    @NotNull
    public Set<GermlineAberration> germlineAberrations() {
        return aberrations;
    }

    @NotNull
    public Gender gender() {
        return gender;
    }

    @NotNull
    public CobaltChromosome get(@NotNull final String contig) {
        return chromosomeMap.get(contig);
    }

    @NotNull
    public Collection<CobaltChromosome> chromosomes() {
        return chromosomeMap.values();
    }

    private static double actualRatio(GermlineAberration aberration, double typicalRatio, double medianRatio) {
        switch (aberration) {
            case TRISOMY_X:
            case TRISOMY_13:
            case TRISOMY_15:
            case TRISOMY_18:
            case TRISOMY_21:
                return 1.5;
            case MOSAIC_X:
                return medianRatio;
            case XYY:
            case KLINEFELTER:
                return 1;
            default:
                return typicalRatio;
        }
    }

    private static GermlineAberration aberration(boolean isFemale, String contig, double medianRatio, double minAutosomeRatio) {

        if (isTrisomy(contig, medianRatio, "X")) {
            return GermlineAberration.TRISOMY_X;
        }

        if (isTrisomy(contig, medianRatio, "13")) {
            return GermlineAberration.TRISOMY_13;
        }

        if (isTrisomy(contig, medianRatio, "15")) {
            return GermlineAberration.TRISOMY_15;
        }

        if (isTrisomy(contig, medianRatio, "18")) {
            return GermlineAberration.TRISOMY_18;
        }

        if (isTrisomy(contig, medianRatio, "21")) {
            return GermlineAberration.TRISOMY_21;
        }

        if (isXYY(isFemale, contig, medianRatio)) {
            return GermlineAberration.XYY;
        }

        if (isMosiacX(isFemale, contig, medianRatio, minAutosomeRatio)) {
            return GermlineAberration.MOSAIC_X;
        }

        if (isKlinefelterXXY(isFemale, contig, medianRatio)) {
            return GermlineAberration.KLINEFELTER;
        }

        return GermlineAberration.NONE;
    }

    private static boolean isKlinefelterXXY(boolean isFemale, String contig, double medianRatio) {
        return !isFemale && isContig(contig, "X") && Doubles.greaterOrEqual(medianRatio, TWO_X_CUTOFF);
    }

    private static boolean isXYY(boolean isFemale, String contig, double medianRatio) {
        return !isFemale && isContig(contig, "Y") && Doubles.greaterOrEqual(medianRatio, TWO_Y_CUTOFF);
    }

    private static boolean isMosiacX(boolean isFemale, String contig, double medianRatio, double minAutosomeRatio) {
        return isFemale && Doubles.lessThan(medianRatio, Math.min(minAutosomeRatio, MOSIAC_X_CUTOFF)) && isContig(contig, "X");
    }

    private static boolean isTrisomy(String contig, double medianRatio, String trisomyContig) {
        return Doubles.greaterOrEqual(medianRatio, TRISOMY_CUTOFF) && isContig(contig, trisomyContig);
    }

    private static double typicalRatio(boolean isFemale, String contig) {
        boolean isX = isContig(contig, "X");
        boolean isY = isContig(contig, "Y");

        if (isFemale) {
            return isY ? 0d : 1d;
        }

        return isX || isY ? 0.5 : 1d;
    }

    public boolean contains(@NotNull final String contig) {
        return chromosomeMap.containsKey(contig);
    }

    static boolean isAutosome(@NotNull final String contig) {
        return !isContig(contig, "X") && !isContig(contig, "Y");
    }

    static boolean isContig(@NotNull final String victim, @NotNull final String contig) {
        return victim.equals(contig) || victim.equals("chr" + contig);
    }

    private double contigRatio(@NotNull final String contig, @NotNull final Collection<MedianRatio> ratios) {
        return ratios.stream()
                .filter(x -> x.count() >= MIN_RATIO_COUNT)
                .filter(x -> isContig(x.chromosome(), contig))
                .mapToDouble(MedianRatio::medianRatio)
                .findFirst()
                .orElse(0);
    }

    private boolean noAberrations() {
        return aberrations.isEmpty() || (aberrations.size() == 1 && aberrations.contains(GermlineAberration.NONE));
    }

}
