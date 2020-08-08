package com.hartwig.hmftools.patientdb;

import static com.hartwig.hmftools.patientdb.dao.DatabaseAccess.addDatabaseCmdLineArgs;
import static com.hartwig.hmftools.patientdb.dao.DatabaseAccess.databaseAccess;

import java.io.IOException;
import java.sql.SQLException;

import com.hartwig.hmftools.common.variant.SomaticVariantFactory;
import com.hartwig.hmftools.common.variant.filter.SomaticFilter;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;
import com.hartwig.hmftools.patientdb.dao.SomaticVariantStreamWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import htsjdk.variant.variantcontext.filter.CompoundFilter;
import htsjdk.variant.variantcontext.filter.PassingVariantFilter;

public class LoadSomaticVariants {

    private static final Logger LOGGER = LogManager.getLogger(LoadSomaticVariants.class);

    private static final String SAMPLE = "sample";
    private static final String REFERENCE = "reference";
    private static final String RNA = "rna";

    private static final String SOMATIC_VCF = "somatic_vcf";
    private static final String HOTSPOT_TSV = "hotspot_tsv";
    private static final String HIGH_CONFIDENCE_BED = "high_confidence_bed";

    private static final String PASS_FILTER = "pass_filter";
    private static final String SOMATIC_FILTER = "somatic_filter";

    public static void main(@NotNull String[] args) throws ParseException, IOException, SQLException {
        Options options = createBasicOptions();
        CommandLine cmd = createCommandLine(args, options);
        String vcfFileLocation = cmd.getOptionValue(SOMATIC_VCF);
        String sample = cmd.getOptionValue(SAMPLE);
        String referenceSample = cmd.getOptionValue(REFERENCE, null);
        String rnaSample = cmd.getOptionValue(RNA, null);
        DatabaseAccess dbAccess = databaseAccess(cmd);
        CompoundFilter filter = new CompoundFilter(true);
        if (cmd.hasOption(PASS_FILTER)) {
            filter.add(new PassingVariantFilter());
        }
        if (cmd.hasOption(SOMATIC_FILTER)) {
            filter.add(new SomaticFilter());
        }

        LOGGER.info("Removing old data of sample {}", sample);
        try (SomaticVariantStreamWriter somaticWriter = dbAccess.somaticVariantWriter(sample)) {
            LOGGER.info("Streaming data from {} to db", vcfFileLocation);
            new SomaticVariantFactory(filter).fromVCFFile(sample, referenceSample, rnaSample, vcfFileLocation, somaticWriter);
        }

        LOGGER.info("Complete");
    }

    @NotNull
    private static Options createBasicOptions() {
        Options options = new Options();
        options.addOption(SAMPLE, true, "Name of the tumor sample.");
        options.addOption(REFERENCE, true, "Optional name of the reference sample. ");
        options.addOption(RNA, true, "Optional name of the rna sample.");

        options.addOption(SOMATIC_VCF, true, "Path to the somatic SNV/indel vcf file.");
        options.addOption(PASS_FILTER, false, "Only load unfiltered variants");
        options.addOption(SOMATIC_FILTER, false, "Only load variants flagged SOMATIC");
        addDatabaseCmdLineArgs(options);
        options.addOption(HOTSPOT_TSV, true, "Deprecated.");
        options.addOption(HIGH_CONFIDENCE_BED, true, "Deprecated.");

        return options;
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull String[] args, @NotNull Options options) throws ParseException {
        return new DefaultParser().parse(options, args);
    }
}
