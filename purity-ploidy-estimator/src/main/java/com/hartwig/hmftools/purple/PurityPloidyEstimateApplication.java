package com.hartwig.hmftools.purple;

import static com.hartwig.hmftools.common.purple.purity.FittedPurityScoreFactory.polyclonalProportion;
import static com.hartwig.hmftools.common.purple.purity.WholeGenomeDuplication.wholeGenomeDuplication;
import static com.hartwig.hmftools.patientdb.LoadPurpleData.persistToDatabase;
import static com.hartwig.hmftools.purple.PurpleRegionZipper.updateRegionsWithCopyNumbers;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.hartwig.hmftools.common.amber.AmberBAF;
import com.hartwig.hmftools.common.drivercatalog.CNADrivers;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalog;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalogFile;
import com.hartwig.hmftools.common.drivercatalog.GermlineDrivers;
import com.hartwig.hmftools.common.genome.chromosome.Chromosome;
import com.hartwig.hmftools.common.genome.chromosome.CobaltChromosomes;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.purple.PurityAdjuster;
import com.hartwig.hmftools.common.purple.PurityAdjusterAbnormalChromosome;
import com.hartwig.hmftools.common.purple.copynumber.PurpleCopyNumber;
import com.hartwig.hmftools.common.purple.copynumber.PurpleCopyNumberFactory;
import com.hartwig.hmftools.common.purple.copynumber.PurpleCopyNumberFile;
import com.hartwig.hmftools.common.purple.gender.Gender;
import com.hartwig.hmftools.common.purple.gene.GeneCopyNumber;
import com.hartwig.hmftools.common.purple.gene.GeneCopyNumberFactory;
import com.hartwig.hmftools.common.purple.gene.GeneCopyNumberFile;
import com.hartwig.hmftools.common.purple.purity.BestFit;
import com.hartwig.hmftools.common.purple.purity.BestFitFactory;
import com.hartwig.hmftools.common.purple.purity.FittedPurity;
import com.hartwig.hmftools.common.purple.purity.FittedPurityFactory;
import com.hartwig.hmftools.common.purple.purity.FittedPurityRangeFile;
import com.hartwig.hmftools.common.purple.purity.ImmutablePurityContext;
import com.hartwig.hmftools.common.purple.purity.PurityContext;
import com.hartwig.hmftools.common.purple.purity.PurityContextFile;
import com.hartwig.hmftools.common.purple.qc.PurpleQC;
import com.hartwig.hmftools.common.purple.qc.PurpleQCFactory;
import com.hartwig.hmftools.common.purple.region.FittedRegion;
import com.hartwig.hmftools.common.purple.region.FittedRegionFactory;
import com.hartwig.hmftools.common.purple.region.FittedRegionFactoryV2;
import com.hartwig.hmftools.common.purple.region.ObservedRegion;
import com.hartwig.hmftools.common.purple.region.SegmentFile;
import com.hartwig.hmftools.common.utils.Doubles;
import com.hartwig.hmftools.common.utils.version.VersionInfo;
import com.hartwig.hmftools.common.variant.PurityAdjustedSomaticVariant;
import com.hartwig.hmftools.common.variant.PurityAdjustedSomaticVariantFactory;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.common.variant.SomaticVariantFactory;
import com.hartwig.hmftools.common.variant.clonality.ModifiableWeightedPloidy;
import com.hartwig.hmftools.common.variant.clonality.PeakModel;
import com.hartwig.hmftools.common.variant.clonality.PeakModelFactory;
import com.hartwig.hmftools.common.variant.clonality.PeakModelFile;
import com.hartwig.hmftools.common.variant.enrich.VariantHotspotEnrichment;
import com.hartwig.hmftools.common.variant.filter.SGTFilter;
import com.hartwig.hmftools.common.variant.recovery.RecoverStructuralVariants;
import com.hartwig.hmftools.common.variant.structural.StructuralVariant;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;
import com.hartwig.hmftools.purple.config.CommonConfig;
import com.hartwig.hmftools.purple.config.ConfigSupplier;
import com.hartwig.hmftools.purple.config.DBConfig;
import com.hartwig.hmftools.purple.config.FitScoreConfig;
import com.hartwig.hmftools.purple.config.FittingConfig;
import com.hartwig.hmftools.purple.config.SmoothingConfig;
import com.hartwig.hmftools.purple.config.SomaticFitConfig;
import com.hartwig.hmftools.purple.config.StructuralVariantConfig;
import com.hartwig.hmftools.purple.germline.GermlineVariants;
import com.hartwig.hmftools.purple.plot.Charts;
import com.hartwig.hmftools.purple.somatic.SomaticStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.filter.PassingVariantFilter;
import htsjdk.variant.vcf.VCFFileReader;

