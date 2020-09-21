package com.hartwig.hmftools.patientreporter.variants;

import java.util.List;

import com.hartwig.hmftools.common.actionability.EvidenceItem;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class ReportableVariantAnalysis {

    @NotNull
    public abstract List<ReportableVariant> variantsToReport();

    @NotNull
    public abstract List<EvidenceItem> evidenceItems();
}
