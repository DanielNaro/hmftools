package com.hartwig.hmftools.protect.evidence;

import static com.hartwig.hmftools.protect.evidence.ProtectEvidenceTestFactory.createTestBaseEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.protect.ProtectEvidence;
import com.hartwig.hmftools.common.variant.msi.MicrosatelliteStatus;
import com.hartwig.hmftools.common.variant.tml.TumorMutationalStatus;
import com.hartwig.hmftools.protect.purple.ImmutablePurpleData;
import com.hartwig.hmftools.protect.purple.PurpleData;
import com.hartwig.hmftools.serve.actionability.signature.ActionableSignature;
import com.hartwig.hmftools.serve.actionability.signature.ImmutableActionableSignature;
import com.hartwig.hmftools.serve.extraction.signature.SignatureName;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class PurpleSignatureEvidenceTest {

    @Test
    public void canDeterminePurpleSignatureEvidence() {
        ActionableSignature signature1 = ImmutableActionableSignature.builder()
                .from(createTestBaseEvent())
                .name(SignatureName.HOMOLOGOUS_RECOMBINATION_DEFICIENT)
                .build();

        ActionableSignature signature2 =
                ImmutableActionableSignature.builder().from(createTestBaseEvent()).name(SignatureName.HIGH_TUMOR_MUTATIONAL_LOAD).build();

        ActionableSignature signature3 =
                ImmutableActionableSignature.builder().from(createTestBaseEvent()).name(SignatureName.MICROSATELLITE_UNSTABLE).build();

        PurpleSignatureEvidence purpleSignatureEvidence =
                new PurpleSignatureEvidence(Lists.newArrayList(signature1, signature2, signature3));

        PurpleData all = createPurpleData(MicrosatelliteStatus.MSI, TumorMutationalStatus.HIGH);
        List<ProtectEvidence> evidence = purpleSignatureEvidence.evidence(Sets.newHashSet(), all);
        assertEquals(2, evidence.size());
        assertTrue(evidence.get(0).reported());
        assertTrue(evidence.get(1).reported());

        PurpleData msi = createPurpleData(MicrosatelliteStatus.MSI, TumorMutationalStatus.LOW);
        assertEquals(1, purpleSignatureEvidence.evidence(Sets.newHashSet(), msi).size());

        PurpleData tmlHigh = createPurpleData(MicrosatelliteStatus.MSS, TumorMutationalStatus.HIGH);
        assertEquals(1, purpleSignatureEvidence.evidence(Sets.newHashSet(), tmlHigh).size());

        PurpleData none = createPurpleData(MicrosatelliteStatus.MSS, TumorMutationalStatus.LOW);
        assertTrue(purpleSignatureEvidence.evidence(Sets.newHashSet(), none).isEmpty());
    }

    @NotNull
    private static PurpleData createPurpleData(@NotNull MicrosatelliteStatus msStatus, @NotNull TumorMutationalStatus tmlStatus) {
        return ImmutablePurpleData.builder()
                .purity(0D)
                .ploidy(0D)
                .microsatelliteIndelsPerMb(0D)
                .microsatelliteStatus(msStatus)
                .tumorMutationalBurdenPerMb(0D)
                .tumorMutationalLoad(0)
                .tumorMutationalLoadStatus(tmlStatus)
                .build();
    }
}