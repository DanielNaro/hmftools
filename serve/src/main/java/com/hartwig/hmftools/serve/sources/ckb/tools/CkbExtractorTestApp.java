package com.hartwig.hmftools.serve.sources.ckb.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.ckb.JsonDatabaseToCkbEntryConverter;
import com.hartwig.hmftools.ckb.classification.CkbClassificationConfig;
import com.hartwig.hmftools.ckb.datamodel.CkbEntry;
import com.hartwig.hmftools.ckb.json.CkbJsonDatabase;
import com.hartwig.hmftools.ckb.json.CkbJsonReader;
import com.hartwig.hmftools.common.serve.classification.EventClassifier;
import com.hartwig.hmftools.common.serve.classification.EventClassifierConfig;
import com.hartwig.hmftools.common.serve.classification.EventClassifierFactory;
import com.hartwig.hmftools.common.serve.classification.EventType;
import com.hartwig.hmftools.serve.sources.ckb.CkbReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

public class CkbExtractorTestApp {

    private static final Logger LOGGER = LogManager.getLogger(CkbExtractorTestApp.class);
    private static final String FIELD_DELIMITER = "\t";

    public static void main(String[] args) throws IOException {
        String ckbDir = "/data/common/dbs/ckb/210319_flex_dump";

        CkbJsonDatabase ckbJsonDatabase = CkbJsonReader.read(ckbDir);
        List<CkbEntry> allCkbEntries = JsonDatabaseToCkbEntryConverter.convert(ckbJsonDatabase);
        List<CkbEntry> filteredAndcurateCkbEntries = CkbReader.filterAndCurateRelevantEntries(allCkbEntries);

        EventClassifierConfig config = CkbClassificationConfig.build();
        EventClassifier classifier = EventClassifierFactory.buildClassifier(config);

        // TODO 1. Make sure every entry has correct event type.

        List<String> lines = Lists.newArrayList();
        String header = new StringJoiner(FIELD_DELIMITER).add("gene").add("event").add("type").toString();
        lines.add(header);

        for (CkbEntry entry : filteredAndcurateCkbEntries) {
            String gene = entry.variants().get(0).gene().geneSymbol();

            EventType type;
            String profileName;
            if (entry.variants().size() > 1) {
                type = EventType.COMBINED;
                profileName = entry.profileName();
            } else {
                if (entry.variants().get(0).variant().equals("fusion") && entry.variants().get(0).impact() != null && entry.variants()
                        .get(0)
                        .impact()
                        .equals("fusion")) {
                    profileName = "fusion promisuous";
                } else if (entry.variants().get(0).impact() != null && entry.variants().get(0).impact().equals("fusion")) {
                    profileName = entry.variants().get(0).variant().replaceAll("\\s+","") + " fusion";
                } else if (entry.variants().get(0).variant().contains("exon")) {
                    profileName = entry.variants().get(0).variant().replace("exon", "exon ");
                }
                else {
                    profileName = entry.variants().get(0).variant() ;
                }

                type = classifier.determineType(gene, profileName);
            }

            lines.add(new StringJoiner(FIELD_DELIMITER).add(gene).add(profileName).add(type.toString()).toString());
        }
        Files.write(new File("/data/common/dbs/serve/pilot_output/events.tsv").toPath(), lines);

        // TODO 2. Make sure every event is extracted correctly using EventExtractor
        //        EventExtractor extractor = EventExtractorFactory.create(config, ....);

        // TODO 3. Create ActionableEvents for all relevant entries.
    }
}
