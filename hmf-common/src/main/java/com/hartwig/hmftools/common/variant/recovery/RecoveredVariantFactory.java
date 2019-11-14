package com.hartwig.hmftools.common.variant.recovery;

import static java.util.Comparator.comparingDouble;

import static com.hartwig.hmftools.common.variant.structural.StructuralVariantFactory.create;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantFactory.createSingleBreakend;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantFactory.mateId;

import static htsjdk.tribble.AbstractFeatureReader.getFeatureReader;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.purple.PurityAdjuster;
import com.hartwig.hmftools.common.purple.copynumber.PurpleCopyNumber;
import com.hartwig.hmftools.common.purple.copynumber.sv.StructuralVariantLegPloidyFactory;
import com.hartwig.hmftools.common.utils.Doubles;
import com.hartwig.hmftools.common.variant.structural.StructuralVariant;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantLeg;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantType;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;

class RecoveredVariantFactory implements AutoCloseable {

    private static final double MIN_LENGTH = 1000;
    private static final double MIN_MATE_QUAL_SCORE = 350;
    private static final double MIN_SINGLE_QUAL_SCORE = 1000;

    private static final double MIN_PLOIDY = 0.5;
    private static final double MIN_PLOIDY_AS_PERCENTAGE_OF_COPY_NUMBER_CHANGE = 0.5;

    private static final int MIN_MATE_UNCERTAINTY = 150;
    private static final String AF_FILTERED = "af";

    private static final Comparator<RecoveredVariant> QUALITY_COMPARATOR = comparingDouble(x -> x.context().getPhredScaledQual());

    private final AbstractFeatureReader<VariantContext, LineIterator> reader;
    private final StructuralVariantLegPloidyFactory<PurpleCopyNumber> ploidyFactory;

    RecoveredVariantFactory(@NotNull final PurityAdjuster purityAdjuster, @NotNull final String recoveryVCF) {
        reader = getFeatureReader(recoveryVCF, new VCFCodec(), true);
        ploidyFactory = new StructuralVariantLegPloidyFactory<>(purityAdjuster, PurpleCopyNumber::averageTumorCopyNumber);
    }

