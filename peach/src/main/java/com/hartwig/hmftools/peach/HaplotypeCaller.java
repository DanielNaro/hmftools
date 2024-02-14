package com.hartwig.hmftools.peach;

import com.hartwig.hmftools.peach.event.HaplotypeEvent;
import com.hartwig.hmftools.peach.haplotype.HaplotypeCombination;
import com.hartwig.hmftools.peach.haplotype.NonDefaultHaplotype;
import com.hartwig.hmftools.peach.haplotype.DefaultHaplotype;
import com.hartwig.hmftools.peach.panel.HaplotypePanel;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.hartwig.hmftools.peach.PeachUtils.GERMLINE_TOTAL_COPY_NUMBER;
import static com.hartwig.hmftools.peach.PeachUtils.PCH_LOGGER;

public class HaplotypeCaller
{
    @NotNull
    private final HaplotypePanel haplotypePanel;

    public HaplotypeCaller(@NotNull HaplotypePanel haplotypePanel)
    {
        this.haplotypePanel = haplotypePanel;
    }

    @NotNull
    public Map<String, HaplotypeAnalysis> getGeneToHaplotypeAnalysis(@NotNull Map<String, Integer> eventIdToCount)
    {
        Optional<String> nonPositiveCountEvent =
                eventIdToCount.entrySet().stream().filter(e -> e.getValue() <= 0).map(Map.Entry::getKey).findAny();
        if(nonPositiveCountEvent.isPresent())
        {
            String nonPositiveCountEventName = nonPositiveCountEvent.get();
            String errorMsg = String.format(
                    "Events cannot have a non-positive count: %s -> %d",
                    nonPositiveCountEventName, eventIdToCount.get(nonPositiveCountEventName)
            );
            throw new IllegalArgumentException(errorMsg);
        }
        return haplotypePanel.getGenes().stream().collect(Collectors.toMap(g -> g, g -> getHaplotypeAnalysis(eventIdToCount, g)));
    }

    @NotNull
    private HaplotypeAnalysis getHaplotypeAnalysis(Map<String, Integer> eventIdToCount, String gene)
    {
        PCH_LOGGER.info("handle gene: {}", gene);
        Map<String, Integer> relevantEventIdToCount = eventIdToCount.entrySet()
                .stream()
                .filter(e -> haplotypePanel.isRelevantFor(e.getKey(), gene))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<List<NonDefaultHaplotype>> nonDefaultCombinations =
                getPossibleNonDefaultHaplotypes(relevantEventIdToCount, List.copyOf(haplotypePanel.getNonDefaultHaplotypes(gene)));
        List<HaplotypeCombination> possibleHaplotypeCombinations = nonDefaultCombinations.stream()
                .map(l -> getCombination(l, haplotypePanel.getDefaultHaplotype(gene)))
                .collect(Collectors.toList());
        return new HaplotypeAnalysis(relevantEventIdToCount, possibleHaplotypeCombinations, haplotypePanel.getDefaultHaplotype(gene)
                .getName(), haplotypePanel.getWildTypeHaplotypeName(gene));
    }

    @NotNull
    private List<List<NonDefaultHaplotype>> getPossibleNonDefaultHaplotypes(Map<String, Integer> eventIdToUnexplainedCount,
            List<NonDefaultHaplotype> candidateHaplotypes)
    {
        // Use recursive descent to efficiently go through all possibilities
        if(eventIdToUnexplainedCount.values().stream().anyMatch(c -> c < 0))
        {
            return Collections.emptyList();
        }
        else if(eventIdToUnexplainedCount.values().stream().allMatch(c -> c == 0))
        {
            // return list containing only an empty list
            return new ArrayList<>(List.of(new ArrayList<>()));
        }

        List<List<NonDefaultHaplotype>> results = new ArrayList<>();
        for(int i = 0; i < candidateHaplotypes.size(); i++)
        {
            NonDefaultHaplotype haplotypeToTry = candidateHaplotypes.get(i);

            if(haplotypeIsPossible(eventIdToUnexplainedCount, haplotypeToTry))
            {
                Map<String, Integer> eventIdToUnexplainedCountAfterTry = eventIdToUnexplainedCount.keySet()
                        .stream()
                        .collect(Collectors.toMap(e -> e, e -> eventIdToUnexplainedCount.get(e) - haplotypeToTry.getMatchingCount(e)));

                // To avoid encountering the exact same combination many times, limit the possible candidates for the recursive calls
                List<NonDefaultHaplotype> candidateHaplotypesAfterTry = candidateHaplotypes.subList(i, candidateHaplotypes.size());

                List<List<NonDefaultHaplotype>> subCombinations =
                        getPossibleNonDefaultHaplotypes(eventIdToUnexplainedCountAfterTry, candidateHaplotypesAfterTry);
                List<List<NonDefaultHaplotype>> fullCombinations =
                        subCombinations.stream().peek(l -> l.add(haplotypeToTry)).collect(Collectors.toList());
                results.addAll(fullCombinations);
            }
        }
        return results;
    }

    private static boolean haplotypeIsPossible(final Map<String, Integer> eventIdToUnexplainedCount,
            final NonDefaultHaplotype haplotypeToTry)
    {
        return haplotypeToTry.events.stream()
                .map(HaplotypeEvent::id)
                .allMatch(e -> eventIdToUnexplainedCount.getOrDefault(e, 0) >= haplotypeToTry.getMatchingCount(e));
    }

    @NotNull
    private HaplotypeCombination getCombination(List<NonDefaultHaplotype> nonDefaultHaplotypes, DefaultHaplotype defaultHaplotype)
    {
        Map<String, Integer> haplotypeNameToCount =
                nonDefaultHaplotypes.stream().collect(Collectors.groupingBy(NonDefaultHaplotype::getName, Collectors.summingInt(h -> 1)));
        if(nonDefaultHaplotypes.size() < GERMLINE_TOTAL_COPY_NUMBER)
        {
            haplotypeNameToCount.put(defaultHaplotype.getName(), GERMLINE_TOTAL_COPY_NUMBER - nonDefaultHaplotypes.size());
        }
        return new HaplotypeCombination(haplotypeNameToCount);
    }
}
