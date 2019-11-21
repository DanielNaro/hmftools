package com.hartwig.hmftools.patientdb;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.lims.ImmutableLimsShallowSeqData;
import com.hartwig.hmftools.common.purple.checkPurpleQuality;
import com.hartwig.hmftools.common.purple.purity.FittedPurityFile;
import com.hartwig.hmftools.common.purple.purity.PurityContext;
import com.hartwig.hmftools.common.purple.qc.PurpleQC;
import com.hartwig.hmftools.common.purple.qc.PurpleQCFile;
import com.hartwig.hmftools.common.utils.io.reader.LineReader;
import com.hartwig.hmftools.patientdb.context.RunContext;
import com.hartwig.hmftools.patientdb.readers.RunsFolderReader;
import com.hartwig.hmftools.common.lims.LimsShallowSeqData;

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

    private static final String SHALLOW_SEQ_CSV = "shallow_seq_csv";
    private static final String PURPLE_PURITY_P4_TSV = "purple_purity_p4_tsv";
    private static final String PURPLE_PURITY_P5_TSV = "purple_purity_p5_tsv";
    private static final String PURPLE_QC_FILE = "purple_qc_file";
    private static final String PURPLE_DIR = "/purple/";
    private static final String DELIMITER = ",";

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

        extraxtPurpleFromSample(runContexts,
                cmd.getOptionValue(SHALLOW_SEQ_CSV),
                cmd.getOptionValue(RUNS_DIRECTORY),
                cmd.getOptionValue(PURPLE_QC_FILE),
                cmd.getOptionValue(PURPLE_PURITY_P4_TSV),
                cmd.getOptionValue(PURPLE_PURITY_P5_TSV));

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

    private static void extraxtPurpleFromSample(@NotNull List<RunContext> runContexts, @NotNull String shallowSeqOutputCsv,
            @NotNull String path, @NotNull String purpleQCFile, @NotNull String purplePurityP4, @NotNull String purplePurityP5) throws IOException {
        for (RunContext runInfo : runContexts) {
            String tumorSample = runInfo.tumorSample();
            String setName = path + "/" + runInfo.setName();
            String sampleBarcode = runInfo.tumorBarcodeSample();

            String purple_purity_tsv_ext = Strings.EMPTY;
            String purple_qc_file_ext = Strings.EMPTY;
            File checkPipelineVersionFile = new File(setName + "/pipeline.version");
            purple_qc_file_ext = purpleQCFile;
            if (checkPipelineVersionFile.exists()) {
                purple_purity_tsv_ext = purplePurityP5;
            } else {
                purple_purity_tsv_ext = purplePurityP4;
            }
            String purple_purity_tsv = setName + PURPLE_DIR + tumorSample + purple_purity_tsv_ext;
            String purple_qc_file = setName + PURPLE_DIR + tumorSample + purple_qc_file_ext;

            PurityContext purityContext = FittedPurityFile.read(purple_purity_tsv);
            PurpleQC purpleQC = PurpleQCFile.read(purple_qc_file);

            boolean QCstatus = checkPurpleQuality.checkHasReliableQuality(purpleQC);
            boolean status = checkPurpleQuality.checkHasReliablePurity(purityContext);
            String purityString = Double.toString(purityContext.bestFit().purity());
            double purity = 0;
            if (purityString.length() == 3) {
                DecimalFormat df = new DecimalFormat("0.00");
                purity = Double.valueOf(df.format(Double.valueOf(Double.toString(purityContext.bestFit().purity()).substring(0,3))));
            } else if (purityString.length() >= 4) {
                purity = Double.valueOf(Double.toString(purityContext.bestFit().purity()).substring(0,4));
            }

            List<LimsShallowSeqData> shallowSeqData = read(shallowSeqOutputCsv);

            boolean inFile = false;
            if (shallowSeqData.size() == 0) {
                String outputStringForFile = sampleBarcode + "," + tumorSample + "," + purity + "," + QCstatus + "," + status + "\n";
                appendToTsv(shallowSeqOutputCsv, outputStringForFile);
                LOGGER.info("Set: " + setName + " with sample barcode: " + sampleBarcode + " is added to shallow seq db!");
            } else {
                for (LimsShallowSeqData sample : shallowSeqData) {
                    if (sample.sampleBarcode().equals(sampleBarcode)) {
                        LOGGER.warn("Sample barcode are already present in file. Skipping set: " + setName + " with sample barcode: "
                                + sampleBarcode + " for writing to shallow seq db!");
                        inFile = true;
                    }
                }
                if (!inFile && !sampleBarcode.equals(Strings.EMPTY)) {
                    String outputStringForFile = sampleBarcode + "," + tumorSample + "," + purity + "," + QCstatus + "," + status + "\n";
                    appendToTsv(shallowSeqOutputCsv, outputStringForFile);
                    LOGGER.info("Set: " + setName + " with sample barcode: " + sampleBarcode + " is added to shallow seq db!");
                }
                if (sampleBarcode.equals(Strings.EMPTY)) {
                    LOGGER.warn("Set: " + setName + " has none known sample barcode!");
                }
            }
        }
    }

    @NotNull
    private static List<RunContext> loadRunContexts(@NotNull String runsDirectory) throws IOException {
        final List<RunContext> runContexts = RunsFolderReader.extractRunContexts(new File(runsDirectory));
        LOGGER.info(" Loaded run contexts from {} ({} sets)", runsDirectory, runContexts.size());

        return runContexts;
    }

    private static void appendToTsv(@NotNull String shallowSeqCsv, @NotNull String stringToAppend) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(shallowSeqCsv, true));
        writer.write(stringToAppend);
        writer.close();
    }

    private static boolean checkInputs(@NotNull CommandLine cmd) {
        final String runsDirectory = cmd.getOptionValue(RUNS_DIRECTORY);

        boolean allParamsPresent = !Utils.anyNull(runsDirectory,
                cmd.getOptionValue(SHALLOW_SEQ_CSV),
                cmd.getOptionValue(PURPLE_PURITY_P4_TSV),
                cmd.getOptionValue(PURPLE_PURITY_P5_TSV),
                cmd.getOptionValue(PURPLE_QC_FILE));

        boolean validRunDirectories = true;
        if (allParamsPresent) {
            final File runDirectoryDb = new File(runsDirectory);

            if (!runDirectoryDb.exists() || !runDirectoryDb.isDirectory()) {
                validRunDirectories = false;
                LOGGER.warn("shallow seq {} does not exist or is not a directory", runDirectoryDb);
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

        options.addOption(SHALLOW_SEQ_CSV, true, "Path towards output file for the shallow seq db CSV.");

        options.addOption(PURPLE_PURITY_P4_TSV, true, "Path towards the purple purity TSV of P4 and lower.");
        options.addOption(PURPLE_PURITY_P5_TSV, true, "Path towards the purple purity TSV of P5.");
        options.addOption(PURPLE_QC_FILE, true, "Path towards the purple qc file.");

        return options;
    }

}
