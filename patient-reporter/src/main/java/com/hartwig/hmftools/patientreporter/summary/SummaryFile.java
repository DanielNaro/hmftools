package com.hartwig.hmftools.patientreporter.summary;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.io.reader.LineReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public final class SummaryFile {

    private static final Logger LOGGER = LogManager.getLogger(SummaryFile.class);
    private static final String SEPARATOR = ";";

    private SummaryFile() {
    }

    @NotNull
    public static SummaryModel buildFromCsv(@NotNull String sampleSummaryCsv) throws IOException {
        List<String> linesSampleSummary = LineReader.build().readLines(new File(sampleSummaryCsv).toPath(), line -> line.length() > 0);

        Map<String, String> sampleToSummaryMap = Maps.newHashMap();

        for (String line : linesSampleSummary) {
            String[] parts = line.split(SEPARATOR);
            if (parts.length == 2) {
                String sampleId = parts[0].trim();
                String summaryOfSample = parts[1].trim();
                summaryOfSample = summaryOfSample.replace("<enter>", "\n");
                sampleToSummaryMap.put(sampleId, summaryOfSample);
                LOGGER.info(summaryOfSample);
            } else {
                LOGGER.warn("Suspicious line detected in sample summary csv: " + line);
            }
        }

        return new SummaryModel(sampleToSummaryMap);
    }
}
