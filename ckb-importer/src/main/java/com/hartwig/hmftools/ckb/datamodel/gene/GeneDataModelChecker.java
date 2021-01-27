package com.hartwig.hmftools.ckb.datamodel.gene;

import java.util.Map;

import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.utils.json.JsonDatamodelChecker;

import org.jetbrains.annotations.NotNull;

public class GeneDataModelChecker {

    private GeneDataModelChecker() {

    }

    @NotNull
    public static JsonDatamodelChecker geneObjectChecker() {
        Map<String, Boolean> map = Maps.newHashMap();
        map.put("id", true);
        map.put("geneSymbol", true);
        map.put("terms", true);
        map.put("entrezId", true);
        map.put("synonyms", true);
        map.put("chromosome", true);
        map.put("mapLocation", true);
        map.put("geneDescriptions", true);
        map.put("canonicalTranscript", true);
        map.put("geneRole", true);
        map.put("createDate", true);
        map.put("updateDate", true);
        map.put("clinicalTrials", true);
        map.put("evidence", true);
        map.put("variants", true);
        map.put("molecularProfiles", true);
        map.put("categoryVariants", true);

        return new JsonDatamodelChecker("GeneObject", map);
    }

    @NotNull
    public static JsonDatamodelChecker geneDescriptionObjectChecker() {
        Map<String, Boolean> map = Maps.newHashMap();
        map.put("description", true);
        map.put("references", true);

        return new JsonDatamodelChecker("GeneDescriptionObject", map);
    }

    @NotNull
    public static JsonDatamodelChecker geneReferenceObjectChecker() {
        Map<String, Boolean> map = Maps.newHashMap();
        map.put("id", true);
        map.put("pubMedId", true);
        map.put("title", true);
        map.put("url", true);

        return new JsonDatamodelChecker("GeneReferenceObject", map);
    }

    @NotNull
    public static JsonDatamodelChecker geneClinicalTrialObjectChecker() {
        Map<String, Boolean> map = Maps.newHashMap();
        map.put("nctId", true);
        map.put("title", true);
        map.put("phase", true);
        map.put("recruitment", true);
        map.put("therapies", true);

        return new JsonDatamodelChecker("GeneClinicalTrialObject", map);
    }

    @NotNull
    public static JsonDatamodelChecker geneTherapiesObjectChecker() {
        Map<String, Boolean> map = Maps.newHashMap();
        map.put("id", true);
        map.put("therapyName", true);
        map.put("synonyms", true);

        return new JsonDatamodelChecker("GeneTherapiesObject", map);
    }

    @NotNull
    public static JsonDatamodelChecker geneEvidenceObjectChecker() {
        Map<String, Boolean> map = Maps.newHashMap();
        map.put("id", true);
        map.put("approvalStatus", true);
        map.put("evidenceType", true);
        map.put("efficacyEvidence", true);
        map.put("molecularProfile", true);
        map.put("therapy", true);
        map.put("indication", true);
        map.put("responseType", true);
        map.put("references", true);
        map.put("ampCapAscoEvidenceLevel", true);
        map.put("ampCapAscoInferredTier", true);

        return new JsonDatamodelChecker("GeneEvidenceObject", map);
    }

    @NotNull
    public static JsonDatamodelChecker geneMolecularProfileObjectChecker() {
        Map<String, Boolean> map = Maps.newHashMap();
        map.put("id", true);
        map.put("profileName", true);
        map.put("profileTreatmentApproaches", false);

        return new JsonDatamodelChecker("GeneMolecularProfileObject", map);
    }

    @NotNull
    public static JsonDatamodelChecker geneTherapyObjectChecker() {
        Map<String, Boolean> map = Maps.newHashMap();
        map.put("id", true);
        map.put("therapyName", true);
        map.put("synonyms", true);

        return new JsonDatamodelChecker("GeneTherapyObject", map);
    }

    @NotNull
    public static JsonDatamodelChecker geneIndicationObjectChecker() {
        Map<String, Boolean> map = Maps.newHashMap();
        map.put("id", true);
        map.put("name", true);
        map.put("source", true);

        return new JsonDatamodelChecker("GeneIndicationObject", map);
    }

    @NotNull
    public static JsonDatamodelChecker geneVariantObjectChecker() {
        Map<String, Boolean> map = Maps.newHashMap();
        map.put("id", true);
        map.put("fullName", true);
        map.put("impact", true);
        map.put("proteinEffect", true);
        map.put("geneVariantDescriptions", true);

        return new JsonDatamodelChecker("GeneVariantObject", map);
    }

    @NotNull
    public static JsonDatamodelChecker geneVariantDescriptionObjectChecker() {
        Map<String, Boolean> map = Maps.newHashMap();
        map.put("description", true);
        map.put("references", true);

        return new JsonDatamodelChecker("GeneVariantDescriptionObject", map);
    }

    @NotNull
    public static JsonDatamodelChecker geneProfileTreatmentApproachObjectChecker() {
        Map<String, Boolean> map = Maps.newHashMap();
        map.put("id", true);
        map.put("name", true);
        map.put("profileName", true);

        return new JsonDatamodelChecker("GeneProfileTreatmentApproachObject", map);
    }

    @NotNull
    public static JsonDatamodelChecker geneCategoryVariantObjectChecker() {
        Map<String, Boolean> map = Maps.newHashMap();
        map.put("id", true);
        map.put("fullName", true);
        map.put("impact", true);
        map.put("proteinEffect", true);
        map.put("geneVariantDescriptions", true);

        return new JsonDatamodelChecker("GeneCategoryVariantObject", map);
    }

}
