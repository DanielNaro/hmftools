package com.hartwig.hmftools.vicc.datamodel.oncokb;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class OncokbVariant {

    @NotNull
    public abstract String name();

    @NotNull
    public abstract String alteration();

    @NotNull
    public abstract OncoKbConsequence oncoKbConsequence();

    @NotNull
    public abstract String proteinStart();

    @NotNull
    public abstract String proteinEnd();

    @Nullable
    public abstract String refResidues();

    @Nullable
    public abstract String variantResidues();

    @NotNull
    public abstract OncokbGene oncokbGene();
}
