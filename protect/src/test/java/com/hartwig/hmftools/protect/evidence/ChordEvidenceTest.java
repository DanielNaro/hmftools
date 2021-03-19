package com.hartwig.hmftools.protect.evidence;

import static com.hartwig.hmftools.protect.ProtectTestFactory.createTestEvent;
import static com.hartwig.hmftools.protect.ProtectTestFactory.createTestEvidenceFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.chord.ChordAnalysis;
import com.hartwig.hmftools.common.chord.ChordStatus;
import com.hartwig.hmftools.common.chord.ImmutableChordAnalysis;
import com.hartwig.hmftools.common.protect.ProtectEvidence;
import com.hartwig.hmftools.serve.actionability.characteristic.ActionableSignature;
import com.hartwig.hmftools.serve.actionability.characteristic.ImmutableActionableSignature;
import com.hartwig.hmftools.serve.extraction.signature.SignatureName;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class ChordEvidenceTest {

    @Test
    public void canDetermineEvidenceForCHORD() {
        ActionableSignature signature1 = ImmutableActionableSignature.builder()
                .from(createTestEvent())
                .name(SignatureName.HOMOLOGOUS_RECOMBINATION_DEFICIENT)
                .build();

        ActionableSignature signature2 =
                ImmutableActionableSignature.builder().from(createTestEvent()).name(SignatureName.HIGH_TUMOR_MUTATIONAL_LOAD).build();

        ChordEvidence chordEvidence = new ChordEvidence(createTestEvidenceFactory(), Lists.newArrayList(signature1, signature2));

        ChordAnalysis hrDeficient = chordAnalysisWithStatus(ChordStatus.HR_DEFICIENT);
        List<ProtectEvidence> evidence = chordEvidence.evidence(hrDeficient);

        assertEquals(1, evidence.size());
        assertTrue(evidence.get(0).reported());

        ChordAnalysis hrProficient = chordAnalysisWithStatus(ChordStatus.HR_PROFICIENT);
        assertTrue(chordEvidence.evidence(hrProficient).isEmpty());
    }

    @NotNull
    private static ChordAnalysis chordAnalysisWithStatus(@NotNull ChordStatus status) {
        return ImmutableChordAnalysis.builder()
                .BRCA1Value(0D)
                .BRCA2Value(0D)
                .hrdValue(0D)
                .hrStatus(status)
                .hrdType(Strings.EMPTY)
                .remarksHrStatus(Strings.EMPTY)
                .remarksHrdType(Strings.EMPTY)
                .build();
    }
}