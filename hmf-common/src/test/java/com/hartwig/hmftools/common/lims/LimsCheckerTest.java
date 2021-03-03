package com.hartwig.hmftools.common.lims;

import static org.junit.Assert.*;

import com.hartwig.hmftools.common.lims.cohort.ImmutableLimsCohortConfig;
import com.hartwig.hmftools.common.lims.cohort.LimsCohortConfig;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class LimsCheckerTest {

    @NotNull
    public static LimsCohortConfig createCohortConfig(@NotNull String cohortId, boolean sampleContainsHospitalCenterId,
            boolean reportGermline, boolean reportGermlineFlag, boolean reportConclusion, boolean reportViral, boolean requireHospitalId,
            boolean requireHospitalPAId, boolean requireHospitalPersonsStudy, boolean requireHospitalPersonsRequester,
            boolean requireAdditionalInformationForSidePanel) {
        return ImmutableLimsCohortConfig.builder()
                .cohortId(cohortId)
                .sampleContainsHospitalCenterId(sampleContainsHospitalCenterId)
                .reportGermline(reportGermline)
                .reportGermlineFlag(reportGermlineFlag)
                .reportConclusion(reportConclusion)
                .reportViral(reportViral)
                .requireHospitalId(requireHospitalId)
                .requireHospitalPAId(requireHospitalPAId)
                .requireHospitalPersonsStudy(requireHospitalPersonsStudy)
                .requireHospitalPersonsRequester(requireHospitalPersonsRequester)
                .requireAdditionalInformationForSidePanel(requireAdditionalInformationForSidePanel)
                .build();
    }

    @Test
    public void canConvertHospitalPathologySampleId() {
        String wideSampleId = "WIDE020000001T";
        String cpctSampleId = "CPCT020000001T";
        String coreSampleId = "CORE020000001T";

        String correctIdT = "T20-72346";
        String correctIdC = "C18-124";
        String wrongId = "BGr-121111";

        LimsCohortConfig cohortConfigWIDE = createCohortConfig("WIDE", true, true, true, true, true, false, true, true, false, true);
        assertEquals(correctIdT, LimsChecker.toHospitalPathologySampleIdForReport(correctIdT, wideSampleId, cohortConfigWIDE));
        assertEquals(correctIdC, LimsChecker.toHospitalPathologySampleIdForReport(correctIdC, wideSampleId, cohortConfigWIDE));
        assertNull(LimsChecker.toHospitalPathologySampleIdForReport(wrongId, wideSampleId, cohortConfigWIDE));
        assertNull(LimsChecker.toHospitalPathologySampleIdForReport(Lims.NOT_AVAILABLE_STRING, wideSampleId, cohortConfigWIDE));
        assertNull(LimsChecker.toHospitalPathologySampleIdForReport(Strings.EMPTY, wideSampleId, cohortConfigWIDE));

        LimsCohortConfig cohortConfigCORE = createCohortConfig("CORE", true, true, false, true, true, true, true, false, true, true);
        assertNull(LimsChecker.toHospitalPathologySampleIdForReport(Strings.EMPTY, coreSampleId, cohortConfigCORE));
        assertEquals(correctIdT, LimsChecker.toHospitalPathologySampleIdForReport(correctIdT, coreSampleId, cohortConfigCORE));
        assertNull(LimsChecker.toHospitalPathologySampleIdForReport(wrongId, coreSampleId, cohortConfigCORE));

        LimsCohortConfig cohortConfigCPCT = createCohortConfig("CPCT", true, false, false, false, false, false, false, true, false, false);
        assertNull(correctIdT, LimsChecker.toHospitalPathologySampleIdForReport(correctIdT, cpctSampleId, cohortConfigCPCT));
        assertNull(correctIdC, LimsChecker.toHospitalPathologySampleIdForReport(correctIdC, cpctSampleId, cohortConfigCPCT));
    }

    @Test
    public void canCheckHospitalPatientId() {
        String coreSampleId = "CORE020000001T";
        String wideSampleId = "WIDE020000001T";

        String hospitalIdNA = Lims.NOT_AVAILABLE_STRING;
        String hospitalIDEmpty = Strings.EMPTY;
        String hospitalId = "1234";

        LimsCohortConfig cohortConfigCORE = createCohortConfig("CORE", true, true, false, true, true, true, true, false, true, true);
        assertEquals(hospitalIdNA, LimsChecker.checkHospitalPatientId(hospitalIdNA, coreSampleId, cohortConfigCORE));
        assertEquals(hospitalIDEmpty, LimsChecker.checkHospitalPatientId(hospitalIDEmpty, coreSampleId, cohortConfigCORE));
        assertEquals(hospitalId, LimsChecker.checkHospitalPatientId(hospitalId, coreSampleId, cohortConfigCORE));

        LimsCohortConfig cohortConfigWIDE = createCohortConfig("WIDE", true, true, true, true, true, false, true, true, false, true);
        assertEquals(hospitalIdNA, LimsChecker.checkHospitalPatientId(hospitalIdNA, wideSampleId, cohortConfigWIDE));
    }

}