package com.hartwig.hmftools.patientdb;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.hartwig.hmftools.common.drivercatalog.DriverCatalog;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalogFile;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class LoadDriverCatalog
{
    private static final String SAMPLE = "sample";
    private static final String DATA_DIR = "data_dir";
    private static final String DB_USER = "db_user";
    private static final String DB_PASS = "db_pass";
    private static final String DB_URL = "db_url";

    private static final Logger LOGGER = LogManager.getLogger(LoadDriverCatalog.class);

    public static void main(@NotNull final String[] args) throws ParseException, IOException, SQLException
    {
        final Options options = new Options();
        options.addOption(DATA_DIR, true, "Location of purple output files");
        options.addOption(SAMPLE, true, "Tumor sample.");

        final CommandLineParser parser = new DefaultParser();
        final CommandLine cmd = parser.parse(options, args);

        final String sample = cmd.getOptionValue(SAMPLE);
        final DatabaseAccess dbAccess = databaseAccess(cmd);

        final List<DriverCatalog> driverCatalog =
                DriverCatalogFile.read(DriverCatalogFile.generateFilename(cmd.getOptionValue(DATA_DIR), sample));

        if (!driverCatalog.isEmpty())
        {
            LOGGER.info("Persisting {} driver records to database", driverCatalog.size());
            dbAccess.writeDriverCatalog(sample, driverCatalog);
        }
    }

    @NotNull
    private static DatabaseAccess databaseAccess(@NotNull final CommandLine cmd) throws SQLException
    {
        final String userName = cmd.getOptionValue(DB_USER);
        final String password = cmd.getOptionValue(DB_PASS);
        final String databaseUrl = cmd.getOptionValue(DB_URL);  //e.g. mysql://localhost:port/database";
        final String jdbcUrl = "jdbc:" + databaseUrl;
        return new DatabaseAccess(userName, password, jdbcUrl);
    }

}