public class PurityPloidyEstimateApplication {

    private static final Logger LOGGER = LogManager.getLogger(PurityPloidyEstimateApplication.class);
    private static final int THREADS_DEFAULT = 2;
    private static final String THREADS = "threads";
    private static final String VERSION = "version";

    public static void main(final String... args) throws IOException, SQLException, ExecutionException, InterruptedException {
        final Options options = createOptions();
        try {
            new PurityPloidyEstimateApplication(options, args);
        } catch (ParseException e) {
            LOGGER.warn(e);
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("PurityPloidyEstimateApplication", options);
            System.exit(1);
        }
    }

    private PurityPloidyEstimateApplication(final Options options, final String... args)
            throws ParseException, IOException, SQLException, ExecutionException, InterruptedException {
        final VersionInfo version = new VersionInfo("purple.version");
        LOGGER.info("PURPLE version: {}", version.version());

        final CommandLine cmd = createCommandLine(options, args);
        if (cmd.hasOption(VERSION)) {
            System.exit(0);
        }

        final int threads = cmd.hasOption(THREADS) ? Integer.parseInt(cmd.getOptionValue(THREADS)) : THREADS_DEFAULT;
        final ExecutorService executorService = Executors.newFixedThreadPool(threads);
        try {
            // Get common config
            final ConfigSupplier configSupplier = new ConfigSupplier(version.version(), cmd, options);
            final CommonConfig config = configSupplier.commonConfig();
            final String outputDirectory = config.outputDirectory();
            final String tumorSample = config.tumorSample();

            // Load Amber Data
            final Gender amberGender = configSupplier.amberData().gender();
            final Multimap<Chromosome, AmberBAF> bafs = configSupplier.amberData().bafs();
            int averageTumorDepth = configSupplier.amberData().averageTumorDepth();

            // Load Cobalt Data
            final Gender cobaltGender = configSupplier.cobaltData().gender();
            final CobaltChromosomes cobaltChromosomes = configSupplier.cobaltData().cobaltChromosomes();

            // Gender
            if (cobaltGender.equals(amberGender)) {
                LOGGER.info("Sample gender is {}", cobaltGender.toString().toLowerCase());
            } else {
                LOGGER.warn("COBALT gender {} does not match AMBER gender {}", cobaltGender, amberGender);
            }

            // Load structural and somatic variants
            final PurpleStructuralVariantSupplier structuralVariants = structuralVariants(configSupplier);
            final List<SomaticVariant> allSomatics = somaticVariants(configSupplier);
            final List<SomaticVariant> fittingSomatics = config.tumorOnly()
                    ? Collections.emptyList()
                    : allSomatics.stream().filter(SomaticVariant::isSnp).collect(Collectors.toList());

            LOGGER.info("Applying segmentation");
            final Segmentation segmentation = new Segmentation(configSupplier);
            final List<ObservedRegion> observedRegions = segmentation.createSegments(structuralVariants.variants());

            LOGGER.info("Fitting purity");
            final FitScoreConfig fitScoreConfig = configSupplier.fitScoreConfig();
            final FittedRegionFactory fittedRegionFactory = createFittedRegionFactory(averageTumorDepth, cobaltChromosomes, fitScoreConfig);
            final BestFit bestFit = fitPurity(executorService,
                    configSupplier,
                    cobaltChromosomes,
                    fittingSomatics,
                    observedRegions,
                    fittedRegionFactory,
                    structuralVariants.variants());
            final FittedPurity fittedPurity = bestFit.fit();
            final PurityAdjuster purityAdjuster =
                    new PurityAdjusterAbnormalChromosome(fittedPurity.purity(), fittedPurity.normFactor(), cobaltChromosomes.chromosomes());

            final SmoothingConfig smoothingConfig = configSupplier.smoothingConfig();
            final PurpleCopyNumberFactory copyNumberFactory = new PurpleCopyNumberFactory(smoothingConfig.minDiploidTumorRatioCount(),
                    smoothingConfig.minDiploidTumorRatioCountAtCentromere(),
                    configSupplier.amberData().averageTumorDepth(),
                    fittedPurity.ploidy(),
                    purityAdjuster,
                    configSupplier.cobaltData().cobaltChromosomes());

            LOGGER.info("Calculating copy number");
            List<FittedRegion> fittedRegions =
                    fittedRegionFactory.fitRegion(fittedPurity.purity(), fittedPurity.normFactor(), observedRegions);
            copyNumberFactory.invoke(fittedRegions, structuralVariants.variants());

            final int recoveredSVCount = recoverStructuralVariants(configSupplier.structuralVariantConfig(),
                    structuralVariants,
                    purityAdjuster,
                    copyNumberFactory.copyNumbers());
            if (recoveredSVCount > 0) {
                LOGGER.info("Reapplying segmentation with {} recovered structural variants", recoveredSVCount);
                final List<ObservedRegion> recoveredObservedRegions = segmentation.createSegments(structuralVariants.variants());

                LOGGER.info("Recalculating copy number");
                fittedRegions = fittedRegionFactory.fitRegion(fittedPurity.purity(), fittedPurity.normFactor(), recoveredObservedRegions);
                copyNumberFactory.invoke(fittedRegions, structuralVariants.variants());
            }

            final List<PurpleCopyNumber> copyNumbers = copyNumberFactory.copyNumbers();
            structuralVariants.inferMissingVariant(copyNumbers);

            final List<PurpleCopyNumber> germlineDeletions = copyNumberFactory.germlineDeletions();
            final List<FittedRegion> enrichedFittedRegions = updateRegionsWithCopyNumbers(fittedRegions, copyNumbers);

            final List<GeneCopyNumber> geneCopyNumbers =
                    GeneCopyNumberFactory.geneCopyNumbers(configSupplier.refGenomeConfig().genePanel(), copyNumbers, germlineDeletions);

            LOGGER.info("Generating QC Stats");
            final PurpleQC qcChecks = PurpleQCFactory.create(configSupplier.amberData().contamination(),
                    bestFit,
                    amberGender,
                    cobaltGender,
                    copyNumbers,
                    geneCopyNumbers,
                    cobaltChromosomes.germlineAberrations());

            LOGGER.info("Modelling somatic peaks");
            final List<PurityAdjustedSomaticVariant> enrichedSomatics =
                    new PurityAdjustedSomaticVariantFactory(tumorSample, purityAdjuster, copyNumbers, enrichedFittedRegions).create(
                            allSomatics);
            final List<PeakModel> somaticPeaks = modelSomaticPeaks(configSupplier.somaticConfig(), enrichedSomatics);

            LOGGER.info("Enriching somatic variants");
            final SomaticStream somaticStream = new SomaticStream(configSupplier);
            somaticStream.processAndWrite(purityAdjuster, copyNumbers, enrichedFittedRegions, somaticPeaks);

            GermlineVariants germlineVariants = new GermlineVariants(configSupplier);
            if (configSupplier.germlineConfig().enabled()) {
                germlineVariants.processAndWrite(purityAdjuster, copyNumbers);
            }

            final PurityContext purityContext = ImmutablePurityContext.builder()
                    .version(version.version())
                    .bestFit(bestFit.fit())
                    .method(bestFit.method())
                    .gender(cobaltGender)
                    .score(bestFit.score())
                    .polyClonalProportion(polyclonalProportion(copyNumbers))
                    .wholeGenomeDuplication(wholeGenomeDuplication(copyNumbers))
                    .microsatelliteIndelsPerMb(somaticStream.microsatelliteIndelsPerMb())
                    .microsatelliteStatus(somaticStream.microsatelliteStatus())
                    .tumorMutationalLoad(somaticStream.tumorMutationalLoad())
                    .tumorMutationalLoadStatus(somaticStream.tumorMutationalLoadStatus())
                    .tumorMutationalBurdenPerMb(somaticStream.tumorMutationalBurdenPerMb())
                    .tumorMutationalBurdenStatus(somaticStream.tumorMutationalBurdenPerMbStatus())
                    .svTumorMutationalBurden(structuralVariants.passingBnd())
                    .qc(qcChecks)
                    .build();

            final List<DriverCatalog> driverCatalog = Lists.newArrayList();
            if (configSupplier.driverCatalogConfig().enabled()) {
                LOGGER.info("Generating driver catalog");
                List<DriverCatalog> somaticDriverCatalog = Lists.newArrayList();
                somaticDriverCatalog.addAll(somaticStream.drivers(geneCopyNumbers));

                final CNADrivers cnaDrivers = new CNADrivers(qcChecks.status(), configSupplier.driverCatalogConfig().genePanel());
                somaticDriverCatalog.addAll(cnaDrivers.deletions(geneCopyNumbers));
                somaticDriverCatalog.addAll(cnaDrivers.amplifications(fittedPurity.ploidy(), geneCopyNumbers));

                final GermlineDrivers germlineDrivers = new GermlineDrivers(configSupplier.driverCatalogConfig().genePanel().driverGenes());
                List<DriverCatalog> germlineDriverCatalog = germlineDrivers.drivers(germlineVariants.reportableVariants(), geneCopyNumbers);

                DriverCatalogFile.write(DriverCatalogFile.generateSomaticFilename(outputDirectory, tumorSample), somaticDriverCatalog);

                if (configSupplier.germlineConfig().enabled()) {
                    DriverCatalogFile.write(DriverCatalogFile.generateGermlineFilename(outputDirectory, tumorSample), germlineDriverCatalog);
                }

                driverCatalog.addAll(somaticDriverCatalog);
                driverCatalog.addAll(germlineDriverCatalog);
            }

            LOGGER.info("Writing purple data to directory: {}", outputDirectory);
            version.write(outputDirectory);
            PurityContextFile.write(outputDirectory, tumorSample, purityContext);
            FittedPurityRangeFile.write(outputDirectory, tumorSample, bestFit.allFits());
            FittedPurityRangeFile.write(outputDirectory, tumorSample, bestFit.allFits());
            PurpleCopyNumberFile.write(PurpleCopyNumberFile.generateFilenameForWriting(outputDirectory, tumorSample), copyNumbers);
            PurpleCopyNumberFile.write(PurpleCopyNumberFile.generateGermlineFilenameForWriting(outputDirectory, tumorSample),
                    germlineDeletions);
            GeneCopyNumberFile.write(GeneCopyNumberFile.generateFilenameForWriting(outputDirectory, tumorSample), geneCopyNumbers);
            SegmentFile.write(SegmentFile.generateFilename(outputDirectory, tumorSample), fittedRegions);
            structuralVariants.write(purityAdjuster, copyNumbers);
            PeakModelFile.write(PeakModelFile.generateFilename(outputDirectory, tumorSample), somaticPeaks);

            final DBConfig dbConfig = configSupplier.dbConfig();
            if (dbConfig.enabled()) {
                LOGGER.info("Writing purple data to database: {}", dbConfig.url());
                final DatabaseAccess dbAccess = databaseAccess(dbConfig);
                persistToDatabase(dbAccess,
                        tumorSample,
                        bestFit.bestFitPerPurity(),
                        copyNumbers,
                        germlineDeletions,
                        purityContext,
                        qcChecks,
                        geneCopyNumbers,
                        driverCatalog);
            }

            LOGGER.info("Generating charts");

            new Charts(configSupplier, executorService).write(cobaltGender,
                    copyNumbers,
                    enrichedSomatics,
                    structuralVariants.variants(),
                    fittedRegions,
                    Lists.newArrayList(bafs.values()));

        } finally {
            executorService.shutdown();
        }
        LOGGER.info("Complete");
    }

