package com.hartwig.hmftools.healthchecker.runners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.io.Resources;
import com.hartwig.hmftools.common.context.RunContext;
import com.hartwig.hmftools.common.context.TestRunContextFactory;
import com.hartwig.hmftools.healthchecker.result.BaseResult;
import com.hartwig.hmftools.healthchecker.result.MultiValueResult;
import com.hartwig.hmftools.healthchecker.result.NoResult;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class AmberCheckerTest {
    private static final String BASE_DIRECTORY = Resources.getResource("").getPath();
    private static final String REF_SAMPLE = "refSample";
    private static final String TUMOR_SAMPLE = "tumorSample";

    private final AmberChecker checker = new AmberChecker();

    @Test
    public void extractDataFromAmberWorksForSomatic() {
        final RunContext runContext = TestRunContextFactory.forSomaticTest(BASE_DIRECTORY, REF_SAMPLE, TUMOR_SAMPLE);
        final BaseResult result = checker.run(runContext);

        assertEquals(CheckType.AMBER, result.checkType());
        final HealthCheck bafCheck = ((MultiValueResult) result).checks().get(0);
        assertCheck(bafCheck, "0.4951", AmberCheck.MEAN_BAF.toString());

        final HealthCheck contaminationCheck = ((MultiValueResult) result).checks().get(1);
        assertCheck(contaminationCheck, "0.001", AmberCheck.CONTAMINATION.toString());
    }

    @Test
    public void extractDataFromOldAmberWorksForSomatic() {
        final RunContext runContext = TestRunContextFactory.forSomaticTest(BASE_DIRECTORY, REF_SAMPLE, "old");
        final BaseResult result = checker.run(runContext);

        assertEquals(CheckType.AMBER, result.checkType());
        final HealthCheck bafCheck = ((MultiValueResult) result).checks().get(0);
        assertCheck(bafCheck, "0.4951", AmberCheck.MEAN_BAF.toString());

        final HealthCheck contaminationCheck = ((MultiValueResult) result).checks().get(1);
        assertCheck(contaminationCheck, "0.0", AmberCheck.CONTAMINATION.toString());
    }

    @Test
    public void testMalformed() {
        final RunContext runContext = TestRunContextFactory.forSomaticTest(BASE_DIRECTORY, REF_SAMPLE, "malformed");
        assertTrue(checker.run(runContext) instanceof NoResult);
    }

    @Test
    public void testMissing() {
        final RunContext runContext = TestRunContextFactory.forSomaticTest(BASE_DIRECTORY, REF_SAMPLE, "missing");
        assertTrue(checker.run(runContext) instanceof NoResult);
    }

    private static void assertCheck(@NotNull final HealthCheck check, final String expectedValue, final String expectedCheckName) {
        assertEquals(expectedValue, check.getValue());
        assertEquals(expectedCheckName, check.getCheckName());
    }
}
