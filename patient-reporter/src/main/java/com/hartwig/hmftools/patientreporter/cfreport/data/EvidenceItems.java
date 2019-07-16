package com.hartwig.hmftools.patientreporter.cfreport.data;

import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.actionability.EvidenceItem;
import com.hartwig.hmftools.common.actionability.EvidenceItemMerger;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class EvidenceItems {

    private EvidenceItems() {
    }

    @NotNull
    public static List<EvidenceItemMerger> sort(@NotNull final List<EvidenceItemMerger> evidenceItems) {
        return evidenceItems.stream().sorted((item1, item2) -> {
            if (item1.level().equals(item2.level())) {
                if (item1.event().equals(item2.event())) {
                    return item1.drug().compareTo(item2.drug());
                } else {
                    return item1.event().compareTo(item2.event());
                }
            } else {
                return item1.level().readableString().compareTo(item2.level().readableString());
            }
        }).collect(Collectors.toList());
    }

    @NotNull
    public static String sourceUrl(@NotNull final EvidenceItemMerger item) {
        String source = item.source().sourceName();
        String reference = item.reference();
        String gene = item.event();
        switch (source.toLowerCase()) {
            case "oncokb":
                String[] geneId = gene.split(" ");
                String referenceFormatting = reference.replace(" ", "%20");
                return "http://oncokb.org/#/gene/" + geneId[0] + "/alteration/" + referenceFormatting;
            case "cgi":
                return "https://www.cancergenomeinterpreter.org/biomarkers";
            case "civic":
                String[] variantId = reference.split(":");
                return "https://civic.genome.wustl.edu/links/variants/" + variantId[1];
            default:
                return Strings.EMPTY;
        }
    }

    public static int uniqueEventCount(@NotNull final List<EvidenceItem> evidenceItems) {
        Set<String> events = Sets.newHashSet();
        for (EvidenceItem evidence : evidenceItems) {
            events.add(evidence.event());
        }
        return events.size();
    }

    public static int uniqueTherapyCount(@NotNull final List<EvidenceItem> evidenceItems) {
        Set<String> drugs = Sets.newHashSet();
        for (EvidenceItem evidence : evidenceItems) {
            drugs.add(evidence.drug());
        }
        return drugs.size();
    }
}
