package com.hartwig.hmftools.protect;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.doid.DiseaseOntology;
import com.hartwig.hmftools.common.doid.DoidParents;
import com.hartwig.hmftools.common.protect.ProtectEvidence;
import com.hartwig.hmftools.common.protect.ProtectEvidenceFile;
import com.hartwig.hmftools.common.serve.RefGenomeVersion;
import com.hartwig.hmftools.serve.actionability.ActionableEvents;
import com.hartwig.hmftools.serve.actionability.ActionableEventsLoader;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class ProtectApplication  {

    private static final Logger LOGGER = LogManager.getLogger(ProtectApplication.class);
    private static final String VERSION = ProtectApplication.class.getPackage().getImplementationVersion();

    private static final RefGenomeVersion REF_GENOME_VERSION = RefGenomeVersion.V37;

    public static void main(@NotNull String[] args) throws IOException {
        LOGGER.info("Running PROTECT v{}", VERSION);

        Options options = ProtectConfig.createOptions();

        try {
            new ProtectApplication(options, args).run();
        } catch (ParseException exception) {
            LOGGER.warn(exception);
            new HelpFormatter().printHelp("PROTECT", options);
            System.exit(1);
        }

        LOGGER.info("Complete");
    }

    @NotNull
    private final ProtectConfig protectConfig;

    private ProtectApplication(final Options options, final String... args) throws ParseException, IOException {
        CommandLine cmd = new DefaultParser().parse(options, args);
        this.protectConfig = ProtectConfig.createConfig(cmd);
    }

    public void run() throws IOException {
        List<ProtectEvidence> evidences = protectEvidence(protectConfig);

        String filename = ProtectEvidenceFile.generateFilename(protectConfig.outputDir(), protectConfig.tumorSampleId());
        LOGGER.info("Writing {} evidence items to file: {}", evidences.size(), filename);
        ProtectEvidenceFile.write(filename, evidences);
    }

    @NotNull
    private static List<ProtectEvidence> protectEvidence(@NotNull ProtectConfig config) throws IOException {
        Set<String> patientTumorDoids = patientTumorDoids(config);

        ActionableEvents actionableEvents = ActionableEventsLoader.readFromDir(config.serveActionabilityDir(), REF_GENOME_VERSION);

        ProtectAlgo algo = ProtectAlgo.buildAlgoFromServeActionability(actionableEvents, patientTumorDoids);

        return algo.run(config);
    }

    @NotNull
    private static Set<String> patientTumorDoids(@NotNull ProtectConfig config) throws IOException {
        Set<String> result = Sets.newHashSet();
        LOGGER.info("Loading DOID file from {}", config.doidJsonFile());
        DoidParents doidParentModel = new DoidParents(DiseaseOntology.readDoidOwlEntryFromDoidJson(config.doidJsonFile()).edges());

        Set<String> initialDoids = config.primaryTumorDoids();
        if (initialDoids.isEmpty()) {
            LOGGER.warn("No doids provided for {}. Every treatment will be considered off-label.", config.tumorSampleId());
            return Sets.newHashSet();
        }

        LOGGER.debug(" Starting doid resolving for patient with initial tumor doids '{}'", initialDoids);
        for (String initialDoid : initialDoids) {
            result.add(initialDoid);
            result.addAll(doidParentModel.parents(initialDoid));
        }

        LOGGER.info(" {} doids which are considered on-label for patient: '{}'", result.size(), result);
        return result;
    }
}
