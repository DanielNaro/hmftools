package com.hartwig.hmftools.orange.algo.cuppa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import com.hartwig.hmftools.common.cuppa.ClfName;
import com.hartwig.hmftools.common.cuppa.CuppaPredictionEntry;
import com.hartwig.hmftools.common.cuppa.CuppaPredictions;
import com.hartwig.hmftools.common.cuppa.DataType;
import com.hartwig.hmftools.datamodel.cuppa.CuppaData;
import com.hartwig.hmftools.datamodel.cuppa.CuppaPrediction;
import com.hartwig.hmftools.datamodel.cuppa.ImmutableCuppaData;
import com.hartwig.hmftools.datamodel.cuppa.ImmutableCuppaPrediction;

import org.jetbrains.annotations.NotNull;

public final class CuppaDataFactory
{
    @NotNull
    public static CuppaData create(@NotNull String cuppaVisDataTsv) throws Exception
    {
        CuppaPredictions cuppaPredictions = CuppaPredictions.fromTsv(cuppaVisDataTsv);
        return CuppaDataFactory.createFromPredictions(cuppaPredictions);
    }

    @NotNull
    private static CuppaData createFromPredictions(@NotNull CuppaPredictions cuppaPredictions) throws Exception
    {
        List<CuppaPrediction> predictions = extractSortedProbabilities(cuppaPredictions);

        if(predictions.isEmpty())
        {
            throw new IllegalStateException("`CuppaPredictions` must contain a non-empty list of predictions");
        }

        CuppaPrediction bestPrediction = predictions.get(0);
        int simpleDups32To200B = getSvFeatureValue(cuppaPredictions, "sv.SIMPLE_DEL_20KB_1MB");
        int maxComplexSize = getSvFeatureValue(cuppaPredictions, "sv.MAX_COMPLEX_SIZE");
        int telomericSGLs = getSvFeatureValue(cuppaPredictions, "sv.TELOMERIC_SGL");
        int lineCount = getSvFeatureValue(cuppaPredictions, "sv.LINE");

        return ImmutableCuppaData.builder()
                .predictions(predictions)
                .bestPrediction(bestPrediction)
                .simpleDups32To200B(simpleDups32To200B)
                .maxComplexSize(maxComplexSize)
                .telomericSGLs(telomericSGLs)
                .lineCount(lineCount)
                .build();
    }

    @NotNull
    private static List<CuppaPrediction> extractSortedProbabilities(@NotNull CuppaPredictions cuppaPredictions)
    {
        CuppaPredictions probabilitiesAllClassifiers = cuppaPredictions
                .subsetByDataType(DataType.PROB)
                .sortByRank();

        List<CuppaPrediction> cuppaPredictionsOrangeFormat = new ArrayList<>();
        for(String cancerType : probabilitiesAllClassifiers.CancerTypes)
        {
            CuppaPredictions probabilitiesOneCancerType = probabilitiesAllClassifiers.subsetByCancerType(cancerType);

            Map<ClfName, Double> probabilitiesByClassifier = new HashMap<>();
            for(int i = 0; i < probabilitiesOneCancerType.size(); i++)
            {
                probabilitiesByClassifier.put(
                        probabilitiesOneCancerType.get(i).ClfName,
                        probabilitiesOneCancerType.get(i).DataValue
                );
            }

            CuppaPrediction prediction = ImmutableCuppaPrediction.builder()
                    .cancerType(cancerType)
                    .likelihood(probabilitiesByClassifier.get(probabilitiesAllClassifiers.MainCombinedClfName))
                    .genomicPositionClassifier(probabilitiesByClassifier.get(ClfName.GEN_POS))
                    .snvPairwiseClassifier(probabilitiesByClassifier.get(ClfName.SNV96))
                    .featureClassifier(probabilitiesByClassifier.get(ClfName.EVENT))
                    .expressionPairwiseClassifier(probabilitiesByClassifier.get(ClfName.GENE_EXP))
                    .altSjCohortClassifier(probabilitiesByClassifier.get(ClfName.ALT_SJ))
                    .build();

            cuppaPredictionsOrangeFormat.add(prediction);
        }
        return cuppaPredictionsOrangeFormat;
    }

    @VisibleForTesting
    static int getSvFeatureValue(@NotNull CuppaPredictions cuppaPredictions, @NotNull String featureName) throws Exception
    {
        // Feature values are replicated for each cancer type because the `cuppaPredictions` table is in long form.
        // Use `.findFirst()` to get a single value
        CuppaPredictionEntry predictionEntry = cuppaPredictions.PredictionEntries.stream()
                .filter(o -> o.DataType == DataType.FEAT_CONTRIB & o.FeatName.equals(featureName))
                .findFirst()
                .orElseThrow(() -> new Exception("Input CuppaPredictions is empty"));

        return (int) Math.round(predictionEntry.FeatValue);
    }
}
