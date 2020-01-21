package com.hartwig.hmftools.sage.phase;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.Consumer;

import com.hartwig.hmftools.common.genome.position.GenomePosition;
import com.hartwig.hmftools.sage.variant.SageVariant;
import com.hartwig.hmftools.sage.vcf.SageVCF;

import org.jetbrains.annotations.NotNull;

public class DedupMnv implements Consumer<SageVariant> {

    private static final int BUFFER = 10;

    private final ArrayDeque<SageVariant> buffer = new ArrayDeque<>();
    private final Consumer<SageVariant> consumer;

    public DedupMnv(final Consumer<SageVariant> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void accept(final SageVariant newVariant) {
        flush(newVariant);

        int lps = newVariant.localPhaseSet();

        if (newVariant.isPassing() && !newVariant.isIndel() && lps > 0) {

            int newVariantSize = newVariant.normal().alt().length();
            for (final SageVariant oldVariant : buffer) {
                if (oldVariant.isPassing() && !oldVariant.isIndel() && oldVariant.localPhaseSet() == lps) {
                    int oldVariantSize = oldVariant.normal().alt().length();
                    if (newVariantSize != oldVariantSize) {
                        final SageVariant shorter;
                        final SageVariant longer;
                        if (newVariantSize > oldVariantSize) {
                            shorter = oldVariant;
                            longer = newVariant;
                        } else {
                            shorter = newVariant;
                            longer = oldVariant;
                        }
                        if (filterShorter(shorter, longer)) {
                            shorter.filters().add(SageVCF.DEDUP_FILTER);
                        }
                    }
                }
            }
        }

        buffer.add(newVariant);
    }

    private static boolean filterShorter(@NotNull final SageVariant shorter, @NotNull final SageVariant longer) {
        long longerStart = longer.position();
        long longerEnd = longer.normal().end();

        long shorterStart = shorter.position();
        long shorterEnd = shorter.normal().end();

        if (shorterStart < longerStart || shorterEnd > longerEnd) {
            return false;
        }

        final String shorterAlt = shorter.normal().alt();

        int offset = (int) (shorterStart - longerStart);
        final String longerAlt = new String(longer.normal().alt().getBytes(), offset, shorter.normal().alt().length());
        return shorterAlt.equals(longerAlt);

    }

    public void flush() {
        buffer.forEach(consumer);
        buffer.clear();
    }

    private void flush(@NotNull final GenomePosition position) {
        final Iterator<SageVariant> iterator = buffer.iterator();
        while (iterator.hasNext()) {
            final SageVariant entry = iterator.next();
            long entryEnd = entry.position() + entry.normal().ref().length() - 1;
            if (!entry.chromosome().equals(position.chromosome()) || entryEnd < position.position() - BUFFER) {
                iterator.remove();
                consumer.accept(entry);
            } else {
                return;
            }
        }
    }

}