    private int recoverStructuralVariants(@NotNull final StructuralVariantConfig svConfig,
            @NotNull final PurpleStructuralVariantSupplier structuralVariants, @NotNull final PurityAdjuster purityAdjuster,
            @NotNull final List<PurpleCopyNumber> copyNumbers) throws IOException {
        if (!svConfig.recoveryFile().isPresent()) {
            return 0;
        }

        final String vcfRecoveryFile = svConfig.recoveryFile().get().toString();
        LOGGER.info("Loading recovery candidates from {}", vcfRecoveryFile);
        try (final RecoverStructuralVariants recovery = new RecoverStructuralVariants(purityAdjuster, vcfRecoveryFile, copyNumbers)) {
            final Collection<VariantContext> recoveredVariants = recovery.recoverVariants(structuralVariants.variants());
            if (!recoveredVariants.isEmpty()) {
                recoveredVariants.forEach(structuralVariants::addVariant);
            }
            return recoveredVariants.size();
        }
    }

    @NotNull
    private BestFit fitPurity(final ExecutorService executorService, final ConfigSupplier configSupplier,
            final CobaltChromosomes cobaltChromosomes, final List<SomaticVariant> snpSomatics, final List<ObservedRegion> observedRegions,
            final FittedRegionFactory fittedRegionFactory, final List<StructuralVariant> structuralVariants)
            throws ExecutionException, InterruptedException {
        final FittingConfig fittingConfig = configSupplier.fittingConfig();
        final SomaticFitConfig somaticFitConfig = configSupplier.somaticConfig();
        final FittedPurityFactory fittedPurityFactory = new FittedPurityFactory(executorService,
                cobaltChromosomes,
                fittingConfig.minPurity(),
                fittingConfig.maxPurity(),
                fittingConfig.purityIncrement(),
                fittingConfig.minPloidy(),
                fittingConfig.maxPloidy(),
                somaticFitConfig.somaticPenaltyWeight(),
                configSupplier.commonConfig().tumorOnly(),
                fittedRegionFactory,
                observedRegions,
                snpSomatics);

        final BestFitFactory bestFitFactory = new BestFitFactory(somaticFitConfig.enabled(),
                somaticFitConfig.minSomaticTotalReadCount(),
                somaticFitConfig.maxSomaticTotalReadCount(),
                fittingConfig.minPurity(),
                fittingConfig.maxPurity(),
                somaticFitConfig.minTotalVariants(),
                somaticFitConfig.minPeakVariants(),
                somaticFitConfig.highlyDiploidPercentage(),
                somaticFitConfig.minSomaticPurity(),
                somaticFitConfig.minSomaticPuritySpread(),
                somaticFitConfig.minTotalSvFragmentCount(),
                somaticFitConfig.minTotalSomaticVariantAlleleReadCount(),
                fittedPurityFactory.all(),
                snpSomatics,
                structuralVariants);
        return bestFitFactory.bestFit();
    }

