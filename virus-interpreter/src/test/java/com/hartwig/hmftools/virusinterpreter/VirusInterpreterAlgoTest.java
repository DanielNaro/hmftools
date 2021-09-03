package com.hartwig.hmftools.virusinterpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.hartwig.hmftools.common.virus.AnnotatedVirus;
import com.hartwig.hmftools.common.virus.VirusBreakend;
import com.hartwig.hmftools.common.virus.VirusBreakendQCStatus;
import com.hartwig.hmftools.common.virus.VirusInterpretation;
import com.hartwig.hmftools.common.virus.VirusTestFactory;
import com.hartwig.hmftools.virusinterpreter.algo.VirusBlacklistModel;
import com.hartwig.hmftools.virusinterpreter.algo.VirusWhitelistModel;
import com.hartwig.hmftools.virusinterpreter.taxonomy.TaxonomyDb;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class VirusInterpreterAlgoTest {

    private static final String RUN_DIRECTORY = Resources.getResource("genomic").getPath();
    private static final String TUMOR_SAMPLE_WGS_METRICS = RUN_DIRECTORY + "/tumor_sample.wgsmetrics";
    private static final String PURPLE_PURITY_TSV = RUN_DIRECTORY + "/sample.purple.purity.tsv";
    private static final String PURPLE_QC_FILE = RUN_DIRECTORY + "/sample.purple.qc";

    @Test
    public void canAnalyzeVirusBreakends() throws IOException {
        List<VirusBreakend> virusBreakends = createTestVirusBreakends();

        String name = "Human papillomavirus type 16";
        Map<Integer, String> taxonomyMap = Maps.newHashMap();
        taxonomyMap.put(1, name);
        TaxonomyDb taxonomyDb = new TaxonomyDb(taxonomyMap);

        Map<Integer, VirusInterpretation> virusInterpretationMap = Maps.newHashMap();
        virusInterpretationMap.put(1, VirusInterpretation.HPV);
        virusInterpretationMap.put(2, VirusInterpretation.EBV);
        VirusWhitelistModel virusWhitelistModel = new VirusWhitelistModel(virusInterpretationMap);

        VirusBlacklistModel virusBlacklistModel = new VirusBlacklistModel(Sets.newHashSet(1), Sets.newHashSet());

        VirusInterpreterAlgo algo = new VirusInterpreterAlgo(taxonomyDb, virusWhitelistModel, virusBlacklistModel);
        List<AnnotatedVirus> annotatedViruses = algo.analyze(virusBreakends, PURPLE_PURITY_TSV, PURPLE_QC_FILE, TUMOR_SAMPLE_WGS_METRICS);
        assertEquals(4, annotatedViruses.size());
        assertEquals(1, annotatedViruses.stream().filter(x -> x.reported()).count());

        AnnotatedVirus reportedVirus = null;
        for (AnnotatedVirus virus : annotatedViruses) {
            if (virus.reported()) {
                reportedVirus = virus;
            }
        }

        assertNotNull(reportedVirus);
        assertEquals(name, reportedVirus.name());
        assertEquals(2, reportedVirus.integrations());
        assertEquals(VirusInterpretation.HPV, reportedVirus.interpretation());
    }

    @NotNull
    private static List<VirusBreakend> createTestVirusBreakends() {
        List<VirusBreakend> virusBreakends = Lists.newArrayList();

        // This one should be added.
        virusBreakends.add(VirusTestFactory.testVirusBreakendBuilder()
                .referenceTaxid(1)
                .taxidGenus(2)
                .taxidSpecies(1)
                .integrations(2)
                .build());

        // This one has a blacklisted genus taxid
        virusBreakends.add(VirusTestFactory.testVirusBreakendBuilder()
                .referenceTaxid(1)
                .taxidGenus(1)
                .taxidSpecies(1)
                .integrations(2)
                .build());

        // This one has a failed QC
        virusBreakends.add(VirusTestFactory.testVirusBreakendBuilder()
                .referenceTaxid(1)
                .taxidGenus(2)
                .taxidSpecies(1)
                .qcStatus(VirusBreakendQCStatus.LOW_VIRAL_COVERAGE)
                .integrations(2)
                .build());

        // This one has no integrations
        virusBreakends.add(VirusTestFactory.testVirusBreakendBuilder()
                .referenceTaxid(1)
                .taxidGenus(2)
                .taxidSpecies(1)
                .integrations(0)
                .build());

        return virusBreakends;
    }
}