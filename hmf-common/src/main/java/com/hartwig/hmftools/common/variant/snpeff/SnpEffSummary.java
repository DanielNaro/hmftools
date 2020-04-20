package com.hartwig.hmftools.common.variant.snpeff;

import com.hartwig.hmftools.common.variant.CodingEffect;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public interface SnpEffSummary {

    @NotNull
    default String gene() {
        return canonicalGene().isEmpty() ? worstGene() : canonicalGene();
    }

    int genesAffected();

    @NotNull
    String canonicalGene();

    @NotNull
    String canonicalEffect();

    @NotNull
    String canonicalTranscript();

    @NotNull
    CodingEffect canonicalCodingEffect();

    @NotNull
    String canonicalHgvsCodingImpact();

    @NotNull
    String canonicalHgvsProteinImpact();

    @NotNull
    String worstGene();

    @NotNull
    String worstEffect();

    @NotNull
    String worstTranscript();

    @NotNull
    CodingEffect worstCodingEffect();

}
