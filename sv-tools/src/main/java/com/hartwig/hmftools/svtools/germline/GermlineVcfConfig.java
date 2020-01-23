package com.hartwig.hmftools.svtools.germline;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GermlineVcfConfig
{
    // run config
    public final String SampleId;
    public final String OutputDir;
    public final String Scope;
    public final String VcfFile;
    public final String VcfsFile;
    public final String ProcessedFile;
    public final String BatchRunRootDir;
    public final boolean LinkByAssembly;
    public final boolean CheckDisruptions;

    // filtering config
    public final boolean RequirePass;
    public final boolean LogFiltered;
    public final int QualScoreThreshold;
    public final List<String> RestrictedChromosomes;
    public final boolean RequireGene;

    private static final String VCF_FILE = "vcf";
    private static final String VCFS_FILE = "vcfs_file";
    private static final String BATCH_ROOT_DIR = "batch_root_dir";
    private static final String PROCESSED_FILE = "processed";

    private static final String SCOPE = "scope";
    private static final String SAMPLE = "sample";
    public static final String GENE_TRANSCRIPTS_DIR = "gene_trans_dir";
    public static final String GENE_PANEL_FILE = "gene_panel_file";
    private static final String LINK_BY_ASSEMBLY = "link_by_assembly";
    private static final String CHECK_DISRUPTIONS = "check_disruptions";
    private static final String OUTPUT_DIR = "output_dir";
    public static final String LOG_DEBUG = "log_debug";

    private static final String REQUIRE_PASS = "require_pass";
    private static final String LOG_FILTERED = "log_filtered";
    private static final String QUAL_SCORE_THRESHOLD = "qs_threshold";
    private static final String RESTRICTED_CHROMOSOMES = "restrict_chromosomes";
    private static final String REQUIRE_GENE = "require_gene";

    private static final Logger LOGGER = LogManager.getLogger(GermlineVcfConfig.class);

    public GermlineVcfConfig(final CommandLine cmd)
    {
        SampleId = cmd.getOptionValue(SAMPLE);
        OutputDir = cmd.getOptionValue(OUTPUT_DIR);

        VcfFile = cmd.getOptionValue(VCF_FILE, "");
        VcfsFile = cmd.getOptionValue(VCFS_FILE, "");

        BatchRunRootDir = cmd.getOptionValue(BATCH_ROOT_DIR, "");
        ProcessedFile = cmd.getOptionValue(PROCESSED_FILE, "");

        Scope = cmd.getOptionValue(SCOPE);
        LinkByAssembly = cmd.hasOption(LINK_BY_ASSEMBLY);
        CheckDisruptions = cmd.hasOption(CHECK_DISRUPTIONS);

        RequirePass = cmd.hasOption(REQUIRE_PASS);
        LogFiltered = cmd.hasOption(LOG_FILTERED);
        RequireGene = cmd.hasOption(REQUIRE_GENE);

        RestrictedChromosomes = cmd.hasOption(RESTRICTED_CHROMOSOMES) ?
                Arrays.stream(cmd.getOptionValue(RESTRICTED_CHROMOSOMES, "")
                .split(";")).collect(Collectors.toList()) : Lists.newArrayList();

        QualScoreThreshold = Integer.parseInt(cmd.getOptionValue(QUAL_SCORE_THRESHOLD, "350"));
    }

    public static void addCommandLineOptions(final Options options)
    {
        options.addOption(SAMPLE, true, "Name of the tumor sample");
        options.addOption(VCF_FILE, true, "Path to the GRIDSS structural variant VCF file");
        options.addOption(BATCH_ROOT_DIR, true, "Path to the root directory for sample runs");
        options.addOption(PROCESSED_FILE, true, "Path to a previously-run output file");
        options.addOption(GENE_TRANSCRIPTS_DIR, true, "Ensembl data cache directory");
        options.addOption(GENE_PANEL_FILE, true, "Gene panel file");
        options.addOption(CHECK_DISRUPTIONS, false, "Check gene disruptions and filter out non-disruptive genes");
        options.addOption(LINK_BY_ASSEMBLY, false, "Look for assembled links");
        options.addOption(SCOPE, true, "Scope: germline or somatic");
        options.addOption(OUTPUT_DIR, true, "Path to write results");
        options.addOption(LOG_DEBUG, false, "Log verbose");

        options.addOption(REQUIRE_PASS, false, "Require variants to have filter = PASS");
        options.addOption(QUAL_SCORE_THRESHOLD, true, "Qual score threshold");
        options.addOption(RESTRICTED_CHROMOSOMES, true, "Optional set of chromosomes to restrict search to");
        options.addOption(LOG_FILTERED, false, "Log filtered variants");
        options.addOption(REQUIRE_GENE, false, "Only log SVs linked to a gene panel entry");
    }

    public static List<String> loadVcfFiles(final String vcfsFile)
    {
        List<String> vcfFiles = Lists.newArrayList();

        if (!Files.exists(Paths.get(vcfsFile)))
            return vcfFiles;

        try
        {
            vcfFiles = Files.readAllLines(new File(vcfsFile).toPath());

            LOGGER.info("loaded {} VCF filenames", vcfFiles.size());
        }
        catch(IOException e)
        {
            LOGGER.error("failed to load gene panel file({}): {}", vcfsFile, e.toString());
        }

        return vcfFiles;
    }
}
