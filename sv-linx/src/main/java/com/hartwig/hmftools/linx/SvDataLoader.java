package com.hartwig.hmftools.linx;

import static com.hartwig.hmftools.linx.LinxConfig.DB_PASS;
import static com.hartwig.hmftools.linx.LinxConfig.DB_URL;
import static com.hartwig.hmftools.linx.LinxConfig.DB_USER;
import static com.hartwig.hmftools.linx.LinxConfig.SAMPLE;
import static com.hartwig.hmftools.linx.LinxConfig.SV_DATA_DIR;
import static com.hartwig.hmftools.linx.LinxConfig.databaseAccess;
import static com.hartwig.hmftools.patientdb.dao.DatabaseUtil.getValueNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalog;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalogFile;
import com.hartwig.hmftools.common.variant.filter.AlwaysPassFilter;
import com.hartwig.hmftools.common.variant.structural.EnrichedStructuralVariant;
import com.hartwig.hmftools.common.variant.structural.EnrichedStructuralVariantFactory;
import com.hartwig.hmftools.common.variant.structural.ImmutableStructuralVariantData;
import com.hartwig.hmftools.common.variant.structural.StructuralVariant;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantData;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantFile;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantFileLoader;
import com.hartwig.hmftools.common.variant.structural.linx.LinxBreakend;
import com.hartwig.hmftools.common.variant.structural.linx.LinxBreakendFile;
import com.hartwig.hmftools.common.variant.structural.linx.LinxCluster;
import com.hartwig.hmftools.common.variant.structural.linx.LinxClusterFile;
import com.hartwig.hmftools.common.variant.structural.linx.LinxDriver;
import com.hartwig.hmftools.common.variant.structural.linx.LinxDriverFile;
import com.hartwig.hmftools.common.variant.structural.linx.LinxFusion;
import com.hartwig.hmftools.common.variant.structural.linx.LinxFusionFile;
import com.hartwig.hmftools.common.variant.structural.linx.LinxLink;
import com.hartwig.hmftools.common.variant.structural.linx.LinxLinkFile;
import com.hartwig.hmftools.common.variant.structural.linx.LinxSvData;
import com.hartwig.hmftools.common.variant.structural.linx.LinxSvDataFile;
import com.hartwig.hmftools.common.variant.structural.linx.LinxViralInsertFile;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;
import com.hartwig.hmftools.patientdb.dao.StructuralVariantFusionDAO;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class SvDataLoader
{
    private static final Logger LOGGER = LogManager.getLogger(SvDataLoader.class);

    public static final String VCF_FILE = "sv_vcf";

    private static final String LOAD_SV_DATA = "load_sv_data";
    private static final String LOAD_LINX_DATA = "load_linx_data";

    public static void main(@NotNull final String[] args) throws ParseException, SQLException
    {
        final Options options = createBasicOptions();
        final CommandLine cmd = createCommandLine(args, options);
        final DatabaseAccess dbAccess = databaseAccess(cmd);

        if(dbAccess == null)
        {
            LOGGER.error("failed to create DB connection");
            return;
        }

        final String sampleId = cmd.getOptionValue(SAMPLE);

        boolean loadSvData = cmd.hasOption(LOAD_SV_DATA);
        boolean loadLinxData = cmd.hasOption(LOAD_LINX_DATA);
        final String svDataPath = cmd.getOptionValue(SV_DATA_DIR);

        if(loadSvData)
        {
            loadStructuralVariants(cmd, dbAccess, sampleId, svDataPath);
        }

        if(loadLinxData)
        {
            loadLinxData(dbAccess, sampleId, svDataPath);
        }

        LOGGER.info("Complete");
    }

    private static void loadLinxData(final DatabaseAccess dbAccess, final String sampleId, final String svDataOutputDir)
    {
        try
        {
            List<LinxSvData> linxSvData = LinxSvDataFile.read(LinxSvDataFile.generateFilename(svDataOutputDir, sampleId));
            LOGGER.info("sample({}) loading {} SV annotation records", sampleId, linxSvData.size());
            dbAccess.writeSvLinxData(sampleId, linxSvData);

            List<LinxCluster> clusterData = LinxClusterFile.read(LinxClusterFile.generateFilename(svDataOutputDir, sampleId));
            LOGGER.info("sample({}) loading {} SV cluster records", sampleId, clusterData.size());
            dbAccess.writeSvClusters(sampleId, clusterData);

            List<LinxLink> linksData = LinxLinkFile.read(LinxLinkFile.generateFilename(svDataOutputDir, sampleId));
            LOGGER.info("sample({}) loading {} SV links records", sampleId, linksData.size());
            dbAccess.writeSvLinks(sampleId, linksData);

            String viralInsertFilename = LinxViralInsertFile.generateFilename(svDataOutputDir, sampleId);
            if(Files.exists(Paths.get(viralInsertFilename)))
            {
                List<LinxViralInsertFile> viralInserts = LinxViralInsertFile.read(viralInsertFilename);

                if (!viralInserts.isEmpty())
                {
                    LOGGER.info("sample({}) loading {} SV viral inserts records", sampleId, viralInserts.size());
                    dbAccess.writeSvViralInserts(sampleId, viralInserts);
                }
            }

            final String fusionsFilename = LinxFusionFile.generateFilename(svDataOutputDir, sampleId);
            final String breakendsFilename = LinxBreakendFile.generateFilename(svDataOutputDir, sampleId);

            if(Files.exists(Paths.get(breakendsFilename)))
            {
                List<LinxBreakend> breakends = LinxBreakendFile.read(breakendsFilename);

                List<LinxFusion> fusions = Files.exists(Paths.get(fusionsFilename)) ?
                        LinxFusionFile.read(fusionsFilename) : Lists.newArrayList();

                LOGGER.info("sample({}) loading {} breakends and {} fusion records", sampleId, breakends.size(), fusions.size());

                final StructuralVariantFusionDAO annotationDAO = new StructuralVariantFusionDAO(dbAccess.context());
                annotationDAO.writeBreakendsAndFusions(sampleId, breakends, fusions);
            }

            final String driverCatalogFilename = DriverCatalogFile.generateFilename(svDataOutputDir, sampleId);

            if(Files.exists(Paths.get(driverCatalogFilename)))
            {
                List<DriverCatalog> drivers = DriverCatalogFile.read(driverCatalogFilename);
                LOGGER.info("sample({}) loading {} driver catalog records", sampleId, drivers.size());
                dbAccess.writeDriverCatalog(sampleId, drivers);
            }

            final String driversFilename = LinxDriverFile.generateFilename(svDataOutputDir, sampleId);

            if(Files.exists(Paths.get(driversFilename)))
            {
                List<LinxDriver> drivers = LinxDriverFile.read(driversFilename);
                LOGGER.info("sample({}) loading {} SV driver records", sampleId, drivers.size());
                dbAccess.writeSvDrivers(sampleId, drivers);
            }

        }
        catch(IOException e)
        {
            LOGGER.error("failed to load SV data files: {}", e.toString());
            return;
        }
    }

    private static void loadStructuralVariants(
            final CommandLine cmd, final DatabaseAccess dbAccess, final String sampleId, final String svDataOutputDir)
    {
        if(!cmd.hasOption(VCF_FILE))
        {
            LOGGER.error("missing VCF to load VCF file");
            return;
        }

        final String vcfFile = cmd.getOptionValue(VCF_FILE);
        final List<StructuralVariantData> svDataList = loadSvDataFromVcf(vcfFile);

        if(!svDataList.isEmpty())
        {
            LOGGER.info("Persisting {} SVs to database", svDataList.size());
            dbAccess.writeStructuralVariants(sampleId, svDataList);

        }

        // write a flat file of SV data if the output directory is configured
        if (svDataOutputDir != null)
        {
            // write data to file
            try
            {
                final String svFilename = StructuralVariantFile.generateFilename(svDataOutputDir, sampleId);
                StructuralVariantFile.write(svFilename, svDataList);
            }
            catch (IOException e)
            {
                LOGGER.error("failed to write SV data: {}", e.toString());
            }
        }
    }

    public static final List<StructuralVariantData> loadSvDataFromVcf(final String vcfFile)
    {
        final List<StructuralVariantData> svDataList = Lists.newArrayList();

        try
        {
            final List<StructuralVariant> variants = StructuralVariantFileLoader.fromFile(vcfFile, new AlwaysPassFilter());
            final List<EnrichedStructuralVariant> enrichedVariants = new EnrichedStructuralVariantFactory().enrich(variants);

            // generate a unique ID for each SV record
            int svId = 0;

            for (EnrichedStructuralVariant var : enrichedVariants)
            {
                svDataList.add(convertSvData(var, svId++));
            }
        }
        catch(IOException e)
        {
            LOGGER.error("failed to load SVs from VCF: {}", e.toString());
        }

        LOGGER.info("loaded {} SV data records from VCF file: {}", svDataList.size(), vcfFile);

        return svDataList;
    }

    public static final List<StructuralVariantData> loadSvDataFromGermlineVcf(final String vcfFile)
    {
        final List<StructuralVariantData> svDataList = Lists.newArrayList();

        try
        {
            final List<StructuralVariant> variants = StructuralVariantFileLoader.fromFile(vcfFile, new AlwaysPassFilter());

            int svId = 0;

            for (StructuralVariant var : variants)
            {
                svDataList.add(convertGermlineSvData(var, svId++));
            }
        }
        catch(IOException e)
        {
            LOGGER.error("failed to load SVs from VCF: {}", e.toString());
        }

        LOGGER.info("loaded {} germline SV data records from VCF file: {}", svDataList.size(), vcfFile);

        return svDataList;
    }

    public static final List<StructuralVariantData> loadSvDataFromSvFile(final String sampleId, final String svDataPath)
    {
        try
        {
            final String svDataFile = StructuralVariantFile.generateFilename(svDataPath, sampleId);
            return StructuralVariantFile.read(svDataFile);
        }
        catch(IOException e)
        {
            LOGGER.error("failed to load SV data: {}", e.toString());
            return Lists.newArrayList();
        }
    }

    public static StructuralVariantData convertSvData(final EnrichedStructuralVariant var, int svId)
    {
        return ImmutableStructuralVariantData.builder()
                .id(svId)
                .startChromosome(var.chromosome(true))
                .endChromosome(var.end() == null ? "0" : var.chromosome(false))
                .startPosition(var.position(true))
                .endPosition(var.end() == null ? -1 : var.position(false))
                .startOrientation(var.orientation(true))
                .endOrientation(var.end() == null ? (byte) 0 : var.orientation(false))
                .startHomologySequence(var.start().homology())
                .endHomologySequence(var.end() == null ? "" : var.end().homology())
                .ploidy(getValueNotNull(var.ploidy()))
                .startAF(getValueNotNull(var.start().alleleFrequency()))
                .endAF(var.end() == null ? 0 : getValueNotNull(var.end().alleleFrequency()))
                .adjustedStartAF(getValueNotNull(var.start().adjustedAlleleFrequency()))
                .adjustedEndAF(var.end() == null ? 0 : getValueNotNull(var.end().adjustedAlleleFrequency()))
                .adjustedStartCopyNumber(getValueNotNull(var.start().adjustedCopyNumber()))
                .adjustedEndCopyNumber(var.end() == null ? 0 : getValueNotNull(var.end().adjustedCopyNumber()))
                .adjustedStartCopyNumberChange(getValueNotNull(var.start().adjustedCopyNumberChange()))
                .adjustedEndCopyNumberChange(var.end() == null ? 0 : getValueNotNull(var.end().adjustedCopyNumberChange()))
                .insertSequence(var.insertSequence())
                .type(var.type())
                .filter(var.filter())
                .imprecise(var.imprecise())
                .qualityScore(getValueNotNull(var.qualityScore()))
                .event(getValueNotNull(var.event()))
                .startTumorVariantFragmentCount(getValueNotNull(var.start().tumorVariantFragmentCount()))
                .startTumorReferenceFragmentCount(getValueNotNull(var.start().tumorReferenceFragmentCount()))
                .startNormalVariantFragmentCount(getValueNotNull(var.start().normalVariantFragmentCount()))
                .startNormalReferenceFragmentCount(getValueNotNull(var.start().normalReferenceFragmentCount()))
                .endTumorVariantFragmentCount(var.end() == null ? 0 : getValueNotNull(var.end().tumorVariantFragmentCount()))
                .endTumorReferenceFragmentCount(var.end() == null ? 0 : getValueNotNull(var.end().tumorReferenceFragmentCount()))
                .endNormalVariantFragmentCount(var.end() == null ? 0 : getValueNotNull(var.end().normalVariantFragmentCount()))
                .endNormalReferenceFragmentCount(var.end() == null ? 0 : getValueNotNull(var.end().normalReferenceFragmentCount()))
                .startIntervalOffsetStart(getValueNotNull(var.start().startOffset()))
                .startIntervalOffsetEnd(getValueNotNull(var.start().endOffset()))
                .endIntervalOffsetStart(var.end() == null ? 0 : getValueNotNull(var.end().startOffset()))
                .endIntervalOffsetEnd(var.end() == null ? 0 : getValueNotNull(var.end().endOffset()))
                .inexactHomologyOffsetStart(getValueNotNull(var.start().inexactHomologyOffsetStart()))
                .inexactHomologyOffsetEnd(getValueNotNull(var.start().inexactHomologyOffsetEnd()))
                .startLinkedBy(getValueNotNull(var.startLinkedBy()))
                .endLinkedBy(getValueNotNull(var.endLinkedBy()))
                .vcfId(getValueNotNull(var.id()))
                .startRefContext(getValueNotNull(var.start().refGenomeContext()))
                .endRefContext(var.end() == null ? "" : getValueNotNull(var.end().refGenomeContext()))
                .recovered(var.recovered())
                .recoveryMethod((getValueNotNull(var.recoveryMethod())))
                .recoveryFilter(getValueNotNull(var.recoveryFilter()))
                .insertSequenceAlignments(getValueNotNull(var.insertSequenceAlignments()))
                .insertSequenceRepeatClass(getValueNotNull(var.insertSequenceRepeatClass()))
                .insertSequenceRepeatType(getValueNotNull(var.insertSequenceRepeatType()))
                .insertSequenceRepeatOrientation(getValueNotNull(var.insertSequenceRepeatOrientation()))
                .insertSequenceRepeatCoverage(getValueNotNull(var.insertSequenceRepeatCoverage()))
                .startAnchoringSupportDistance(var.start().anchoringSupportDistance())
                .endAnchoringSupportDistance(var.end() == null ? 0 : var.end().anchoringSupportDistance())
                .build();
    }

    public static StructuralVariantData convertGermlineSvData(final StructuralVariant var, int svId)
    {
        return ImmutableStructuralVariantData.builder()
                .id(svId)
                .startChromosome(var.chromosome(true))
                .endChromosome(var.end() == null ? "0" : var.chromosome(false))
                .startPosition(var.position(true))
                .endPosition(var.end() == null ? -1 : var.position(false))
                .startOrientation(var.orientation(true))
                .endOrientation(var.end() == null ? (byte) 0 : var.orientation(false))
                .startHomologySequence(var.start().homology())
                .endHomologySequence(var.end() == null ? "" : var.end().homology())
                .ploidy(1)
                .startAF(getValueNotNull(var.start().alleleFrequency()))
                .endAF(var.end() == null ? 0 : getValueNotNull(var.end().alleleFrequency()))
                .adjustedStartAF(getValueNotNull(var.start().alleleFrequency()))
                .adjustedEndAF(var.end() == null ? 0 : getValueNotNull(var.end().alleleFrequency()))
                .adjustedStartCopyNumber(getValueNotNull(1))
                .adjustedEndCopyNumber(var.end() == null ? 0 : 1)
                .adjustedStartCopyNumberChange(1)
                .adjustedEndCopyNumberChange(var.end() == null ? 0 : 1)
                .insertSequence(var.insertSequence())
                .type(var.type())
                .filter(var.filter())
                .imprecise(var.imprecise())
                .qualityScore(getValueNotNull(var.qualityScore()))
                .event(getValueNotNull(var.event()))
                .startTumorVariantFragmentCount(getValueNotNull(var.start().tumorVariantFragmentCount()))
                .startTumorReferenceFragmentCount(getValueNotNull(var.start().tumorReferenceFragmentCount()))
                .startNormalVariantFragmentCount(getValueNotNull(var.start().normalVariantFragmentCount()))
                .startNormalReferenceFragmentCount(getValueNotNull(var.start().normalReferenceFragmentCount()))
                .endTumorVariantFragmentCount(0)
                .endTumorReferenceFragmentCount(0)
                .endNormalVariantFragmentCount(var.end() == null ? 0 : getValueNotNull(var.end().normalVariantFragmentCount()))
                .endNormalReferenceFragmentCount(var.end() == null ? 0 : getValueNotNull(var.end().normalReferenceFragmentCount()))
                .startIntervalOffsetStart(getValueNotNull(var.start().startOffset()))
                .startIntervalOffsetEnd(getValueNotNull(var.start().endOffset()))
                .endIntervalOffsetStart(var.end() == null ? 0 : getValueNotNull(var.end().startOffset()))
                .endIntervalOffsetEnd(var.end() == null ? 0 : getValueNotNull(var.end().endOffset()))
                .inexactHomologyOffsetStart(getValueNotNull(var.start().inexactHomologyOffsetStart()))
                .inexactHomologyOffsetEnd(getValueNotNull(var.start().inexactHomologyOffsetEnd()))
                .startLinkedBy(getValueNotNull(var.startLinkedBy()))
                .endLinkedBy(getValueNotNull(var.endLinkedBy()))
                .vcfId(getValueNotNull(var.id()))
                .startRefContext("") // getValueNotNull(var.start().refGenomeContext())
                .endRefContext(var.end() == null ? "" : "") // getValueNotNull(var.end().refGenomeContext())
                .recovered(var.recovered())
                .recoveryMethod((getValueNotNull(var.recoveryMethod())))
                .recoveryFilter(getValueNotNull(var.recoveryFilter()))
                .insertSequenceAlignments(getValueNotNull(var.insertSequenceAlignments()))
                .insertSequenceRepeatClass(getValueNotNull(var.insertSequenceRepeatClass()))
                .insertSequenceRepeatType(getValueNotNull(var.insertSequenceRepeatType()))
                .insertSequenceRepeatOrientation(getValueNotNull(var.insertSequenceRepeatOrientation()))
                .insertSequenceRepeatCoverage(getValueNotNull(var.insertSequenceRepeatCoverage()))
                .startAnchoringSupportDistance(var.start().anchoringSupportDistance())
                .endAnchoringSupportDistance(var.end() == null ? 0 : var.end().anchoringSupportDistance())
                .build();
    }

    @NotNull
    private static Options createBasicOptions()
    {
        final Options options = new Options();
        options.addOption(SAMPLE, true, "Name of the tumor sample. This should correspond to the value used in PURPLE");
        options.addOption(VCF_FILE, true, "Path to the PURPLE structural variant VCF file");
        options.addOption(LOAD_SV_DATA, false, "Optional: load purple VCF into SV database table");
        options.addOption(LOAD_LINX_DATA, false, "Optional: load all LINX files to database");
        options.addOption(DB_USER, true, "Database user name.");
        options.addOption(DB_PASS, true, "Database password");
        options.addOption(DB_URL, true, "Database url");
        options.addOption(SV_DATA_DIR, true, "Directory to read or write SV data");

        return options;
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final String[] args, @NotNull final Options options) throws ParseException
    {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }
}
