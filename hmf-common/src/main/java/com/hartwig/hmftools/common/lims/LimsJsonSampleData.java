package com.hartwig.hmftools.common.lims;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.annotations.SerializedName;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Gson.TypeAdapters
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
abstract class LimsJsonSampleData {

    @NotNull
    @SerializedName("sample_name")
    public abstract String sampleId();

    @NotNull
    @SerializedName("patient")
    public abstract String patientId();

    // ref_sample_id could be null in lims if the sample itself is a reference sample
    @Nullable
    @SerializedName("ref_sample_id")
    public abstract String refBarcode();

    @NotNull
    @SerializedName("sample_id")
    public abstract String tumorBarcode();

    @NotNull
    @SerializedName("arrival_date")
    public abstract String arrivalDate();

    // Sampling date is only known for CPCT/DRUP/WIDE/CORE tumor biopsies.
    @Nullable
    @SerializedName("sampling_date")
    public abstract String samplingDate();

    @NotNull
    @SerializedName("requester_email")
    public abstract String requesterEmail();

    @NotNull
    @SerializedName("requester_name")
    public abstract String requesterName();

    @NotNull
    @SerializedName("shallowseq")
    public abstract String shallowSeq();

    // Tumor biopsies analyzed in research context do not have a pathology tumor percentage. Also WIDE samples are no longer sent to PA.
    @Nullable
    @SerializedName("tumor_perc")
    public abstract String pathologyTumorPercentage();

    @NotNull
    @SerializedName("dna_conc")
    public abstract String dnaConcentration();

    @NotNull
    @SerializedName("ptum")
    public abstract String primaryTumor();

    @NotNull
    @SerializedName("submission")
    public abstract String submission();

    // Patient number is only used for CORE project
    @Nullable
    @SerializedName("hospital_patient_id")
    public abstract String hospitalPatientId();

    // Pathology sample id is only used for WIDE project
    @Nullable
    @SerializedName("hospital_pa_sample_id")
    public abstract String hospitalPathologySampleId();

    // Choice regarding reporting of germline findings is only used in WIDE project
    @Nullable
    @SerializedName("germline_findings")
    public abstract String germlineReportingChoice();

    // Lab remarks is an optional field in LIMS
    @Nullable
    @SerializedName("lab_remarks")
    public abstract String labRemarks();

    @NotNull
    @SerializedName("lab_sop_versions")
    abstract String labSopVersions();

    @NotNull
    @Value.Derived
    public String labProcedures() {
        final Pattern pattern = Pattern.compile("PREP(\\d+)V(\\d+)-QC(\\d+)V(\\d+)-SEQ(\\d+)V(\\d+)");
        final Matcher matcher = pattern.matcher(labSopVersions());
        if (matcher.find()) {
            return labSopVersions();
        } else {
            return Lims.NOT_AVAILABLE_STRING;
        }
    }
}
