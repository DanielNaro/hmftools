package com.hartwig.hmftools.patientdb.readers.drup;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.ecrf.datamodel.EcrfPatient;
import com.hartwig.hmftools.common.ecrf.formstatus.FormStatus;
import com.hartwig.hmftools.patientdb.curators.BiopsySiteCurator;
import com.hartwig.hmftools.patientdb.curators.TumorLocationCurator;
import com.hartwig.hmftools.patientdb.data.BaselineData;
import com.hartwig.hmftools.patientdb.data.BiopsyData;
import com.hartwig.hmftools.patientdb.data.ImmutablePreTreatmentData;
import com.hartwig.hmftools.patientdb.data.Patient;
import com.hartwig.hmftools.patientdb.data.PreTreatmentData;
import com.hartwig.hmftools.patientdb.data.SampleData;
import com.hartwig.hmftools.patientdb.matchers.BiopsyMatcher;
import com.hartwig.hmftools.patientdb.matchers.MatchResult;
import com.hartwig.hmftools.patientdb.readers.EcrfPatientReader;

import org.jetbrains.annotations.NotNull;

public class DrupPatientReader implements EcrfPatientReader {

    @NotNull
    private final BaselineReader baselineReader;
    @NotNull
    private final BiopsyReader biopsyReader;

    public DrupPatientReader(@NotNull TumorLocationCurator tumorLocationCurator, @NotNull BiopsySiteCurator biopsySiteCurator) {
        this.baselineReader = new BaselineReader(tumorLocationCurator);
        this.biopsyReader = new BiopsyReader(biopsySiteCurator);
    }

    @NotNull
    @Override
    public Patient read(@NotNull EcrfPatient ecrfPatient, @NotNull List<SampleData> sequencedSamples) {
        BaselineData baselineData = baselineReader.read(ecrfPatient);
        PreTreatmentData noPreTreatmentData = ImmutablePreTreatmentData.builder().formStatus(FormStatus.undefined()).build();
        List<BiopsyData> clinicalBiopsies = biopsyReader.read(ecrfPatient, baselineData.curatedTumorLocation());

        MatchResult<BiopsyData> matchedBiopsies =
                BiopsyMatcher.matchBiopsiesToTumorSamples(ecrfPatient.patientId(), sequencedSamples, clinicalBiopsies);

        return new Patient(ecrfPatient.patientId(),
                baselineData,
                noPreTreatmentData,
                sequencedSamples,
                matchedBiopsies.values(),
                Lists.newArrayList(),
                Lists.newArrayList(),
                Lists.newArrayList(),
                Lists.newArrayList(),
                matchedBiopsies.findings());
    }
}
