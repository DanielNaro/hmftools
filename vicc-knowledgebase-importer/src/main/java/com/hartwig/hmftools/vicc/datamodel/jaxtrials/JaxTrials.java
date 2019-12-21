package com.hartwig.hmftools.vicc.datamodel.jaxtrials;

import java.util.List;

import com.hartwig.hmftools.vicc.datamodel.KbSpecificObject;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class JaxTrials implements KbSpecificObject {

    @NotNull
    public abstract List<JaxTrialsIndication> indications();

    @NotNull
    public abstract String title();

    @Nullable
    public abstract String gender();

    @NotNull
    public abstract String nctId();

    @NotNull
    public abstract String sponsors();

    @NotNull
    public abstract String recruitment();

    @NotNull
    public abstract String variantRequirements();

    @NotNull
    public abstract String updateDate();

    @NotNull
    public abstract String phase();

    @NotNull
    public abstract List<JaxTrialsVariantRequirementDetails> variantRequirementDetails();

    @NotNull
    public abstract List<JaxTrialsTherapy> therapies();
}
