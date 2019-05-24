package com.hartwig.hmftools.patientreporter.cfreport.data;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DataUtil {

    private static final String DATE_TIME_FORMAT = "dd-MMM-yyyy";
    private static final DecimalFormat PERCENTAGE_FORMAT = new DecimalFormat("#'%'");

    public static final String NONE_STRING = "NONE";
    public static final String NA_STRING = "N/A";
    public static final String BELOW_DETECTION_STRING = "[below detection threshold]";

    private DataUtil() {
    }

    @NotNull
    public static String formatPercentage(final double percentage) {
        return PERCENTAGE_FORMAT.format(percentage);
    }

    @NotNull
    public static String formatDate(@Nullable final LocalDate date) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
        return date != null ? formatter.format(date) : "?";
    }
}
