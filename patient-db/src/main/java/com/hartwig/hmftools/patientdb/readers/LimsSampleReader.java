package com.hartwig.hmftools.patientdb.readers;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.lims.Lims;
import com.hartwig.hmftools.patientdb.data.ImmutableSampleData;
import com.hartwig.hmftools.patientdb.data.SampleData;

import org.jetbrains.annotations.NotNull;

public class LimsSampleReader {
    @NotNull
    private final Lims lims;

    public LimsSampleReader(@NotNull final Lims lims) {
        this.lims = lims;
    }

    @NotNull
    public List<SampleData> read(@NotNull final Set<String> sampleIds) {
        final List<SampleData> limsBiopsies = Lists.newArrayList();

        sampleIds.forEach(sampleId -> {
            final LocalDate arrivalDate = lims.arrivalDate(sampleId);
            if (arrivalDate != null) {
                limsBiopsies.add(ImmutableSampleData.of(sampleId,
                        arrivalDate,
                        lims.samplingDate(sampleId),
                        lims.dnaNanograms(sampleId),
                        lims.primaryTumor(sampleId),
                        lims.pathologyTumorPercentage(sampleId)));
            }
        });
        return limsBiopsies;
    }
}
