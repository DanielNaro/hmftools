package com.hartwig.hmftools.isofox.results;

import static com.hartwig.hmftools.isofox.common.FragmentType.ALT;
import static com.hartwig.hmftools.isofox.common.FragmentType.CHIMERIC;
import static com.hartwig.hmftools.isofox.common.FragmentType.DUPLICATE;
import static com.hartwig.hmftools.isofox.common.FragmentType.READ_THROUGH;
import static com.hartwig.hmftools.isofox.common.FragmentType.TOTAL;
import static com.hartwig.hmftools.isofox.common.FragmentType.TRANS_SUPPORTING;
import static com.hartwig.hmftools.isofox.common.FragmentType.UNSPLICED;
import static com.hartwig.hmftools.isofox.common.FragmentType.typeAsInt;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.DELIMITER;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.FLD_CHROMOSOME;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.FLD_GENE_ID;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.FLD_GENE_NAME;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.FLD_GENE_SET_ID;
import static com.hartwig.hmftools.isofox.results.TranscriptResult.FLD_TPM;

import java.util.StringJoiner;

import com.hartwig.hmftools.common.ensemblcache.EnsemblGeneData;
import com.hartwig.hmftools.isofox.common.GeneCollection;
import com.hartwig.hmftools.isofox.common.GeneReadData;

import org.immutables.value.Value;

public class GeneResult
{
    public final EnsemblGeneData GeneData;
    public final String CollectionId;
    public final int IntronicLength;
    public final int TransCount;

    private double mSplicedAlloc;
    private double mUnsplicedAlloc;
    private double mRawTpm;
    private double mAdjustedTpm;
    private double mFitResiduals;

    public GeneResult(final GeneCollection geneCollection, final GeneReadData geneReadData)
    {
        GeneData = geneReadData.GeneData;
        CollectionId = geneCollection.chrId();

        long exonicLength = geneReadData.calcExonicRegionLength();
        IntronicLength = (int)(GeneData.length() - exonicLength);
        TransCount = geneReadData.getTranscripts().size();

        mFitResiduals = 0;
        mSplicedAlloc = 0;
        mRawTpm = 0;
        mAdjustedTpm = 0;
        mUnsplicedAlloc = 0;
    }

    public void setFitAllocation(double splicedAlloc, double unsplicedAlloc)
    {
        mSplicedAlloc = splicedAlloc;
        mUnsplicedAlloc = unsplicedAlloc;
    }

    public void setTPM(double raw, double adjusted)
    {
        mRawTpm = raw;
        mAdjustedTpm = adjusted;
    }

    public void setFitResiduals(double residuals) { mFitResiduals = residuals; }
    public double getFitResiduals() { return mFitResiduals; }

    public static final String FLD_SUPPORTING_TRANS = "SupportingTrans";
    public static final String FLD_UNSPLICED = "Unspliced";

    public static String csvHeader()
    {
        return new StringJoiner(DELIMITER)
                .add(FLD_GENE_ID)
                .add(FLD_GENE_NAME)
                .add(FLD_CHROMOSOME)
                .add("GeneLength")
                .add("IntronicLength")
                .add("TranscriptCount")
                .add(FLD_GENE_SET_ID)
                .add("SplicedFragments")
                .add("UnsplicedFragments")
                .add(FLD_TPM)
                .add("RawTPM")
                .add("FitResiduals")
                .toString();
    }

    public String toCsv()
    {
        return new StringJoiner(DELIMITER)
                .add(GeneData.GeneId)
                .add(GeneData.GeneName)
                .add(GeneData.Chromosome)
                .add(String.valueOf(GeneData.length()))
                .add(String.valueOf(IntronicLength))
                .add(String.valueOf(TransCount))
                .add(CollectionId)
                .add(String.format("%.0f", mSplicedAlloc))
                .add(String.format("%.0f", mUnsplicedAlloc))
                .add(String.format("%6.3e", mAdjustedTpm))
                .add(String.format("%6.3e", mRawTpm))
                .add(String.format("%.1f", getFitResiduals()))
                .toString();
    }
}
