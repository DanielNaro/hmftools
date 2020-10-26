package com.hartwig.hmftools.healthchecker;

import java.io.File;
import java.io.IOException;

import com.google.common.io.Resources;

import org.junit.Test;

public class HealthChecksApplicationTest {

    private static final String BASE_DIR = Resources.getResource("").getPath();

    private static final String OUTPUT_DIR = System.getProperty("user.home") + File.separator + "hmf" + File.separator + "tmp";
    private static final boolean WRITE_OUTPUT = false;

    @Test
    public void runHealthCheckerInSomaticMode() throws IOException {
        HealthChecksApplication app = new HealthChecksApplication(
                "reference",
                "tumor",
                BASE_DIR + "metrics/reference.wgsmetrics",
                BASE_DIR + "metrics/tumor.wgsmetrics",
                BASE_DIR + "purple",
                BASE_DIR + "flagstat/reference.flagstat",
                BASE_DIR + "flagstat/tumor.flagstat",
                OUTPUT_DIR
        );

        app.run(WRITE_OUTPUT);
    }

    @Test
    public void runHealthCheckerInSingleSampleMode() throws IOException {
        HealthChecksApplication app = new HealthChecksApplication(
                "reference",
                null,
                BASE_DIR + "metrics/reference.wgsmetrics",
                null,
                null,
                BASE_DIR + "flagstat/reference.flagstat",
                null,
                 OUTPUT_DIR
        );

        app.run(WRITE_OUTPUT);
    }
}