    @NotNull
    Optional<RecoveredVariant> recoverVariantAtIndex(int expectedOrientation, double unexplainedCopyNumberChange, int index,
            @NotNull final List<PurpleCopyNumber> copyNumbers) throws IOException {
        if (index <= 0) {
            return Optional.empty();
        }

        final List<RecoveredVariant> all = recoverAllVariantAtIndex(expectedOrientation, unexplainedCopyNumberChange, index, copyNumbers);
        all.sort(QUALITY_COMPARATOR.reversed());
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    @NotNull
    private List<RecoveredVariant> recoverAllVariantAtIndex(int expectedOrientation, double unexplainedCopyNumberChange, int index,
            @NotNull final List<PurpleCopyNumber> copyNumbers) throws IOException {
        assert (index > 1);

        final List<RecoveredVariant> result = Lists.newArrayList();

        final PurpleCopyNumber prev = copyNumbers.get(index - 1);
        final PurpleCopyNumber current = copyNumbers.get(index);
        final long minPosition = Math.max(1, current.minStart() - 1000);
        final long maxPosition = current.maxStart() + 1000;

        final List<VariantContext> recovered = findVariants(current.chromosome(), minPosition, maxPosition);
        for (VariantContext potentialVariant : recovered) {
            final String alt = potentialVariant.getAlternateAllele(0).getDisplayString();

            final String mateId = mateId(potentialVariant);
            final String mateLocation = mateLocation(alt);
            final String mateChromosome = mateChromosome(mateLocation);
            final Long matePosition = matePosition(mateLocation);
            final int uncertainty = uncertainty(potentialVariant);

            final VariantContext mate = mateChromosome != null && matePosition != null && mateId != null ? findMate(mateId,
                    mateChromosome,
                    Math.max(1, matePosition - uncertainty),
                    matePosition + uncertainty) : null;

            final StructuralVariant sv = mate != null ? create(potentialVariant, mate) : createSingleBreakend(potentialVariant);

            final double ploidy = ploidyFactory.singleLegPloidy(sv.start(), prev.averageTumorCopyNumber(), current.averageTumorCopyNumber())
                    .averageImpliedPloidy();

            if (sv.start().orientation() == expectedOrientation && sufficientPloidy(ploidy, unexplainedCopyNumberChange) && hasPotential(sv,
                    copyNumbers)) {
                result.add(ImmutableRecoveredVariant.builder()
                        .context(potentialVariant)
                        .mate(mate)
                        .variant(sv)
                        .copyNumber(current)
                        .prevCopyNumber(prev)
                        .build());
            }
        }

        return result;
    }

    private boolean sufficientPloidy(double ploidy, double unexplainedCopyNumberChange) {
        return Doubles.greaterOrEqual(ploidy, unexplainedCopyNumberChange * MIN_PLOIDY_AS_PERCENTAGE_OF_COPY_NUMBER_CHANGE)
                && Doubles.greaterOrEqual(ploidy, MIN_PLOIDY);
    }

    private int uncertainty(@NotNull final VariantContext context) {
        final int homlen = 2 * context.getAttributeAsInt("HOMLEN", 0);
        final int cipos = cipos(context);
        return Math.max(homlen, cipos);
    }

    private int cipos(@NotNull final VariantContext context) {
        int max = MIN_MATE_UNCERTAINTY;
        if (context.hasAttribute("IMPRECISE")) {

            final String cipos = context.getAttributeAsString("CIPOS", "-0,0");
            if (cipos.contains(",")) {
                for (String s : cipos.split(",")) {
                    try {
                        max = Math.max(max, 2 * Math.abs(Integer.parseInt(s)));
                    } catch (Exception ignored) {

                    }
                }
            }
        }
        return max;
    }

    private boolean hasPotential(@NotNull final StructuralVariant variant, @NotNull final List<PurpleCopyNumber> copyNumbers) {
        StructuralVariantLeg start = variant.start();
        StructuralVariantLeg end = variant.end();

        // This should never actually occur because we are searching within this area
        if (!isInRangeOfCopyNumberSegment(start, copyNumbers)) {
            return false;
        }

        if (end == null) {
            return variant.qualityScore() >= MIN_SINGLE_QUAL_SCORE;
        }

        if (variant.qualityScore() < MIN_MATE_QUAL_SCORE) {
            return false;
        }

        long endPosition = end.position();
        StructuralVariantType type = variant.type();
        if (type == StructuralVariantType.DEL || type == StructuralVariantType.DUP || type == StructuralVariantType.INS) {
            assert (variant.end() != null);

            long length = Math.abs(endPosition - variant.start().position());
            return length >= MIN_LENGTH;
        }

        return true;
    }

    private boolean isInRangeOfCopyNumberSegment(@NotNull final StructuralVariantLeg leg,
            @NotNull final List<PurpleCopyNumber> copyNumbers) {
        final Predicate<PurpleCopyNumber> chrRange = copyNumber -> copyNumber.chromosome().equals(leg.chromosome());
        final Predicate<PurpleCopyNumber> posRange =
                copyNumber -> leg.cnaPosition() >= copyNumber.minStart() - 1000 && leg.cnaPosition() <= copyNumber.maxStart() + 1000;
        return copyNumbers.stream().anyMatch(chrRange.and(posRange));
    }

    @NotNull
    private List<VariantContext> findVariants(@NotNull final String chromosome, final long lowerBound, final long upperBound)
            throws IOException {
        return reader.query(chromosome, (int) lowerBound, (int) upperBound)
                .stream()
                .filter(RecoveredVariantFactory::isAppropriatelyFiltered)
                .collect(Collectors.toList());
    }

    @VisibleForTesting
    static boolean isAppropriatelyFiltered(@NotNull VariantContext variantContext) {
        final Set<String> filters = variantContext.getFilters();
        return !filters.isEmpty() && !filters.contains(AF_FILTERED);
    }

    @NotNull
    private VariantContext findMate(@NotNull final String id, @NotNull final String chromosome, final long min, final long max)
            throws IOException {
        return reader.query(chromosome, (int) min, (int) max)
                .stream()
                .filter(x -> x.getID().equals(id))
                .findFirst()
                .orElseThrow(() -> new IOException("Unable to find mateId " + id + " between " + min + " and " + max));
    }

    @Nullable
    static String mateLocation(@NotNull final String alt) {
        final String bracket;
        if (alt.contains("[")) {
            bracket = "\\[";
        } else if (alt.contains("]")) {
            bracket = "]";
        } else {
            return null;
        }

        String[] results = alt.split(bracket);
        for (String result : results) {
            if (result.contains(":")) {
                return result;
            }
        }
        return null;
    }

    @Nullable
    private static String mateChromosome(@Nullable String mate) {
        return mate == null || !mate.contains(":") ? null : mate.split(":")[0];
    }

    @Nullable
    private static Long matePosition(@Nullable String mate) {
        return mate == null || !mate.contains(":") ? null : Long.valueOf(mate.split(":")[1]);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
