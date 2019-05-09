package com.hartwig.hmftools.patientdb;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.hartwig.hmftools.common.amber.AmberBAF;
import com.hartwig.hmftools.common.amber.AmberBAFFile;
import com.hartwig.hmftools.common.chromosome.Chromosome;
import com.hartwig.hmftools.common.position.GenomePositionSelector;
import com.hartwig.hmftools.common.position.GenomePositionSelectorFactory;
import com.hartwig.hmftools.common.position.GenomePositions;
import com.hartwig.hmftools.common.region.BEDFileLoader;
import com.hartwig.hmftools.common.region.GenomeRegion;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class LoadAmberData {

    private static final Logger LOGGER = LogManager.getLogger(LoadAmberData.class);

    private static final String SAMPLE = "sample";
    private static final String AMBER_DIR = "amber_dir";
    private static final String BED_FILE = "bed";
    private static final String DB_USER = "db_user";
    private static final String DB_PASS = "db_pass";
    private static final String DB_URL = "db_url";

    public static void main(@NotNull final String[] args) throws ParseException, IOException, SQLException {
        final Options options = createBasicOptions();
        final CommandLine cmd = createCommandLine(args, options);
        final DatabaseAccess dbAccess = databaseAccess(cmd);

        final String tumorSample = cmd.getOptionValue(SAMPLE);
        final String amberPath = cmd.getOptionValue(AMBER_DIR);

        final String amberFile = AmberBAFFile.generateAmberFilename(amberPath, tumorSample);
        final Multimap<Chromosome, AmberBAF> bafs = AmberBAFFile.read(amberFile);
        final GenomePositionSelector<AmberBAF> selector = GenomePositionSelectorFactory.create(bafs);

        final List<GenomeRegion> loci = Lists.newArrayList(BEDFileLoader.fromBedFile(cmd.getOptionValue(BED_FILE)).values());
        final List<AmberBAF> lociAmberPoints = Lists.newArrayList();
        for (GenomeRegion locus : loci) {
            selector.select(GenomePositions.create(locus.chromosome(), locus.start())).ifPresent(lociAmberPoints::add);
        }

        persistToDatabase(dbAccess, tumorSample, lociAmberPoints);
    }

    @NotNull
    private static Options createBasicOptions() {
        final Options options = new Options();
        options.addOption(SAMPLE, true, "Tumor sample");
        options.addOption(AMBER_DIR, true, "Path to the amber directory");
        options.addOption(DB_USER, true, "Database user name");
        options.addOption(DB_PASS, true, "Database password");
        options.addOption(DB_URL, true, "Database url");
        options.addOption(BED_FILE, true, "Location of bed file");
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

    private static void persistToDatabase(final DatabaseAccess dbAccess, final String tumorSample, final List<AmberBAF> amber) {
        dbAccess.writeAmberBAF(tumorSample, amber);
    }

}