    @NotNull
    private FittedRegionFactory createFittedRegionFactory(final int averageTumorDepth, final CobaltChromosomes cobaltChromosomes,
            final FitScoreConfig fitScoreConfig) {
        return new FittedRegionFactoryV2(cobaltChromosomes,
                averageTumorDepth,
                fitScoreConfig.ploidyPenaltyFactor(),
                fitScoreConfig.ploidyPenaltyStandardDeviation(),
                fitScoreConfig.ploidyPenaltyMinStandardDeviationPerPloidy(),
                fitScoreConfig.ploidyPenaltyMajorAlleleSubOneMultiplier(),
                fitScoreConfig.ploidyPenaltyMajorAlleleSubOneAdditional(),
                fitScoreConfig.ploidyPenaltyBaselineDeviation());
    }

    @NotNull
    private static PurpleStructuralVariantSupplier structuralVariants(@NotNull final ConfigSupplier configSupplier) {
        final CommonConfig commonConfig = configSupplier.commonConfig();
        final StructuralVariantConfig svConfig = configSupplier.structuralVariantConfig();
        if (svConfig.file().isPresent()) {
            final String filePath = svConfig.file().get().toString();
            final String outputPath = commonConfig.outputDirectory() + File.separator + commonConfig.tumorSample() + ".purple.sv.vcf.gz";

            LOGGER.info("Loading structural variants from {}", filePath);
            return new PurpleStructuralVariantSupplier(commonConfig.version(),
                    filePath,
                    outputPath,
                    configSupplier.refGenomeConfig().refGenome());
        } else {
            return new PurpleStructuralVariantSupplier();
        }
    }

