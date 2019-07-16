package com.hartwig.hmftools.purple.config;

import static com.hartwig.hmftools.purple.CommandLineUtil.defaultIntValue;
import static com.hartwig.hmftools.purple.CommandLineUtil.defaultValue;

import java.io.File;
import java.util.Optional;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public interface SomaticConfig {

    Logger LOGGER = LogManager.getLogger(SomaticConfig.class);

    String SOMATIC_VARIANTS = "somatic_vcf";
    String SOMATIC_MIN_PEAK = "somatic_min_peak";
    String SOMATIC_MIN_TOTAL = "somatic_min_total";
    String SOMATIC_MIN_PURITY = "somatic_min_purity";
    String SOMATIC_MIN_PURITY_SPREAD = "somatic_min_purity_spread";
    String SOMATIC_PENALTY_WEIGHT = "somatic_penalty_weight";
    String HIGHLY_DIPLOID_PERCENTAGE = "highly_diploid_percentage";

    double SOMATIC_MIN_PURITY_DEFAULT = 0.17;
    double SOMATIC_MIN_PURITY_SPREAD_DEFAULT = 0.15;
    int SOMATIC_MIN_PEAK_DEFAULT = 50;
    int SOMATIC_MIN_TOTAL_DEFAULT = 300;
    double SOMATIC_PENALTY_WEIGHT_DEFAULT = 1;
    double HIGHLY_DIPLOID_PERCENTAGE_DEFAULT = 0.97;
    double MIN_SOMATIC_UNADJUSTED_VAF = 0.1;

    static void addOptions(@NotNull Options options) {
        options.addOption(SOMATIC_MIN_PEAK, true, "Minimum number of somatic variants to consider a peak. Default 50.");
        options.addOption(SOMATIC_MIN_TOTAL, true, "Minimum number of somatic variants required to assist highly diploid fits. Default 300.");
        options.addOption(SOMATIC_MIN_PURITY, true, "Somatic fit will not be used if both somatic and fitted purities are less than this value. Default 0.17");
        options.addOption(SOMATIC_MIN_PURITY_SPREAD, true, "Minimum spread within candidate purities before somatics can be used. Default 0.15");
        options.addOption(SOMATIC_VARIANTS, true, "Optional location of somatic variant vcf to assist fitting in highly-diploid samples.");
        options.addOption(SOMATIC_PENALTY_WEIGHT, true, "Proportion of somatic deviation to include in fitted purity score. Default 1.");
        options.addOption(HIGHLY_DIPLOID_PERCENTAGE, true, "Proportion of genome that must be diploid before using somatic fit. Default 0.97.");
    }

    Optional<File> file();

    int minTotalVariants();

    int minPeakVariants();

    double minSomaticPurity();

    double minSomaticPuritySpread();

    double somaticPenaltyWeight();

    double highlyDiploidPercentage();

    default double minSomaticUnadjustedVaf() {
        return MIN_SOMATIC_UNADJUSTED_VAF;
    }

    default double clonalityMaxPloidy() {
        return 10;
    }

    default double clonalityBinWidth() {
        return 0.05;
    }

    @NotNull
    static SomaticConfig createSomaticConfig(@NotNull CommandLine cmd) throws ParseException {
        final Optional<File> file;
        if (cmd.hasOption(SOMATIC_VARIANTS)) {
            final String somaticFilename = cmd.getOptionValue(SOMATIC_VARIANTS);
            final File somaticFile = new File(somaticFilename);
            if (!somaticFile.exists()) {
                throw new ParseException("Unable to read somatic variants from: " + somaticFilename);
            }
            file = Optional.of(somaticFile);
        } else {
            file = Optional.empty();
            LOGGER.info("No somatic vcf supplied");
        }

        return ImmutableSomaticConfig.builder()
                .file(file)
                .minTotalVariants(defaultIntValue(cmd, SOMATIC_MIN_TOTAL, SOMATIC_MIN_TOTAL_DEFAULT))
                .minPeakVariants(defaultIntValue(cmd, SOMATIC_MIN_PEAK, SOMATIC_MIN_PEAK_DEFAULT))
                .minSomaticPurity(defaultValue(cmd, SOMATIC_MIN_PURITY, SOMATIC_MIN_PURITY_DEFAULT))
                .minSomaticPuritySpread(defaultValue(cmd, SOMATIC_MIN_PURITY_SPREAD, SOMATIC_MIN_PURITY_SPREAD_DEFAULT))
                .somaticPenaltyWeight(defaultValue(cmd, SOMATIC_PENALTY_WEIGHT, SOMATIC_PENALTY_WEIGHT_DEFAULT))
                .highlyDiploidPercentage(defaultValue(cmd, HIGHLY_DIPLOID_PERCENTAGE, HIGHLY_DIPLOID_PERCENTAGE_DEFAULT))
                .build();
    }
}
