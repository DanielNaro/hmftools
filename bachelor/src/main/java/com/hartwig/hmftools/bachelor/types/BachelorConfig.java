package com.hartwig.hmftools.bachelor.types;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;
import com.hartwig.hmftools.bachelor.GermlineVcfParser;
import com.hartwig.hmftools.bachelor.datamodel.Program;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BachelorConfig
{
    public final String SampleId;
    public final String OutputDir;
    public final String GermlineVcf;
    public final String BamFile;
    public final String RefGenomeFile;
    public final String PurpleDataDir;
    public final boolean SkipEnrichment;

    public final boolean IsBatchMode;
    public final BatchRunData BatchRun;

    public final Map<String, Program> ProgramConfigMap;

    private boolean mIsValid;

    // config options
    public static final String CONFIG_XML = "xml_config";
    public static final String SAMPLE = "sample";

    public static final String DB_USER = "db_user";
    public static final String DB_PASS = "db_pass";
    public static final String DB_URL = "db_url";
    public static final String REF_GENOME = "ref_genome";

    private static final String GERMLINE_VCF = "germline_vcf";
    private static final String TUMOR_BAM_FILE = "tumor_bam_file";
    private static final String OUTPUT_DIR = "output_dir";
    private static final String PURPLE_DATA_DIRECTORY = "purple_data_dir"; // path to purple data directory
    private static final String SKIP_ENRICHMENT = "skip_enrichment";

    public static final String LOG_DEBUG = "log_debug";
    public static final String BATCH_FILE = "BATCH";

    private static final Logger LOGGER = LogManager.getLogger(BachelorConfig.class);

    public BachelorConfig(final CommandLine cmd)
    {
        mIsValid = true;

        ProgramConfigMap = Maps.newHashMap();

        if (cmd.hasOption(CONFIG_XML))
        {
            if(!loadXML(Paths.get(cmd.getOptionValue(CONFIG_XML)), ProgramConfigMap))
                mIsValid = false;
        }

        SampleId = cmd.getOptionValue(SAMPLE, "");

        if (SampleId.isEmpty() || SampleId.equals("*"))
        {
            LOGGER.info("Running in batch mode");
            IsBatchMode = true;
            BatchRun = new BatchRunData(cmd);
        }
        else
        {
            IsBatchMode = false;
            BatchRun = null;
        }

        GermlineVcf = cmd.getOptionValue(GERMLINE_VCF);

        SkipEnrichment = cmd.hasOption(SKIP_ENRICHMENT);
        BamFile = cmd.getOptionValue(TUMOR_BAM_FILE);
        RefGenomeFile = cmd.getOptionValue(REF_GENOME);

        String sampleOutputDir = cmd.getOptionValue(OUTPUT_DIR);

        if (!sampleOutputDir.endsWith(File.separator))
        {
            sampleOutputDir += File.separator;
        }

        OutputDir = sampleOutputDir;

        PurpleDataDir = cmd.getOptionValue(PURPLE_DATA_DIRECTORY, "");

        if(GermlineVcf == null)
        {
            LOGGER.error("missing germline VCF file");
            mIsValid = false;
        }

        if(!SkipEnrichment)
        {
            if (BamFile == null || RefGenomeFile == null || PurpleDataDir.isEmpty())
            {
                LOGGER.error("missing input files: BAM({}) refGenome({}) purpleDataDir({})",
                        BamFile == null || RefGenomeFile == null || PurpleDataDir.isEmpty());

                mIsValid = false;
            }
        }
    }

    public boolean isValid() { return mIsValid; }

    public static boolean loadXML(final Path path, Map<String, Program> configMap)
    {
        try
        {
            final ConfigSchema schema = ConfigSchema.make();

            final List<Program> programs = Files.walk(path)
                    .filter(p -> p.toString().endsWith(".xml"))
                    .map(schema::processXML)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            for (final Program p : programs)
            {
                if (configMap.containsKey(p.getName()))
                {
                    LOGGER.error("duplicate Programs detected: {}", p.getName());
                    return false;
                }
                else
                {
                    configMap.put(p.getName(), p);
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Error loading XML: {}", e.toString());
            return false;
        }

        return true;
    }

    @NotNull
    public static Options createOptions()
    {
        final Options options = new Options();

        // germline VCF parsing
        options.addOption(CONFIG_XML, true, "XML with genes, black and white lists");
        options.addOption(OUTPUT_DIR, true, "When in single-sample mode, all output written to this dir");
        options.addOption(TUMOR_BAM_FILE, true, "Location of a specific BAM file");        options.addOption(GERMLINE_VCF, true, "Germline VCF file");
        options.addOption(SAMPLE, true, "Sample Id (not applicable for batch mode)");
        options.addOption(REF_GENOME, true, "Path to the ref genome fasta file");
        options.addOption(PURPLE_DATA_DIRECTORY, true, "Sub-directory with sample path for purple data");
        options.addOption(SKIP_ENRICHMENT, false, "Only search for variants but skip Purple enrichment");

        options.addOption(DB_USER, true, "Database user name");
        options.addOption(DB_PASS, true, "Database password");
        options.addOption(DB_URL, true, "Database url");

        // logging
        options.addOption(LOG_DEBUG, false, "Sets log level to Debug, off by default");

        GermlineVcfParser.addCmdLineOptions(options);

        return options;
    }

    @NotNull
    public static CommandLine createCommandLine(@NotNull final Options options, @NotNull final String... args) throws ParseException
    {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

    @Nullable
    public static DatabaseAccess databaseAccess(@NotNull final CommandLine cmd)
    {
        if(!cmd.hasOption(DB_URL))
            return null;

        try
        {
            final String userName = cmd.getOptionValue(DB_USER);
            final String password = cmd.getOptionValue(DB_PASS);
            final String databaseUrl = cmd.getOptionValue(DB_URL);
            final String jdbcUrl = "jdbc:" + databaseUrl;
            return new DatabaseAccess(userName, password, jdbcUrl);
        }
        catch (SQLException e)
        {
            LOGGER.error("DB connection failed: {}", e.toString());
            return null;
        }
    }
}
