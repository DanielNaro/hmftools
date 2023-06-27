package com.hartwig.hmftools.patientdb;

import static com.hartwig.hmftools.common.utils.config.CommonConfig.PURPLE_DIR_CFG;
import static com.hartwig.hmftools.common.utils.config.CommonConfig.PURPLE_DIR_DESC;
import static com.hartwig.hmftools.common.utils.config.CommonConfig.REFERENCE;
import static com.hartwig.hmftools.common.utils.config.CommonConfig.REFERENCE_DESC;
import static com.hartwig.hmftools.common.utils.config.CommonConfig.SAMPLE;
import static com.hartwig.hmftools.common.utils.config.ConfigUtils.LOG_DEBUG;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.checkAddDirSeparator;
import static com.hartwig.hmftools.common.utils.config.ConfigUtils.SAMPLE_ID_FILE;
import static com.hartwig.hmftools.common.utils.config.ConfigUtils.SAMPLE_ID_FILE_DESC;
import static com.hartwig.hmftools.patientdb.CommonUtils.LOGGER;
import static com.hartwig.hmftools.patientdb.CommonUtils.logVersion;
import static com.hartwig.hmftools.patientdb.dao.DatabaseAccess.addDatabaseCmdLineArgs;
import static com.hartwig.hmftools.patientdb.dao.DatabaseAccess.databaseAccess;
import static com.hartwig.hmftools.patientdb.database.hmfpatients.tables.Somaticvariant.SOMATICVARIANT;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.hartwig.hmftools.common.purple.PurpleCommon;
import com.hartwig.hmftools.common.utils.config.ConfigUtils;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.common.variant.SomaticVariantFactory;
import com.hartwig.hmftools.patientdb.dao.BufferedWriter;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Result;

public class LoadPurpleSomaticVariants
{
    private static final String RNA = "rna";
    private static final String DRY_RUN = "dry_run";

    public static void main(@NotNull String[] args)
    {
        Options options = createOptions();

        try
        {
            CommandLine cmd = new DefaultParser().parse(options, args);
            DatabaseAccess dbAccess = databaseAccess(cmd);

            if(cmd.hasOption(LOG_DEBUG))
            {
                Configurator.setRootLevel(Level.DEBUG);
            }

            logVersion();

            String sampleId = cmd.getOptionValue(SAMPLE);
            List<String> sampleIds = cmd.hasOption(SAMPLE_ID_FILE) ? ConfigUtils.loadSampleIdsFile(cmd.getOptionValue(SAMPLE_ID_FILE)) : null;
            String referenceId = cmd.getOptionValue(REFERENCE);
            String rnaId = cmd.getOptionValue(RNA);
            String purpleDir = checkAddDirSeparator(cmd.getOptionValue(PURPLE_DIR_CFG));

            boolean dryRunOnly = cmd.hasOption(DRY_RUN);

            if(sampleIds == null && sampleId == null && referenceId == null)
            {
                LOGGER.error("missing sample or reference ID config");
                System.exit(1);
            }

            if(sampleIds != null)
            {
                LOGGER.info("processing {} samples", sampleIds.size());

                int processed = 0;
                for(String sample : sampleIds)
                {
                    String ref = sample.substring(0, sample.lastIndexOf("T")) + "R";
                    String sampleDir = purpleDir.replaceAll("\\*", sample);

                    try
                    {
                        loadSomaticData(sample, ref, null, dbAccess, sampleDir, dryRunOnly);
                    }
                    catch(Exception e)
                    {
                        LOGGER.error("failed to load Purple data: {}", e.toString());
                    }
                    ++processed;

                    if(processed > 0 && (processed % 100) == 0)
                    {
                        LOGGER.info("processed {} samples", processed);
                    }
                }
            }
            else
            {
                if(!Files.exists(Paths.get(purpleDir)))
                {
                    LOGGER.error("invalid Purple data directory({})", purpleDir);
                    System.exit(1);
                }

                if(sampleId == null)
                    sampleId = referenceId;

                loadSomaticData(sampleId, referenceId, rnaId, dbAccess, purpleDir, dryRunOnly);
            }

            LOGGER.info("Purple data loading complete");
        }
        catch(Exception e)
        {
            LOGGER.error("failed to load Purple data: {}", e.toString());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void loadSomaticData(
            final String sampleId, final String referenceId, final String rnaId,
            final DatabaseAccess dbAccess, final String purpleDir, boolean dryRunOnly) throws Exception
    {
        final String somaticVcf = PurpleCommon.purpleSomaticVcfFile(purpleDir, sampleId);

        if(!Files.exists(Paths.get(somaticVcf)))
        {
            LOGGER.info("somatic VCF({}) missing");
            return;
        }

        SomaticVariantFactory somaticVariantFactory = new SomaticVariantFactory();
        List<SomaticVariant> somaticVariants = somaticVariantFactory.fromVCFFile(sampleId, referenceId, rnaId, somaticVcf);

        int dbCount = getDbVariantCount(sampleId, dbAccess);

        if(dbCount == somaticVariants.size())
        {
            LOGGER.debug("sample({}) count({}) match", sampleId, somaticVariants.size());
            return;
        }

        LOGGER.warn("sample({}) count mismatch: vcf({}) db({})", sampleId, somaticVariants.size(), dbCount);

        if(dryRunOnly)
            return;

        BufferedWriter<SomaticVariant> somaticWriter = dbAccess.somaticVariantWriter(sampleId);
        somaticVariants.forEach(x -> somaticWriter.accept(x));
        somaticWriter.close();

        LOGGER.info("loaded {} somatic variants, filtered({})",
                somaticVariantFactory.getCreatedCount(), somaticVariantFactory.getFilteredCount());
    }

    private static int getDbVariantCount(final String sampleId, final DatabaseAccess dbAccess)
    {
        Result<Record1<Integer>> result = dbAccess.context()
                .selectCount()
                .from(SOMATICVARIANT)
                .where(SOMATICVARIANT.SAMPLEID.eq(sampleId))
                .fetch();

        for(Record record : result)
        {
            return Integer.parseInt(record.getValue(0).toString());
        }

        return 0;
    }

    @NotNull
    private static Options createOptions()
    {
        Options options = new Options();
        options.addOption(SAMPLE, true, SAMPLE);
        options.addOption(SAMPLE_ID_FILE, true, SAMPLE_ID_FILE_DESC);
        options.addOption(REFERENCE, true, REFERENCE_DESC);
        options.addOption(RNA, true, "RNA sample ID");
        options.addOption(DRY_RUN, false, "Only examine differences in counts");
        options.addOption(PURPLE_DIR_CFG, true, PURPLE_DIR_DESC);
        addDatabaseCmdLineArgs(options);
        ConfigUtils.addLoggingOptions(options);
        return options;
    }
}
