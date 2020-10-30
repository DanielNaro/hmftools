package com.hartwig.hmftools.patientdb.readers;

import java.time.LocalDate;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.ecrf.datamodel.ImmutableValidationFinding;
import com.hartwig.hmftools.common.ecrf.datamodel.ValidationFinding;
import com.hartwig.hmftools.common.ecrf.formstatus.FormStatus;
import com.hartwig.hmftools.patientdb.curators.TreatmentCurator;
import com.hartwig.hmftools.patientdb.curators.TumorLocationCurator;
import com.hartwig.hmftools.patientdb.data.BaselineData;
import com.hartwig.hmftools.patientdb.data.BiopsyData;
import com.hartwig.hmftools.patientdb.data.BiopsyTreatmentData;
import com.hartwig.hmftools.patientdb.data.BiopsyTreatmentResponseData;
import com.hartwig.hmftools.patientdb.data.CuratedBiopsyType;
import com.hartwig.hmftools.patientdb.data.CuratedTumorLocation;
import com.hartwig.hmftools.patientdb.data.DrugData;
import com.hartwig.hmftools.patientdb.data.ImmutableBaselineData;
import com.hartwig.hmftools.patientdb.data.ImmutableBiopsyData;
import com.hartwig.hmftools.patientdb.data.ImmutableCuratedBiopsyType;
import com.hartwig.hmftools.patientdb.data.ImmutableDrugData;
import com.hartwig.hmftools.patientdb.data.ImmutablePreTreatmentData;
import com.hartwig.hmftools.patientdb.data.Patient;
import com.hartwig.hmftools.patientdb.data.SampleData;
import com.hartwig.hmftools.patientdb.matchers.MatchResult;
import com.hartwig.hmftools.patientdb.readers.wide.WideAvlTreatmentData;
import com.hartwig.hmftools.patientdb.readers.wide.WideBiopsyData;
import com.hartwig.hmftools.patientdb.readers.wide.WideClinicalData;
import com.hartwig.hmftools.patientdb.readers.wide.WideEcrfModel;
import com.hartwig.hmftools.patientdb.readers.wide.WideFiveDays;
import com.hartwig.hmftools.patientdb.readers.wide.WidePreAvlTreatmentData;
import com.hartwig.hmftools.patientdb.readers.wide.WideResponseData;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WidePatientReader {

    private static final Logger LOGGER = LogManager.getLogger(WidePatientReader.class);

    @NotNull
    private final WideEcrfModel wideEcrfModel;
    @NotNull
    private final TumorLocationCurator tumorLocationCurator;
    @NotNull
    private final TreatmentCurator treatmentCurator;

    public WidePatientReader(@NotNull final WideEcrfModel wideEcrfModel,
            @NotNull final TumorLocationCurator tumorLocationCurator, @NotNull final TreatmentCurator treatmentCurator) {
        this.wideEcrfModel = wideEcrfModel;
        this.tumorLocationCurator = tumorLocationCurator;
        this.treatmentCurator = treatmentCurator;
    }

    @NotNull
    public Patient read(@NotNull String patientIdentifier, @Nullable String limsPrimaryTumorLocation,
            @NotNull List<SampleData> sequencedSamples) {
        BaselineData baseline = buildBaselineData(patientIdentifier, limsPrimaryTumorLocation);
        MatchResult<BiopsyData> matchedBiopsies = buildMatchedBiopsies(patientIdentifier, sequencedSamples);
        List<BiopsyTreatmentData> treatments = buildBiopsyTreatmentData(patientIdentifier);
        List<BiopsyTreatmentResponseData> responses = buildBiopsyTreatmentResponseData(patientIdentifier);

        return new Patient(patientIdentifier,
                baseline,
                ImmutablePreTreatmentData.builder().formStatus(FormStatus.undefined()).build(),
                sequencedSamples,
                matchedBiopsies.values(),
                treatments,
                responses,
                Lists.newArrayList(),
                Lists.newArrayList(),
                matchedBiopsies.findings());
    }

    @NotNull
    private BaselineData buildBaselineData(@NotNull String patientIdentifier, @Nullable String limsPrimaryTumorLocation) {
        List<WideFiveDays> fiveDays = dataForPatient(wideEcrfModel.fiveDays(), patientIdentifier);

        // There is one entry per biopsy but we assume they all share the same baseline information
        Integer birthYear = !fiveDays.isEmpty() ? fiveDays.get(0).birthYear() : null;
        String gender = !fiveDays.isEmpty() ? fiveDays.get(0).gender() : null;
        LocalDate informedConsentDate = !fiveDays.isEmpty() ? fiveDays.get(0).informedConsentDate() : null;

        CuratedTumorLocation curatedTumorLocation = tumorLocationCurator.search(limsPrimaryTumorLocation);
        if (curatedTumorLocation.primaryTumorLocation() == null && limsPrimaryTumorLocation != null
                && !limsPrimaryTumorLocation.isEmpty()) {
            LOGGER.warn("Could not curate WIDE primary tumor location v2 '{}'", limsPrimaryTumorLocation);
        }

        return ImmutableBaselineData.builder()
                .registrationDate(null)
                .informedConsentDate(informedConsentDate)
                .gender(gender)
                .hospital(null)
                .birthYear(birthYear)
                .curatedTumorLocation(curatedTumorLocation)
                .deathDate(null)
                .demographyStatus(FormStatus.undefined())
                .primaryTumorStatus(FormStatus.undefined())
                .informedConsentStatus(FormStatus.undefined())
                .eligibilityStatus(FormStatus.undefined())
                .selectionCriteriaStatus(FormStatus.undefined())
                .deathStatus(FormStatus.undefined())
                .build();
    }

    @NotNull
    private MatchResult<BiopsyData> buildMatchedBiopsies(@NotNull String patientIdentifier, @NotNull List<SampleData> sequencedBiopsies) {
        List<WideBiopsyData> clinicalBiopsies = dataForPatient(wideEcrfModel.biopsies(), patientIdentifier);

        List<ValidationFinding> findings = Lists.newArrayList();
        if (clinicalBiopsies.size() < sequencedBiopsies.size()) {
            findings.add(biopsyMatchFinding(patientIdentifier,
                    "Not enough clinical biopsies to match for every sequenced sample",
                    "Clinical biopsies: " + clinicalBiopsies.size() + ", sequenced samples: " + sequencedBiopsies.size()));
        }

        List<BiopsyData> biopsies = Lists.newArrayList();
        List<WideFiveDays> fiveDaysForPatient = dataForPatient(wideEcrfModel.fiveDays(), patientIdentifier);
        for (WideBiopsyData clinicalBiopsy : clinicalBiopsies) {
            WideFiveDays fiveDays = lookupFiveDaysOnBiopsyDate(fiveDaysForPatient, clinicalBiopsy.biopsyDate());

            if (fiveDays != null) {
                BiopsyData finalBiopsy = toBiopsyData(fiveDays);
                SampleData sequencedBiopsy = lookupOnPathologySampleId(sequencedBiopsies, clinicalBiopsy.pathologySampleId());
                if (sequencedBiopsy != null) {
                    finalBiopsy = ImmutableBiopsyData.builder().from(finalBiopsy).sampleId(sequencedBiopsy.sampleId()).build();
                }

                biopsies.add(finalBiopsy);
            } else {
                findings.add(biopsyMatchFinding(patientIdentifier,
                        "Could not find five days entry for biopsy",
                        "WIDE Biopsy = " + clinicalBiopsy));
            }
        }

        return new MatchResult<>(biopsies, findings);
    }

    @Nullable
    private static WideFiveDays lookupFiveDaysOnBiopsyDate(@NotNull List<WideFiveDays> fiveDaysForPatient, @Nullable LocalDate biopsyDate) {
        if (biopsyDate == null) {
            return null;
        }

        for (WideFiveDays fiveDays : fiveDaysForPatient) {
            LocalDate fiveDaysBiopsyDate = fiveDays.biopsyDate();
            if (fiveDaysBiopsyDate != null && fiveDaysBiopsyDate.equals(biopsyDate)) {
                return fiveDays;
            }
        }

        return null;
    }

    @Nullable
    private static SampleData lookupOnPathologySampleId(@NotNull List<SampleData> sequencedBiopsies, @NotNull String pathologySampleId) {
        String clinicalPathologySampleIdYear = extractYearFromPathologySampleId(pathologySampleId);
        String clinicalPathologySampleIdConvert = extractBiopsyIdFromPathologySampleId(pathologySampleId);

        if (clinicalPathologySampleIdYear != null && clinicalPathologySampleIdConvert != null) {
            for (SampleData sequencedBiopsy : sequencedBiopsies) {
                String limsPathologySampleIdYear = extractYearFromPathologySampleId(sequencedBiopsy.pathologySampleId());
                String limsPathologySampleIdConvert = extractBiopsyIdFromPathologySampleId(sequencedBiopsy.pathologySampleId());

                if (clinicalPathologySampleIdConvert.equals(limsPathologySampleIdConvert) && clinicalPathologySampleIdYear.equals(
                        limsPathologySampleIdYear)) {
                    return sequencedBiopsy;
                }
            }
        }
        return null;
    }

    @Nullable
    @VisibleForTesting
    static String extractYearFromPathologySampleId(@NotNull String pathologySampleId) {
        if (pathologySampleId.isEmpty()) {
            return null;
        }

        return pathologySampleId.split("-")[0];
    }

    @Nullable
    @VisibleForTesting
    static String extractBiopsyIdFromPathologySampleId(@NotNull String pathologySampleId) {
        if (pathologySampleId.isEmpty()) {
            return null;
        }

        String[] parts = pathologySampleId.split("-");
        if (parts.length >= 2) {
            return parts[1].replaceFirst("^0+(?!$)", "").split(" ")[0];
        } else {
            LOGGER.warn("Could not extract biopsy id from pathology sample id: {}", pathologySampleId);
            return null;
        }
    }

    @NotNull
    private static ValidationFinding biopsyMatchFinding(@NotNull String patientIdentifier, @NotNull String finding,
            @NotNull String details) {
        return ImmutableValidationFinding.builder()
                .level("match")
                .patientIdentifier(patientIdentifier)
                .message(finding)
                .formStatus(FormStatus.undefined())
                .details(details)
                .build();
    }

    @NotNull
    private static BiopsyData toBiopsyData(@NotNull WideFiveDays fiveDays) {
        CuratedBiopsyType curatedBiopsyType = ImmutableCuratedBiopsyType.builder()
                .type("Unknown")
                .searchPrimaryTumorLocation(Strings.EMPTY)
                .searchCancerSubType(Strings.EMPTY)
                .searchBiopsySite(Strings.EMPTY)
                .searchBiopsyLocation(Strings.EMPTY)
                .build();

        return BiopsyData.of(fiveDays.biopsyDate(),
                null,
                null,
                curatedBiopsyType,
                fiveDays.biopsySite(),
                fiveDays.sampleTissue(),
                FormStatus.undefined());
    }

    @NotNull
    private List<BiopsyTreatmentData> buildBiopsyTreatmentData(@NotNull String patientIdentifier) {
        List<BiopsyTreatmentData> biopsyTreatmentDataList = Lists.newArrayList();

        for (WidePreAvlTreatmentData preAvlTreatment : dataForPatient(wideEcrfModel.preAvlTreatments(), patientIdentifier)) {
            biopsyTreatmentDataList.add(BiopsyTreatmentData.of(null,
                    "no",
                    null,
                    preAvlTreatmentDrugList(preAvlTreatment, treatmentCurator),
                    FormStatus.undefined()));
        }

        for (WideAvlTreatmentData avlTreatment : dataForPatient(wideEcrfModel.avlTreatments(), patientIdentifier)) {
            biopsyTreatmentDataList.add(BiopsyTreatmentData.of(null,
                    "yes",
                    null,
                    avlTreatmentDrugList(avlTreatment, treatmentCurator),
                    FormStatus.undefined()));
        }

        return biopsyTreatmentDataList;
    }

    @NotNull
    public static List<DrugData> preAvlTreatmentDrugList(@NotNull WidePreAvlTreatmentData preAvlTreatment,
            @NotNull TreatmentCurator treatmentCurator) {
        List<DrugData> drugs = Lists.newArrayList();

        LocalDate drugsEndDate = preAvlTreatment.lastSystemicTherapyDate();
        if (!preAvlTreatment.drug1().isEmpty()) {
            drugs.add(toSingleDrug(preAvlTreatment.drug1(), null, drugsEndDate, treatmentCurator));
        }

        if (!preAvlTreatment.drug2().isEmpty()) {
            drugs.add(toSingleDrug(preAvlTreatment.drug2(), null, drugsEndDate, treatmentCurator));
        }

        if (!preAvlTreatment.drug3().isEmpty()) {
            drugs.add(toSingleDrug(preAvlTreatment.drug3(), null, drugsEndDate, treatmentCurator));
        }

        if (!preAvlTreatment.drug4().isEmpty()) {
            drugs.add(toSingleDrug(preAvlTreatment.drug4(), null, drugsEndDate, treatmentCurator));
        }

        return drugs;
    }

    @NotNull
    private static List<DrugData> avlTreatmentDrugList(@NotNull WideAvlTreatmentData avlTreatment,
            @NotNull TreatmentCurator treatmentCurator) {
        List<DrugData> drugs = Lists.newArrayList();
        String drugName = avlTreatment.drug();

        if (!drugName.isEmpty()) {
            drugs.add(toSingleDrug(drugName, avlTreatment.startDate(), avlTreatment.endDate(), treatmentCurator));
        }
        return drugs;
    }

    @NotNull
    private static DrugData toSingleDrug(@NotNull String drug, @Nullable LocalDate startDate, @Nullable LocalDate endDate,
            @NotNull TreatmentCurator treatmentCurator) {
        return ImmutableDrugData.builder()
                .name(drug)
                .startDate(startDate)
                .endDate(endDate)
                .bestResponse(null)
                .curatedDrugs(treatmentCurator.search(drug))
                .build();
    }

    @NotNull
    private List<BiopsyTreatmentResponseData> buildBiopsyTreatmentResponseData(@NotNull String patientIdentifier) {
        List<BiopsyTreatmentResponseData> biopsyTreatmentResponseDataList = Lists.newArrayList();
        for (WideResponseData response : dataForPatient(wideEcrfModel.responses(), patientIdentifier)) {
            biopsyTreatmentResponseDataList.add(BiopsyTreatmentResponseData.of(null,
                    null,
                    response.date(),
                    determineResponse(response),
                    "yes",
                    null,
                    FormStatus.undefined()));
        }

        return biopsyTreatmentResponseDataList;
    }

    @NotNull
    @VisibleForTesting
    static String determineResponse(@NotNull WideResponseData response) {
        String responseString = Strings.EMPTY;
        if (response.recistDone()) {
            if (!response.recistResponse().isEmpty()) {
                responseString = "(" + response.timePoint() + ") " + response.recistResponse();
            }
        } else if (!response.noRecistResponse().isEmpty()) {
            responseString = "(" + response.timePoint() + ") " + response.noRecistResponse();
            if (!response.noRecistReasonStopTreatment().isEmpty()) {
                if (response.noRecistReasonStopTreatmentOther().isEmpty()) {
                    responseString = responseString + " (" + response.noRecistReasonStopTreatment() + ")";
                } else {
                    responseString = responseString + " (" + response.noRecistReasonStopTreatmentOther() + ")";
                }
            }
        }
        return responseString;
    }

    @NotNull
    private static <T extends WideClinicalData> List<T> dataForPatient(@NotNull List<T> data, @NotNull String patientIdentifier) {
        List<T> elementsForPatient = Lists.newArrayList();
        for (T element : data) {
            if (element.widePatientId().equals(patientIdentifier)) {
                elementsForPatient.add(element);
            }
        }
        return elementsForPatient;
    }
}