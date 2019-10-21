package com.hartwig.hmftools.patientdb;

import static com.hartwig.hmftools.patientdb.dao.DatabaseUtil.getValueNotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.variant.filter.AlwaysPassFilter;
import com.hartwig.hmftools.common.variant.structural.EnrichedStructuralVariant;
import com.hartwig.hmftools.common.variant.structural.EnrichedStructuralVariantFactory;
import com.hartwig.hmftools.common.variant.structural.EnrichedStructuralVariantLeg;
import com.hartwig.hmftools.common.variant.structural.ImmutableStructuralVariantData;
import com.hartwig.hmftools.common.variant.structural.StructuralVariant;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantData;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantFile;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantFileLoader;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;


public class LoadStructuralVariants {

    private static final Logger LOGGER = LogManager.getLogger(LoadStructuralVariants.class);

    private static final String SAMPLE = "sample";
    private static final String ALIAS = "alias";

    private static final String SV_VCF = "structural_vcf";
    private static final String SV_DATA_DIRECTORY = "sv_data_dir";

    private static final String DB_USER = "db_user";
    private static final String DB_PASS = "db_pass";
    private static final String DB_URL = "db_url";

    public static void main(@NotNull final String[] args) throws ParseException, IOException, SQLException {
        final Options options = createBasicOptions();
        final CommandLine cmd = createCommandLine(args, options);
        final DatabaseAccess dbAccess = databaseAccess(cmd);

        final String tumorSample = cmd.getOptionValue(SAMPLE);
        final String vcfPath = cmd.getOptionValue(SV_VCF);
        final String svDataOutputDir = cmd.getOptionValue(SV_DATA_DIRECTORY);

        LOGGER.info("Reading data from {}", vcfPath);
        final List<StructuralVariant> variants = StructuralVariantFileLoader.fromFile(vcfPath, new AlwaysPassFilter());
        final List<EnrichedStructuralVariant> enrichedVariants = new EnrichedStructuralVariantFactory().enrich(variants);

        // generate a unique ID for each SV record
        int svId = 0;

        List<StructuralVariantData> svDataList = Lists.newArrayList();
        for (EnrichedStructuralVariant var : enrichedVariants) {
            svDataList.add(convertSvData(var, svId++));
        }

        LOGGER.info("Persisting {} SVs to db", svDataList.size());
        dbAccess.writeStructuralVariants(cmd.getOptionValue(ALIAS, tumorSample), svDataList);

        if (svDataOutputDir != null) {
            // write data to file
            try {
                final String svFilename = StructuralVariantFile.generateFilename(svDataOutputDir, tumorSample);
                StructuralVariantFile.write(svFilename, svDataList);
            } catch (IOException e) {
                LOGGER.error("failed to write SV data: {}", e.toString());
            }
        }

        LOGGER.info("Complete");
    }

