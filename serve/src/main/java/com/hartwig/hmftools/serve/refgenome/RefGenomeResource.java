package com.hartwig.hmftools.serve.refgenome;

import java.util.List;
import java.util.Map;

import com.hartwig.hmftools.common.drivercatalog.panel.DriverGene;
import com.hartwig.hmftools.common.fusion.KnownFusionCache;
import com.hartwig.hmftools.common.genome.region.HmfTranscriptRegion;
import com.hartwig.hmftools.common.serve.RefGenomeVersion;
import com.hartwig.hmftools.serve.extraction.hotspot.ProteinResolver;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class RefGenomeResource {

    @NotNull
    public abstract String fastaFile();

    @NotNull
    public abstract List<DriverGene> driverGenes();

    @NotNull
    public abstract KnownFusionCache knownFusionCache();

    @NotNull
    public abstract Map<String, HmfTranscriptRegion> canonicalTranscriptPerGeneMap();

    @NotNull
    public abstract Map<RefGenomeVersion, String> chainToOtherRefGenomeMap();

    @NotNull
    public abstract ProteinResolver proteinResolver();

}
