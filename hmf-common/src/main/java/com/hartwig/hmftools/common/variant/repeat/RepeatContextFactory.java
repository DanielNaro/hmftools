package com.hartwig.hmftools.common.variant.repeat;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public final class RepeatContextFactory {

    private static final int MIN_COUNT = 2;
    private static final int MAX_LENGTH = 10;

    private static final Logger LOGGER = LogManager.getLogger(RepeatContextFactory.class);

    private RepeatContextFactory() {
    }

    @NotNull
    public static Optional<RepeatContext> repeats(int index, @NotNull final byte[] sequence) {
        int startIndex = Math.max(0, index - 2 * MAX_LENGTH);
        int actualIndex = Math.min(index, 2 * MAX_LENGTH);
        int length = Math.min(sequence.length - startIndex, 4 * MAX_LENGTH + 1);

        final String sequenceString = new String(sequence, startIndex, length);
        return repeats(actualIndex, sequenceString);
    }

    @NotNull
    public static Optional<RepeatContext> repeats(int index, @NotNull final String sequence) {
        final Map<String, Integer> result = Maps.newHashMap();

        if (sequence.length() >= index) {
            for (int start = Math.max(0, index - MAX_LENGTH); start <= index; start++) {
                final String prior = sequence.substring(0, start);
                final String post = sequence.substring(start);

                for (int end = index; end <= Math.min(sequence.length(), start + MAX_LENGTH); end++) {
                    if (end != index) {
                        int count = 0;
                        final String bases = sequence.substring(Math.min(start, end), Math.max(start, end));

                        count += backwardRepeats(bases, prior);
                        count += forwardRepeats(bases, post);

                        if (count >= MIN_COUNT) {
                            result.merge(bases, count, Math::max);
                        }
                    }
                }
            }
        } else {
            LOGGER.warn("Repeats requested outside of sequence length");
        }
        return result.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).map(RepeatContextFactory::create);
    }


    @NotNull
    private static RepeatContext create(@NotNull Map.Entry<String, Integer> entry) {
        return ImmutableRepeatContext.builder().sequence(entry.getKey()).count(entry.getValue()).build();
    }

    @VisibleForTesting
    static int forwardRepeats(@NotNull final String bases, @NotNull final String sequence) {
        int count = 0;
        int basesLength = bases.length();
        for (int j = 0; j < sequence.length() - basesLength + 1; j += basesLength) {
            final String subSequence = sequence.substring(j, j + basesLength);
            if (!subSequence.equals(bases)) {
                break;
            }
            count++;
        }

        return count;
    }

    @VisibleForTesting
    static int backwardRepeats(@NotNull final String bases, @NotNull final String sequence) {
        int count = 0;
        int basesLength = bases.length();

        for (int j = sequence.length() - basesLength; j >= 0; j -= basesLength) {
            final String subSequence = sequence.substring(j, j + basesLength);
            if (!subSequence.equals(bases)) {
                break;
            }
            count++;
        }

        return count;
    }


}
