package com.hartwig.hmftools.common.purple.gender;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Multimap;
import com.hartwig.hmftools.common.baf.TumorBAF;
import com.hartwig.hmftools.common.cobalt.CobaltRatio;
import com.hartwig.hmftools.common.cobalt.ReadRatio;
import com.hartwig.hmftools.common.numeric.Doubles;
import com.hartwig.hmftools.common.purple.copynumber.PurpleCopyNumber;
import com.hartwig.hmftools.common.purple.region.ObservedRegion;

import org.jetbrains.annotations.NotNull;

public enum Gender {
    MALE,
    FEMALE;

    private static final int MIN_BAF_COUNT = 1000;

    public static Gender fromAmber(@NotNull final Multimap<String, TumorBAF> bafs) {
        return bafs.get("X").stream().filter(x -> x.position() > 2_699_520 && x.position() < 155_260_560).count() > MIN_BAF_COUNT
                ? FEMALE
                : MALE;
    }

    public static Gender fromReferenceReadRatios(@NotNull final Multimap<String, ReadRatio> readRatios) {
        return fromRatio(readRatios, ReadRatio::ratio);
    }

    public static Gender fromCobalt(@NotNull final Multimap<String, CobaltRatio> readRatios) {
        return fromRatio(readRatios, CobaltRatio::referenceGCRatio);
    }

    private static <T> Gender fromRatio(@NotNull final Multimap<String, T> readRatios, Function<T, Double> transform) {
        return Doubles.greaterThan(median(readRatios.get("X"), transform), 0.75) ? Gender.FEMALE : Gender.MALE;
    }

    public static Gender fromObservedRegions(Collection<ObservedRegion> regions) {
        return regions.stream()
                .filter(x -> x.chromosome().equals("X"))
                .filter(x -> x.end() > 2_699_520 && x.start() < 155_260_560)
                .mapToInt(ObservedRegion::bafCount)
                .sum() > MIN_BAF_COUNT ? FEMALE : MALE;
    }

    public static Gender fromCopyNumbers(Collection<PurpleCopyNumber> regions) {
        return regions.stream()
                .filter(x -> x.chromosome().equals("X"))
                .filter(x -> x.end() > 2_699_520 && x.start() < 155_260_560)
                .mapToInt(PurpleCopyNumber::bafCount)
                .sum() > MIN_BAF_COUNT ? FEMALE : MALE;
    }

    private static <T> double median(Collection<T> readRatios, Function<T, Double> transform) {
        return median(readRatios.stream().map(transform).filter(x -> !Doubles.equal(x, -1)).collect(Collectors.toList()));
    }

    private static double median(List<Double> ratios) {
        Collections.sort(ratios);
        int count = ratios.size();
        return ratios.size() % 2 == 0 ? (ratios.get(count / 2) + ratios.get(count / 2 - 1)) / 2 : ratios.get(count / 2);
    }
}