    @NotNull
    private static List<SomaticVariant> somaticVariants(@NotNull final ConfigSupplier configSupplier) {
        final String sample = configSupplier.commonConfig().tumorSample();
        final SomaticFitConfig somaticConfig = configSupplier.somaticConfig();
        if (somaticConfig.file().isPresent()) {
            final List<SomaticVariant> result = Lists.newArrayList();

            final SomaticVariantFactory factory = new SomaticVariantFactory(new PassingVariantFilter(), new SGTFilter());
            final Consumer<VariantContext> convertThenSave = context -> factory.createVariant(sample, context).ifPresent(result::add);
            final VariantHotspotEnrichment enrich =
                    new VariantHotspotEnrichment(configSupplier.driverCatalogConfig().somaticHotspots(), convertThenSave);

            LOGGER.info("Loading somatic variants from {}", somaticConfig.file().get().toString());
            try (VCFFileReader vcfReader = new VCFFileReader(somaticConfig.file().get(), false)) {
                for (VariantContext variantContext : vcfReader) {
                    if (factory.test(variantContext)) {
                        enrich.accept(variantContext);
                    }
                }
            }

            enrich.flush();
            return result;
        } else {
            LOGGER.info("Somatic variants support disabled.");
            return Collections.emptyList();
        }
    }