    @NotNull
    private static StructuralVariantData convertSvData(@NotNull EnrichedStructuralVariant var, int svId) {
        EnrichedStructuralVariantLeg start = var.start();
        EnrichedStructuralVariantLeg end = var.end();
        String filter = var.filter();
        Boolean imprecise = var.imprecise();

        return ImmutableStructuralVariantData.builder()
                .id(svId)
                .startChromosome(start.chromosome())
                .endChromosome(end == null ? "0" : end.chromosome())
                .startPosition(start.position())
                .endPosition(end == null ? -1 : end.position())
                .startOrientation(start.orientation())
                .endOrientation(end == null ? (byte) 0 : end.orientation())
                .startHomologySequence(start.homology())
                .endHomologySequence(end == null ? "" : end.homology())
                .ploidy(getValueNotNull(var.ploidy()))
                .startAF(getValueNotNull(start.alleleFrequency()))
                .endAF(end == null ? 0 : getValueNotNull(end.alleleFrequency()))
                .adjustedStartAF(getValueNotNull(start.adjustedAlleleFrequency()))
                .adjustedEndAF(end == null ? 0 : getValueNotNull(end.adjustedAlleleFrequency()))
                .adjustedStartCopyNumber(getValueNotNull(start.adjustedCopyNumber()))
                .adjustedEndCopyNumber(end == null ? 0 : getValueNotNull(end.adjustedCopyNumber()))
                .adjustedStartCopyNumberChange(getValueNotNull(start.adjustedCopyNumberChange()))
                .adjustedEndCopyNumberChange(end == null ? 0 : getValueNotNull(end.adjustedCopyNumberChange()))
                .insertSequence(var.insertSequence())
                .type(var.type())
                .filter(filter != null ? filter : "Unknown")
                .imprecise(imprecise != null ? imprecise : false)
                .qualityScore(getValueNotNull(var.qualityScore()))
                .event(getValueNotNull(var.event()))
                .startTumorVariantFragmentCount(getValueNotNull(start.tumorVariantFragmentCount()))
                .startTumorReferenceFragmentCount(getValueNotNull(start.tumorReferenceFragmentCount()))
                .startNormalVariantFragmentCount(getValueNotNull(start.normalVariantFragmentCount()))
                .startNormalReferenceFragmentCount(getValueNotNull(start.normalReferenceFragmentCount()))
                .endTumorVariantFragmentCount(end == null ? 0 : getValueNotNull(end.tumorVariantFragmentCount()))
                .endTumorReferenceFragmentCount(end == null ? 0 : getValueNotNull(end.tumorReferenceFragmentCount()))
                .endNormalVariantFragmentCount(end == null ? 0 : getValueNotNull(end.normalVariantFragmentCount()))
                .endNormalReferenceFragmentCount(end == null ? 0 : getValueNotNull(end.normalReferenceFragmentCount()))
                .startIntervalOffsetStart(getValueNotNull(start.startOffset()))
                .startIntervalOffsetEnd(getValueNotNull(start.endOffset()))
                .endIntervalOffsetStart(end == null ? 0 : getValueNotNull(end.startOffset()))
                .endIntervalOffsetEnd(end == null ? 0 : getValueNotNull(end.endOffset()))
                .inexactHomologyOffsetStart(getValueNotNull(start.inexactHomologyOffsetStart()))
                .inexactHomologyOffsetEnd(getValueNotNull(start.inexactHomologyOffsetEnd()))
                .startLinkedBy(getValueNotNull(var.startLinkedBy()))
                .endLinkedBy(getValueNotNull(var.endLinkedBy()))
                .vcfId(getValueNotNull(var.id()))
                .startRefContext(getValueNotNull(start.refGenomeContext()))
                .endRefContext(end == null ? "" : getValueNotNull(end.refGenomeContext()))
                .recovered(var.recovered())
                .recoveryMethod((getValueNotNull(var.recoveryMethod())))
                .recoveryFilter(getValueNotNull(var.recoveryFilter()))
                .insertSequenceAlignments(getValueNotNull(var.insertSequenceAlignments()))
                .insertSequenceRepeatClass(getValueNotNull(var.insertSequenceRepeatClass()))
                .insertSequenceRepeatType(getValueNotNull(var.insertSequenceRepeatType()))
                .insertSequenceRepeatOrientation(getValueNotNull(var.insertSequenceRepeatOrientation()))
                .insertSequenceRepeatCoverage(getValueNotNull(var.insertSequenceRepeatCoverage()))
                .startAnchoringSupportDistance(start.anchoringSupportDistance())
                .endAnchoringSupportDistance(end == null ? 0 : end.anchoringSupportDistance())
                .build();
    }

    @NotNull
    private static Options createBasicOptions() {
        final Options options = new Options();
        options.addOption(SAMPLE, true, "Name of the tumor sample. This should correspond to the value used in PURPLE.");
        options.addOption(ALIAS, true, "Overwrite the sample name with specified alias when writing to db");
        options.addOption(SV_VCF, true, "Path to the PURPLE structural variant VCF file.");
        options.addOption(SV_DATA_DIRECTORY, true, "Optional: directory to write SV data in TSV format");
        options.addOption(DB_USER, true, "Database user name.");
        options.addOption(DB_PASS, true, "Database password.");
        options.addOption(DB_URL, true, "Database url.");

        return options;
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final String[] args, @NotNull final Options options) throws ParseException {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

    @NotNull
    private static DatabaseAccess databaseAccess(@NotNull final CommandLine cmd) throws SQLException {
        final String userName = cmd.getOptionValue(DB_USER);
        final String password = cmd.getOptionValue(DB_PASS);
        final String databaseUrl = cmd.getOptionValue(DB_URL);  //e.g. mysql://localhost:port/database";
        final String jdbcUrl = "jdbc:" + databaseUrl;
        return new DatabaseAccess(userName, password, jdbcUrl);
    }
}
