package com.hartwig.hmftools.patientdb;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.lims.ImmutableLimsShallowSeqData;
import com.hartwig.hmftools.common.lims.LimsShallowSeqData;
import com.hartwig.hmftools.common.purple.CheckPurpleQuality;
import com.hartwig.hmftools.common.purple.purity.FittedPurityFile;
import com.hartwig.hmftools.common.purple.purity.PurityContext;
import com.hartwig.hmftools.common.purple.qc.PurpleQC;
import com.hartwig.hmftools.common.purple.qc.PurpleQCFile;
import com.hartwig.hmftools.common.utils.io.reader.LineReader;
import com.hartwig.hmftools.patientdb.context.RunContext;
import com.hartwig.hmftools.patientdb.readers.RunsFolderReader;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

public class CreateShallowSeqDB {

    private static final Logger LOGGER = LogManager.getLogger(CreateShallowSeqDB.class);

    private static final String RUNS_DIRECTORY = "runs_dir";

    private static final String SHALLOW_SEQ_TSV = "shallow_seq_tsv";
    private static final String PURPLE_PURITY_P4_TSV = "purple_purity_p4_tsv";
    private static final String PURPLE_PURITY_P5_TSV = "purple_purity_p5_tsv";
    private static final String PURPLE_QC_FILE = "purple_qc_file";
    private static final String PIPELINE_VERSION = "pipeline_version_file";

    private static final String PURPLE_DIR = "purple";
    private static final String DELIMITER = "\t";

    public static void main(@NotNull final String[] args) throws ParseException, IOException {
        final Options options = createBasicOptions();
        final CommandLine cmd = createCommandLine(args, options);

        if (!checkInputs(cmd)) {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("shallow seq db ", options);
            System.exit(1);
        }

        LOGGER.info("Loading shallow seq runs from {}", cmd.getOptionValue(RUNS_DIRECTORY));
        final List<RunContext> runContexts = loadRunContexts(cmd.getOptionValue(RUNS_DIRECTORY));

        List<LimsShallowSeqData> newShallowSeqEntries = extractNewEntriesForShallowDbFromRunContexts(runContexts,
                cmd.getOptionValue(SHALLOW_SEQ_TSV),
                cmd.getOptionValue(RUNS_DIRECTORY),
                cmd.getOptionValue(PURPLE_QC_FILE),
                cmd.getOptionValue(PURPLE_PURITY_P4_TSV),
                cmd.getOptionValue(PURPLE_PURITY_P5_TSV),
                cmd.getOptionValue(PIPELINE_VERSION));

        appendToCsv(cmd.getOptionValue(SHALLOW_SEQ_TSV), newShallowSeqEntries);

        LOGGER.info("Shallow seq DB is complete!");
    }

    @NotNull
    private static List<LimsShallowSeqData> read(@NotNull String shallowSeqCsv) throws IOException {
        List<String> linesShallowDB = LineReader.build().readLines(new File(shallowSeqCsv).toPath(), line -> line.length() > 0);
        List<LimsShallowSeqData> shallowSeqDataList = Lists.newArrayList();
        for (String line : linesShallowDB.subList(1, linesShallowDB.size())) {
            String[] values = line.split(DELIMITER);

            shallowSeqDataList.add(ImmutableLimsShallowSeqData.builder()
                    .sampleBarcode(values[0])
                    .sampleId(values[1])
                    .purityShallowSeq(values[2])
                    .hasReliableQuality(Boolean.parseBoolean(values[3]))
                    .hasReliablePurity(Boolean.parseBoolean(values[4]))
                    .build());
        }
        return shallowSeqDataList;
    }