    @NotNull
    private static Options createOptions() {
        final Options options = new Options();
        ConfigSupplier.addOptions(options);

        options.addOption(THREADS, true, "Number of threads (default 2)");
        options.addOption(VERSION, false, "Exit after displaying version info.");

        return options;
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final Options options, @NotNull final String... args) throws ParseException {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

    @NotNull
    private static DatabaseAccess databaseAccess(@NotNull final DBConfig dbConfig) throws SQLException {
        return new DatabaseAccess(dbConfig.user(), dbConfig.password(), dbConfig.url());
    }

    @NotNull
    private List<PeakModel> modelSomaticPeaks(@NotNull final SomaticFitConfig config,
            @NotNull final List<PurityAdjustedSomaticVariant> enrichedSomatics) {
        final List<ModifiableWeightedPloidy> weightedPloidies = Lists.newArrayList();
        for (PurityAdjustedSomaticVariant enrichedSomatic : enrichedSomatics) {
            if (Doubles.lessThan(enrichedSomatic.variantCopyNumber(), config.clonalityMaxPloidy()) && !enrichedSomatic.isFiltered()
                    && HumanChromosome.contains(enrichedSomatic.chromosome()) && HumanChromosome.fromString(enrichedSomatic.chromosome())
                    .isAutosome()) {
                weightedPloidies.add(ModifiableWeightedPloidy.create()
                        .from(enrichedSomatic)
                        .setPloidy(enrichedSomatic.variantCopyNumber())
                        .setWeight(1));
            }
        }

        return new PeakModelFactory(config.clonalityMaxPloidy(), config.clonalityBinWidth()).model(weightedPloidies);
    }

}
