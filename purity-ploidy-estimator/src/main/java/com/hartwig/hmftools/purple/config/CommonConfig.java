package com.hartwig.hmftools.purple.config;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public interface CommonConfig {

    int WINDOW_SIZE = 1000;

    @NotNull
    String refSample();

    @NotNull
    String tumorSample();

    @NotNull
    String outputDirectory();

    @NotNull
    String freecDirectory();

    @NotNull
    String cobaltDirectory();

    @NotNull
    String amberDirectory();

    boolean forceSegmentation();

    default int windowSize() {
        return WINDOW_SIZE;
    }

}
