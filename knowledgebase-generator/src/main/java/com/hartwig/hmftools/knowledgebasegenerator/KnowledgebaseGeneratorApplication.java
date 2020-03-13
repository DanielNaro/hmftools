package com.hartwig.hmftools.knowledgebasegenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.iclusion.data.IclusionTrial;
import com.hartwig.hmftools.iclusion.io.IclusionTrialFile;
import com.hartwig.hmftools.knowledgebasegenerator.cnv.ActionableAmplificationDeletion;
import com.hartwig.hmftools.knowledgebasegenerator.cnv.KnownAmplificationDeletion;
import com.hartwig.hmftools.knowledgebasegenerator.compassionateuse.CompassionateUseProgram;
import com.hartwig.hmftools.knowledgebasegenerator.compassionateuse.CompassionateUseProgramFile;
import com.hartwig.hmftools.knowledgebasegenerator.eventtype.DetermineEventOfGenomicMutation;
import com.hartwig.hmftools.knowledgebasegenerator.eventtype.EventType;
import com.hartwig.hmftools.knowledgebasegenerator.eventtype.EventTypeAnalyzer;
import com.hartwig.hmftools.knowledgebasegenerator.fusion.KnownFusions;
import com.hartwig.hmftools.knowledgebasegenerator.hotspot.HotspotExtractor;
import com.hartwig.hmftools.knowledgebasegenerator.output.GeneratingOutputFiles;
import com.hartwig.hmftools.vicc.datamodel.ViccEntry;
import com.hartwig.hmftools.vicc.reader.ViccJsonReader;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class KnowledgebaseGeneratorApplication {

    private static final Logger LOGGER = LogManager.getLogger(KnowledgebaseGeneratorApplication.class);
    private static final Set<String> PERMITTED_REF_GENOME_VERSIONS = Sets.newHashSet("hg19");

    private static final String VICC_JSON = "vicc_json";
    private static final String ICLUSION_TRIAL_TSV = "iclusion_trial_tsv";
    private static final String COMPASSIONATE_USE_PROGRAM_TSV = "compassionate_use_program_tsv";

    private static final String REF_GENOME_VERSION = "ref_genome_version";
    private static final String REF_GENOME_FASTA_FILE = "ref_genome_fasta_file";

    private static final String OUTPUT_DIR = "output_dir";

    private static final String VERSION = KnowledgebaseGeneratorApplication.class.getPackage().getImplementationVersion();

    public static void main(String[] args) throws ParseException, IOException, InterruptedException {
        LOGGER.info("Running Knowledgebase Generator v{}", VERSION);

        Options options = createOptions();
        CommandLine cmd = createCommandLine(args, options);

        if (!validInputForKnowledgebaseGeneration(cmd)) {
            printUsageAndExit(options);
        }

        // These are just to test the reading of the files. Handling will happen later.
        readIclusionTrials(cmd.getOptionValue(ICLUSION_TRIAL_TSV));
        readCompassionateUsePrograms(cmd.getOptionValue(COMPASSIONATE_USE_PROGRAM_TSV));

        List<ViccEntry> viccEntries = readViccEntries(cmd.getOptionValue(VICC_JSON));

        //TODO: generate HMF KB

        // Currently only support hg19.
        String refVersionString = cmd.getOptionValue(REF_GENOME_VERSION);
        assert PERMITTED_REF_GENOME_VERSIONS.contains(refVersionString);
        assert refVersionString.equals("hg19");
        RefGenomeVersion refGenomeVersion = RefGenomeVersion.HG19;

        HotspotExtractor hotspotExtractor = HotspotExtractor.withRefGenome(refGenomeVersion, cmd.getOptionValue(REF_GENOME_FASTA_FILE));

        ImmutableAllGenomicEvents.Builder genomicEventsBuilder = ImmutableAllGenomicEvents.builder();

        //Lists of known genomic events
        List<KnownAmplificationDeletion> listKnownAmplification = Lists.newArrayList();
        List<KnownAmplificationDeletion> listKnownDeletion = Lists.newArrayList();
        List<String> listKnownVariants = Lists.newArrayList();
        List<String> listKnownRange = Lists.newArrayList();
        List<KnownFusions> listKnownFusionPairs = Lists.newArrayList();
        List<KnownFusions> listKnownFusionPromiscuousFive = Lists.newArrayList();
        List<KnownFusions> listKnownFusionPromiscuousThree = Lists.newArrayList();
        List<String> listSignatures = Lists.newArrayList();

        //Lists of actionable genomic events
        List<ActionableAmplificationDeletion> listActionableDeletion = Lists.newArrayList();
        List<ActionableAmplificationDeletion> listActionableAmplification = Lists.newArrayList();

        LOGGER.info("Analyzing all VICC entries");
        for (ViccEntry viccEntry : viccEntries) {

            List<EventType> eventType = EventTypeAnalyzer.determineEventType(viccEntry);

            for (EventType type : eventType) {
                // Generating known events
                //TODO: map every genomic event to one object
                //TODO: if combined event use single event for determine known events
                for (String interpetationEvents: type.interpretEventType()) {
                    listKnownAmplification.add(DetermineEventOfGenomicMutation.checkKnownAmplification(viccEntry, type, interpetationEvents));
                    listKnownDeletion.add(DetermineEventOfGenomicMutation.checkKnownDeletion(viccEntry, type, interpetationEvents));
                    DetermineEventOfGenomicMutation.checkVariants(viccEntry, type, interpetationEvents);
                    DetermineEventOfGenomicMutation.checkRange(viccEntry, type, interpetationEvents);
                    listKnownFusionPairs.add(DetermineEventOfGenomicMutation.checkFusions(viccEntry, type, interpetationEvents));
                    listKnownFusionPromiscuousFive.add(DetermineEventOfGenomicMutation.checkFusions(viccEntry, type, interpetationEvents));
                    listKnownFusionPromiscuousThree.add(DetermineEventOfGenomicMutation.checkFusions(viccEntry, type, interpetationEvents));
                    DetermineEventOfGenomicMutation.checkSignatures(viccEntry, type, interpetationEvents);
                }


                // Generating actionable event
                listActionableDeletion.add(DetermineEventOfGenomicMutation.checkActionableDeletion(viccEntry, type));
                listActionableAmplification.add(DetermineEventOfGenomicMutation.checkActionableAmplification(viccEntry, type));

            }
        }
        AllGenomicEvents allGenomicEvents = genomicEventsBuilder.build();

        List<KnownAmplificationDeletion> listAmpsFilter = Lists.newArrayList();
        List<KnownAmplificationDeletion> listDelsFIlter = Lists.newArrayList();
        Set<String> uniqueAmps = Sets.newHashSet();
        Set<String> uniqueDels = Sets.newHashSet();
        for (KnownAmplificationDeletion amps : listKnownAmplification) {
            if (!amps.eventType().isEmpty()) {
                listAmpsFilter.add(amps);
                uniqueAmps.add(amps.gene());
            }
        }

        List<String> sortedUniqueAmps = new ArrayList<String>(uniqueAmps);
        Collections.sort(sortedUniqueAmps);

        for (KnownAmplificationDeletion dels : listKnownDeletion) {
            if (!dels.eventType().isEmpty()) {
                listDelsFIlter.add(dels);
                uniqueDels.add(dels.gene());
            }
        }
        List<String> sortedUniqueDels = new ArrayList<String>(uniqueDels);
        Collections.sort(sortedUniqueDels);

        List<ActionableAmplificationDeletion> listFilterActionableAmplifications = Lists.newArrayList();

        // If drug info/tumor location is known then variant is an actionable variant
        for (ActionableAmplificationDeletion actionableAmplification : listActionableAmplification) {
            if (actionableAmplification.level() != null && actionableAmplification.drug() != null
                    && actionableAmplification.drugType() != null && actionableAmplification.direction() != null
                    && actionableAmplification.sourceLink() != null && actionableAmplification.cancerType() != null) {
                listFilterActionableAmplifications.add(actionableAmplification);
            }
        }

        List<ActionableAmplificationDeletion> listFilterActionableDeletion = Lists.newArrayList();

        for (ActionableAmplificationDeletion actionableDeletion : listActionableDeletion) {
            if (actionableDeletion.level() != null && actionableDeletion.drug() != null && actionableDeletion.drugType() != null
                    && actionableDeletion.direction() != null && actionableDeletion.sourceLink() != null
                    && actionableDeletion.cancerType() != null) {
                listFilterActionableDeletion.add(actionableDeletion);
            }
        }

        AllGenomicEvents finalAllGenomicEvents = ImmutableAllGenomicEvents.builder()
                .knownAmplifications(listAmpsFilter)
                .uniqueAmplification(sortedUniqueAmps)
                .knownDeletions(listDelsFIlter)
                .uniqueDeletions(sortedUniqueDels)
                .actionableAmplification(listFilterActionableAmplifications)
                .actionableDeletion(listFilterActionableDeletion)
                .build();

        // Create all output files from knowledgebase with data
        LOGGER.info("Generating output files");
        GeneratingOutputFiles.generatingOutputFiles(cmd.getOptionValue(OUTPUT_DIR), finalAllGenomicEvents);
    }

    private static void readIclusionTrials(@NotNull String iClusionTrialTsv) throws IOException {
        LOGGER.info("Reading iClusion trials from {}", iClusionTrialTsv);
        List<IclusionTrial> trials = IclusionTrialFile.read(iClusionTrialTsv);
        LOGGER.info(" Read {} iClusion trials", trials.size());
    }

    private static void readCompassionateUsePrograms(@NotNull String compassionateUseProgramTsv) throws IOException {
        LOGGER.info("Reading compassionate use programs from {}", compassionateUseProgramTsv);
        List<CompassionateUseProgram> compassionateUsePrograms = CompassionateUseProgramFile.read(compassionateUseProgramTsv);
        LOGGER.info(" Read {} compassionate use programs", compassionateUsePrograms.size());
    }

    @NotNull
    private static List<ViccEntry> readViccEntries(@NotNull String viccJson) throws IOException {
        LOGGER.info("Reading VICC entries from {}", viccJson);
        List<ViccEntry> viccEntries = ViccJsonReader.readViccKnowledgebaseJsonFile(viccJson);
        LOGGER.info(" Read {} VICC entries", viccEntries.size());
        return viccEntries;
    }

    private static boolean validInputForKnowledgebaseGeneration(@NotNull CommandLine cmd) {
        return fileExists(cmd, ICLUSION_TRIAL_TSV) && fileExists(cmd, VICC_JSON) && fileExists(cmd, COMPASSIONATE_USE_PROGRAM_TSV)
                && paramExists(cmd, REF_GENOME_VERSION) && valueIsPermitted(cmd, REF_GENOME_VERSION, PERMITTED_REF_GENOME_VERSIONS)
                && fileExists(cmd, REF_GENOME_FASTA_FILE) && dirExists(cmd, OUTPUT_DIR);
    }

    private static boolean fileExists(@NotNull CommandLine cmd, @NotNull String param) {
        if (paramExists(cmd, param) && !Files.exists(new File(cmd.getOptionValue(param)).toPath())) {
            LOGGER.warn("{} does not exist while '{}' has to be an existing path", cmd.getOptionValue(param), param);
            return false;
        }

        return true;
    }

    private static boolean dirExists(@NotNull CommandLine cmd, @NotNull String param) {
        String value = cmd.getOptionValue(param);

        if (value == null || !pathExists(value) || !pathIsDirectory(value)) {
            LOGGER.warn(param + " has to be an existing directory: " + value);
            return false;
        }

        return true;
    }

    private static boolean pathExists(@NotNull String path) {
        return Files.exists(new File(path).toPath());
    }

    private static boolean pathIsDirectory(@NotNull String path) {
        return Files.isDirectory(new File(path).toPath());
    }

    private static boolean paramExists(@NotNull CommandLine cmd, @NotNull String param) {
        if (!cmd.hasOption(param)) {
            LOGGER.warn("Param '{}' has to be provided", param);
            return false;
        }

        return true;
    }

    private static boolean valueIsPermitted(@NotNull CommandLine cmd, @NotNull String param, @NotNull Set<String> permittedValues) {
        assert paramExists(cmd, param);
        String value = cmd.getOptionValue(param);

        if (!permittedValues.contains(value)) {
            LOGGER.warn("Value '{}' is not permitted for '{}'", value, param);
            return false;
        }

        return true;
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull String[] args, @NotNull Options options) throws ParseException {
        return new DefaultParser().parse(options, args);
    }

    @NotNull
    private static Options createOptions() {
        Options options = new Options();

        options.addOption(VICC_JSON, true, "VICC JSON knowledgebase");
        options.addOption(ICLUSION_TRIAL_TSV, true, "iClusion input trial tsv");
        options.addOption(COMPASSIONATE_USE_PROGRAM_TSV, true, "Compassionate use program input tsv");

        options.addOption(REF_GENOME_VERSION, true, "Ref version. Should be 'hgxx'");
        options.addOption(REF_GENOME_FASTA_FILE, true, "Path to the ref genome fasta file");

        options.addOption(OUTPUT_DIR, true, "Path to the output dir of the files");

        return options;
    }

    private static void printUsageAndExit(@NotNull Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Knowledgebase-Generator", options);
        System.exit(1);
    }
}
