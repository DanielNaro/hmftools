package com.hartwig.hmftools.serve.actionability.fusion;

import java.util.Set;

import com.google.common.collect.Sets;
import com.hartwig.hmftools.serve.actionability.util.UrlConsolidator;

import org.jetbrains.annotations.NotNull;

public class ActionableFusionUrlConsolidator implements UrlConsolidator<ActionableFusion> {

    @NotNull
    @Override
    public ActionableFusion stripUrls(@NotNull final ActionableFusion instance) {
        return ImmutableActionableFusion.builder().from(instance).urls(Sets.newHashSet()).build();
    }

    @NotNull
    @Override
    public ActionableFusion buildWithUrls(@NotNull final ActionableFusion instance, @NotNull final Set<String> urls) {
        return ImmutableActionableFusion.builder().from(instance).urls(urls).build();
    }
}
