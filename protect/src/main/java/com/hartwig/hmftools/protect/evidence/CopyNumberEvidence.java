package com.hartwig.hmftools.protect.evidence;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.protect.ProtectEvidence;
import com.hartwig.hmftools.common.purple.copynumber.CopyNumberInterpretation;
import com.hartwig.hmftools.common.purple.copynumber.ReportableGainLoss;
import com.hartwig.hmftools.serve.actionability.gene.ActionableGene;
import com.hartwig.hmftools.serve.extraction.gene.GeneLevelEvent;

import org.jetbrains.annotations.NotNull;

public class CopyNumberEvidence {

    @NotNull
    private final List<ActionableGene> actionableGenes;

    public CopyNumberEvidence(@NotNull final List<ActionableGene> actionableGenes) {
        this.actionableGenes = actionableGenes.stream()
                .filter(x -> x.event() == GeneLevelEvent.INACTIVATION || x.event() == GeneLevelEvent.AMPLIFICATION
                        || x.event() == GeneLevelEvent.DELETION)
                .collect(Collectors.toList());
    }

    @NotNull
    public List<ProtectEvidence> evidence(@NotNull Set<String> doids, @NotNull List<ReportableGainLoss> reportables) {
        List<ProtectEvidence> result = Lists.newArrayList();
        for (ReportableGainLoss reportable : reportables) {
            result.addAll(evidence(doids, reportable));
        }
        return result;
    }

    @NotNull
    private List<ProtectEvidence> evidence(@NotNull Set<String> doids, @NotNull ReportableGainLoss reportable) {
        List<ProtectEvidence> result = Lists.newArrayList();
        for (ActionableGene actionable : actionableGenes) {
            if (actionable.gene().equals(reportable.gene()) && isTypeMatch(actionable, reportable)) {
                ProtectEvidence evidence = ProtectEvidenceFunctions.builder(doids, actionable)
                        .germline(false)
                        .genomicEvent(reportable.genomicEvent())
                        .reported(true)
                        .build();
                result.add(evidence);
            }
        }
        return ProtectEvidenceFunctions.reportHighest(result);
    }

    private static boolean isTypeMatch(@NotNull ActionableGene actionable, @NotNull ReportableGainLoss reportable) {
        switch (actionable.event()) {
            case AMPLIFICATION:
                return reportable.interpretation() == CopyNumberInterpretation.GAIN;
            case INACTIVATION:
            case DELETION:
                return reportable.interpretation() == CopyNumberInterpretation.FULL_LOSS
                        || reportable.interpretation() == CopyNumberInterpretation.PARTIAL_LOSS;
            default:
                return false;
        }
    }
}
