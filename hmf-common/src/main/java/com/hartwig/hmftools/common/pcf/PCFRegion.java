package com.hartwig.hmftools.common.pcf;

import com.hartwig.hmftools.common.genome.region.GenomeRegion;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class PCFRegion implements GenomeRegion {
}
