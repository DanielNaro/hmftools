package com.hartwig.hmftools.patientreporter.cfreport.data;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.drivercatalog.DriverCategory;
import com.hartwig.hmftools.common.variant.Hotspot;
import com.hartwig.hmftools.common.variant.ReportableVariant;
import com.hartwig.hmftools.patientreporter.variants.DriverInterpretation;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SomaticVariants {

    private SomaticVariants() {
    }

    @NotNull
    public static List<ReportableVariant> sort(@NotNull List<ReportableVariant> variants) {
        return variants.stream().sorted((variant1, variant2) -> {
            Double variant1DriverLikelihood = variant1.driverLikelihood();
            Double variant2DriverLikelihood = variant2.driverLikelihood();

            // Force any variant outside of driver catalog to the bottom of table.
            double driverLikelihood1 = variant1DriverLikelihood != null ? variant1DriverLikelihood : -1;
            double driverLikelihood2 = variant2DriverLikelihood != null ? variant2DriverLikelihood : -1;
            if (Math.abs(driverLikelihood1 - driverLikelihood2) > 0.001) {
                return (driverLikelihood1 - driverLikelihood2) < 0 ? 1 : -1;
            } else {
                if (variant1.gene().equals(variant2.gene())) {
                    // sort on codon position if gene is the same
                    return extractCodonField(variant1.canonicalHgvsCodingImpact()) - extractCodonField(variant2.canonicalHgvsCodingImpact()) < 0 ? -1 : 1;
                } else {
                    return variant1.gene().compareTo(variant2.gene());
                }
            }
        }).collect(Collectors.toList());
    }

    public static boolean hasNotifiableGermlineVariant(@NotNull List<ReportableVariant> variants) {
        for (ReportableVariant variant : variants) {
            if (variant.notifyClinicalGeneticist()) {
                return true;
            }
        }

        return false;
    }

    @NotNull
    public static String geneDisplayString(@NotNull ReportableVariant variant) {
        String geneSuffix = Strings.EMPTY;

        if (variant.notifyClinicalGeneticist()) {
            geneSuffix += "#";
        }

        return geneSuffix.isEmpty() ? variant.gene() : variant.gene() + " " + geneSuffix;
    }

    @NotNull
    public static String ploidyString(double ploidy, boolean hasReliablePurity) {
        if (!hasReliablePurity) {
            return DataUtil.NA_STRING;
        }

        return String.valueOf(Math.round(Math.max(0, ploidy)));
    }

    @NotNull
    public static String vafString(@NotNull ReportableVariant variant, boolean hasReliablePurity) {
        if (!hasReliablePurity) {
            return DataUtil.NA_STRING;
        }
        double vaf = variant.allelePloidy() / variant.totalPloidy();

        return DataUtil.formatPercentage(100 * Math.max(0, Math.min(1, vaf)));
    }

    @VisibleForTesting
    static int extractCodonField(@NotNull String hgvsCoding) {
        StringBuilder codonAppender = new StringBuilder();
        boolean noDigitFound = true;
        // hgvsCoding starts with "c.", we need to skip that...
        int index = 2;
        while (noDigitFound && index < hgvsCoding.length()) {
            if (Character.isDigit(hgvsCoding.charAt(index))) {
                codonAppender.append(hgvsCoding.charAt(index));
            } else {
                noDigitFound = false;
            }
            index++;
        }
        return Integer.parseInt(codonAppender.toString());
    }

    @NotNull
    public static String hotspotString(@NotNull Hotspot hotspot) {
        switch (hotspot) {
            case HOTSPOT:
                return "Yes";
            case NEAR_HOTSPOT:
                return "Near";
            default:
                return Strings.EMPTY;
        }
    }

    @NotNull
    public static String biallelicString(boolean biallelic, @Nullable DriverCategory driverCategory, boolean hasReliablePurity) {
        if (driverCategory == DriverCategory.TSG) {
            if (hasReliablePurity) {
                return biallelic ? "Yes" : "No";
            } else {
                return DataUtil.NA_STRING;
            }
        } else {
            return Strings.EMPTY;
        }
    }

    @NotNull
    public static String clonalString(double clonalLikelihood) {
        if (clonalLikelihood > 0.95) {
            return ">95%";
        } else if (clonalLikelihood > 0.9) {
            return "90-95%";
        } else if (clonalLikelihood > 0.8) {
            return "80-90%";
        } else if (clonalLikelihood > 0.7) {
            return "70-80%";
        } else if (clonalLikelihood > 0.6) {
            return "60-70%";
        } else if (clonalLikelihood > 0.5) {
            return "50-60%";
        } else if (clonalLikelihood > 0.4) {
            return "40-50%";
        } else if (clonalLikelihood > 0.3) {
            return "30-40%";
        } else if (clonalLikelihood > 0.2) {
            return "20-30%";
        } else if (clonalLikelihood > 0.1) {
            return "10-20%";
        } else if (clonalLikelihood > 0.05) {
            return "5-10%";
        } else  {
            return "<5%";
        }
    }

    @NotNull
    public static String driverString(@Nullable Double driverLikelihood) {
        DriverInterpretation interpretation = DriverInterpretation.interpret(driverLikelihood);

        return interpretation != null ? interpretation.text() : Strings.EMPTY;
    }

    @NotNull
    public static Set<String> driverGenesWithVariant(@NotNull List<ReportableVariant> variants) {
        Set<String> genes = Sets.newHashSet();
        for (ReportableVariant variant : variants) {
            if (DriverInterpretation.interpret(variant.driverLikelihood()) == DriverInterpretation.HIGH) {
                genes.add(variant.gene());
            }
        }
        return genes;
    }

    public static int countReportableVariants(@NotNull List<ReportableVariant> variants) {
        return variants.size();
    }
}
