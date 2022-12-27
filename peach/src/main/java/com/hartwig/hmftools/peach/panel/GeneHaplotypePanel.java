package com.hartwig.hmftools.peach.panel;

import com.google.common.collect.ImmutableList;
import com.hartwig.hmftools.common.genome.chromosome.Chromosome;
import com.hartwig.hmftools.peach.event.HaplotypeEvent;
import com.hartwig.hmftools.peach.event.VariantHaplotypeEvent;
import com.hartwig.hmftools.peach.haplotype.NonWildTypeHaplotype;
import com.hartwig.hmftools.peach.haplotype.WildTypeHaplotype;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GeneHaplotypePanel
{
    @NotNull
    public final WildTypeHaplotype wildTypeHaplotype;
    @NotNull
    public final ImmutableList<NonWildTypeHaplotype> nonWildTypeHaplotypes;

    public GeneHaplotypePanel(
            @NotNull WildTypeHaplotype wildTypeHaplotype,
            @NotNull ImmutableList<NonWildTypeHaplotype> nonWildTypeHaplotypes
    )
    {
        this.wildTypeHaplotype = wildTypeHaplotype;
        this.nonWildTypeHaplotypes = nonWildTypeHaplotypes;
    }

    public Map<Chromosome, Set<Integer>> getRelevantVariantPositions()
    {
        Stream<VariantHaplotypeEvent> variantHaplotypeEvents = nonWildTypeHaplotypes.stream()
                .map(h -> h.events)
                .flatMap(Collection::stream)
                .filter(e -> e instanceof VariantHaplotypeEvent)
                .map(VariantHaplotypeEvent.class::cast);
        return variantHaplotypeEvents.collect(
                Collectors.groupingBy(
                        e -> e.chromosome,
                        Collectors.mapping(VariantHaplotypeEvent::getCoveredPositions, Collectors.flatMapping(Collection::stream, Collectors.toSet()))
                )
        );
    }

    public boolean isRelevantFor(HaplotypeEvent event)
    {
        boolean isEventToIgnore = wildTypeHaplotype.eventsToIgnore.stream()
                .map(HaplotypeEvent::id)
                .anyMatch(e -> e.equals(event.id()));
        return !isEventToIgnore && nonWildTypeHaplotypes.stream().anyMatch(h -> h.isRelevantFor(event));
    }

    public int getHaplotypeCount()
    {
        return nonWildTypeHaplotypes.size() + 1;
    }
}