    @NotNull
    private static List<LimsShallowSeqData> extractNewEntriesForShallowDbFromRunContexts(@NotNull List<RunContext> runContexts,
            @NotNull String shallowSeqOutputCsv, @NotNull String path, @NotNull String purpleQCFile, @NotNull String purplePurityP4,
            @NotNull String purplePurityP5, @NotNull String pipelineVersion) throws IOException {
        List<LimsShallowSeqData> currentShallowSeqData = read(shallowSeqOutputCsv);

        List<LimsShallowSeqData> shallowSeqDataToAppend = Lists.newArrayList();

        for (RunContext runInfo : runContexts) {
            String tumorSample = runInfo.tumorSample();
            String setPath = path + File.separator + runInfo.setName();
            String sampleBarcode = runInfo.tumorBarcodeSample();

            String purplePurityTsvExt;
            if (new File(setPath + File.separator + pipelineVersion).exists()) {
                purplePurityTsvExt = purplePurityP5;
            } else {
                purplePurityTsvExt = purplePurityP4;
            }
            String fullPurplePurityTsvPath = setPath + File.separator + PURPLE_DIR + File.separator + tumorSample + purplePurityTsvExt;
            String fullPurpleQCFilePath = setPath + File.separator + PURPLE_DIR + File.separator + tumorSample + purpleQCFile;

            PurityContext purityContext = FittedPurityFile.read(fullPurplePurityTsvPath);
            PurpleQC purpleQC = PurpleQCFile.read(fullPurpleQCFilePath);

            boolean hasReliableQuality = CheckPurpleQuality.checkHasReliableQuality(purpleQC);
            boolean hasReliablePurity = CheckPurpleQuality.checkHasReliablePurity(purityContext);
            DecimalFormat decimalFormat = new DecimalFormat("0.00");
            String purity = decimalFormat.format(purityContext.bestFit().purity());

            boolean inFile = false;
            for (LimsShallowSeqData sample : currentShallowSeqData) {
                if (sample.sampleBarcode().equals(sampleBarcode)) {
                    LOGGER.warn("Sample barcode is already present in file. Skipping set: {} with sample barcode: {} for"
                            + " writing to shallow seq db!", setPath, sampleBarcode);
                    inFile = true;
                }
            }
            if (!inFile && !sampleBarcode.equals(Strings.EMPTY)) {
                shallowSeqDataToAppend.add(ImmutableLimsShallowSeqData.builder()
                        .sampleBarcode(sampleBarcode)
                        .sampleId(tumorSample)
                        .purityShallowSeq(purity)
                        .hasReliableQuality(hasReliableQuality)
                        .hasReliablePurity(hasReliablePurity)
                        .build());
                LOGGER.info("Set: {} is added to shallow list!", setPath);
            }
        }
        return shallowSeqDataToAppend;
    }

    @NotNull
    private static List<RunContext> loadRunContexts(@NotNull String runsDirectory) throws IOException {
        final List<RunContext> runContexts = RunsFolderReader.extractRunContexts(new File(runsDirectory), "shallow-seq");
        LOGGER.info(" Loaded run contexts from {} ({} sets)", runsDirectory, runContexts.size());

        return runContexts;
    }

    private static void appendToCsv(@NotNull String shallowSeqCsv, @NotNull List<LimsShallowSeqData> shallowSeqDataToAppend)
            throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(shallowSeqCsv, true));
        for (LimsShallowSeqData dataToAppend : shallowSeqDataToAppend) {
            String outputStringForFile =
                    dataToAppend.sampleBarcode() + DELIMITER + dataToAppend.sampleId() + DELIMITER + dataToAppend.purityShallowSeq()
                            + DELIMITER + dataToAppend.hasReliableQuality() + DELIMITER + dataToAppend.hasReliablePurity() + "\n";
            writer.write(outputStringForFile);
            LOGGER.info("Sample barcode: {} is added to shallow seq db!", dataToAppend.sampleBarcode());

        }
        writer.close();
    }

    private static boolean checkInputs(@NotNull CommandLine cmd) {
        final String runsDirectory = cmd.getOptionValue(RUNS_DIRECTORY);

        boolean allParamsPresent = !Utils.anyNull(runsDirectory,
                cmd.getOptionValue(SHALLOW_SEQ_TSV),
                cmd.getOptionValue(PURPLE_PURITY_P4_TSV),
                cmd.getOptionValue(PURPLE_PURITY_P5_TSV),
                cmd.getOptionValue(PURPLE_QC_FILE),
                cmd.getOptionValue(PIPELINE_VERSION));

        boolean validRunDirectories = true;
        if (allParamsPresent) {
            final File runDirectoryDb = new File(runsDirectory);

            if (!runDirectoryDb.exists() || !runDirectoryDb.isDirectory()) {
                validRunDirectories = false;
                LOGGER.warn("Shallow seq dir {} does not exist or is not a directory", runDirectoryDb);
            }
        }

        return validRunDirectories && allParamsPresent;
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final String[] args, @NotNull final Options options) throws ParseException {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

    @NotNull
    private static Options createBasicOptions() {
        final Options options = new Options();

        options.addOption(RUNS_DIRECTORY, true, "Path towards the folder containing all shallow seq runs .");

        options.addOption(SHALLOW_SEQ_TSV, true, "Path towards output file for the shallow seq db TSV.");

        options.addOption(PURPLE_PURITY_P4_TSV, true, "Path towards the purple purity TSV of P4 and lower.");
        options.addOption(PURPLE_PURITY_P5_TSV, true, "Path towards the purple purity TSV of P5.");
        options.addOption(PURPLE_QC_FILE, true, "Path towards the purple qc file.");

        options.addOption(PIPELINE_VERSION, true, "Path towards the pipeline version");

        return options;
    }
}
