package com.hartwig.hmftools.patientreporter.viralInsertion;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.variant.structural.linx.LinxViralInsertFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public final class ViralInsertionAnalyzer {

    private static final Logger LOGGER = LogManager.getLogger(ViralInsertionAnalyzer.class);

    private ViralInsertionAnalyzer() {

    }

    @NotNull
    public static List<ViralInsertion> loadViralInsertions(@NotNull String viralInsertTsv) throws IOException {
        List<LinxViralInsertFile> viralInsertFileList = LinxViralInsertFile.read(viralInsertTsv);
        LOGGER.info("Loaded {} viral insertions from {}", viralInsertFileList.size(), viralInsertTsv);

        Map<ViralInsertionAnalyzer.VirusKey, List<LinxViralInsertFile>> itemsPerKey = Maps.newHashMap();
        for (LinxViralInsertFile viralInsertion : viralInsertFileList) {
            ViralInsertionAnalyzer.VirusKey key = new ViralInsertionAnalyzer.VirusKey(viralInsertion.VirusId);
            List<LinxViralInsertFile> items = itemsPerKey.get(key);

            if (items == null) {
                items = Lists.newArrayList();
            }
            items.add(viralInsertion);
            itemsPerKey.put(key, items);
        }

        List<ViralInsertion> viralInsertions = Lists.newArrayList();
        for (Map.Entry<ViralInsertionAnalyzer.VirusKey, List<LinxViralInsertFile>> entry : itemsPerKey.entrySet()) {
            List<LinxViralInsertFile> itemsForKey = entry.getValue();

            int count = itemsForKey.size();
            assert count > 0;
            String virusName = itemsForKey.get(0).VirusName;

            viralInsertions.add(ImmutableViralInsertion.builder().virus(virusName).viralInsertionCount(count).build());
        }
        return viralInsertions;
    }

    private static class VirusKey {

        @NotNull
        private final String virusId;

        private VirusKey(@NotNull final String virusId) {
            this.virusId = virusId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ViralInsertionAnalyzer.VirusKey key = (ViralInsertionAnalyzer.VirusKey) o;
            return Objects.equals(virusId, key.virusId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(virusId);
        }
    }
}
