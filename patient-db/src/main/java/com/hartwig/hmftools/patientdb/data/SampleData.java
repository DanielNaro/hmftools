package com.hartwig.hmftools.patientdb.data;

import java.time.LocalDate;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class SampleData implements Comparable<SampleData> {

    @NotNull
    public abstract String sampleId();

    public abstract boolean sequenced();

    @NotNull
    public abstract String setName();

    @NotNull
    public abstract LocalDate arrivalDate();

    @Nullable
    public abstract LocalDate samplingDate();

    @Nullable
    public abstract Integer dnaNanograms();

    @Nullable
    public abstract String limsPrimaryTumor();

    @NotNull
    public abstract String pathologyTumorPercentage();

    @NotNull
    public abstract String pathologySampleId();

    @NotNull
    @Value.Derived
    public LocalDate date() {
        LocalDate samplingDate = samplingDate();
        return samplingDate != null ? samplingDate : arrivalDate();
    }

    @Override
    public String toString() {
        return String.format("Sample {%s}", sampleId());
    }

    @Override
    public int compareTo(@NotNull SampleData other) {
        return date().compareTo(other.date());
    }
}
