package com.hartwig.hmftools.patientdb.readers.wide;

import static com.hartwig.hmftools.patientdb.data.TestDatamodelFactory.sampleBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.LocalDate;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.lims.Lims;
import com.hartwig.hmftools.common.lims.LimsFactory;
import com.hartwig.hmftools.patientdb.curators.TestCuratorFactory;
import com.hartwig.hmftools.patientdb.curators.TreatmentCurator;
import com.hartwig.hmftools.patientdb.data.Patient;
import com.hartwig.hmftools.patientdb.data.SampleData;

import org.apache.logging.log4j.util.Strings;
import org.junit.Test;

public class WidePatientReaderTest {

    @Test
    public void canInterpretDateIC() {
        assertEquals(WidePatientReader.createInterpretDateIC("12/04/2019").toString(), "2019-04-12");
    }

    @Test
    public void canConvertStringToDate() {
        assertEquals(WidePatientReader.convertStringToLocalDate("2018-10-17").toString(), "2018-10-17");
    }

    @Test
    public void canConvertTissueId() {
        assertEquals(WidePatientReader.extractYearOfTissueId("T1-00895 P"), "T1");
        assertEquals(WidePatientReader.extractBiopsyIdOfTissueId("T1-00895 P"), "895");
    }

    @Test
    public void determineResponse() {
        WideResponseData responseFollowRecist = ImmutableWideResponseData.builder()
                .patientId(Strings.EMPTY)
                .timePoint(1)
                .date(LocalDate.parse("2017-01-01"))
                .recistDone(true)
                .recistResponse("PD")
                .noRecistResponse(Strings.EMPTY)
                .noRecistReasonStopTreatment(Strings.EMPTY)
                .noRecistReasonStopTreatmentOther(Strings.EMPTY)
                .build();

        assertEquals(WidePatientReader.determineResponse(responseFollowRecist), "(1) PD");

        WideResponseData responseNotRecist = ImmutableWideResponseData.builder()
                .patientId(Strings.EMPTY)
                .timePoint(2)
                .date(LocalDate.parse("2017-01-01"))
                .recistDone(false)
                .recistResponse(Strings.EMPTY)
                .noRecistResponse("stop treatment")
                .noRecistReasonStopTreatment("other, please specify")
                .noRecistReasonStopTreatmentOther(Strings.EMPTY)
                .build();

        assertEquals(WidePatientReader.determineResponse(responseNotRecist), "(2) stop treatment (other, please specify)");
    }

    @Test
    public void convertGender() {
        assertEquals(WidePatientReader.convertGender("1"), "Male");
        assertEquals(WidePatientReader.convertGender("2"), "Female");
        assertEquals(WidePatientReader.convertGender(""), Strings.EMPTY);
    }

    @Test
    public void canReadCombiPreTreatment() {
        List<WidePreAvlTreatmentData2> preTreatmentDataCombi = Lists.newArrayList(ImmutableWidePreAvlTreatmentData2.builder()
                .patientId("WIDE00000001")
                .hasPreviousTherapy(true)
                .drug1("carboplatin")
                .drug2("erlotinib")
                .drug3(Strings.EMPTY)
                .drug4(Strings.EMPTY)
                .lastSystemicTherapyDate(LocalDate.parse("2015-11-20"))
                .build());
        List<WidePreAvlTreatmentData2> preTreatmentData = Lists.newArrayList(ImmutableWidePreAvlTreatmentData2.builder()
                .patientId("WIDE00000001")
                .hasPreviousTherapy(true)
                .drug1("carboplatin")
                .drug2(Strings.EMPTY)
                .drug3(Strings.EMPTY)
                .drug4(Strings.EMPTY)
                .lastSystemicTherapyDate(LocalDate.parse("2015-11-20"))
                .build());
        TreatmentCurator treatmentCurator = TestCuratorFactory.treatmentCurator();
        String patientIdentifier = "WIDE00000001";
        LocalDate biopsyDate = LocalDate.parse("2019-05-21");
        List<WideAvlTreatmentData> treatmentData = Lists.newArrayList();

        assertEquals(WidePatientReader.readDrugsPreTreatment(preTreatmentDataCombi,
                treatmentCurator,
                patientIdentifier,
                biopsyDate,
                treatmentData).get(0).name(), "carboplatin,erlotinib");

        assertEquals(WidePatientReader.readDrugsPreTreatment(preTreatmentData,
                treatmentCurator,
                patientIdentifier,
                biopsyDate,
                treatmentData).get(0).name(), "carboplatin");

    }

    @Test
    public void canLoadEmptyPatient() {
        Lims lims = LimsFactory.empty();
        WideEcrfModel wideEcrfModel = ImmutableWideEcrfModel.builder()
                .preAvlTreatments(Lists.newArrayList())
                .biopsies(Lists.newArrayList())
                .avlTreatments(Lists.newArrayList())
                .responses(Lists.newArrayList())
                .build();

        WidePatientReader patientReader = new WidePatientReader(wideEcrfModel,
                TestCuratorFactory.tumorLocationCurator(),
                TestCuratorFactory.biopsySiteCurator(),
                TestCuratorFactory.treatmentCurator());

        SampleData sample = sampleBuilder(LocalDate.parse("2017-01-01")).build();

        Patient patient = patientReader.read("ID", "Melanoma", Lists.newArrayList(sample), lims, "FR123");

        assertNotNull(patient);
    }
}