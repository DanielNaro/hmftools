package com.hartwig.hmftools.isofox.exp_rates;

import static com.hartwig.hmftools.isofox.exp_rates.ExpectedRatesGenerator.FL_FREQUENCY;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.appendStr;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.appendStrList;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Lists;

// counts of fragments which could support a set of transcripts and/or unspliced genes

public class CategoryCountsData
{
    private final List<Integer> mTranscripts;
    private final List<String> mUnsplicedGenes;
    private int mFragmentCount;
    private int[] mFragmentCountsByLength;

    private final String mCombinedKey;

    public CategoryCountsData(final List<Integer> transcripts, final List<String> unsplicedGenes)
    {
        mTranscripts = transcripts;
        mUnsplicedGenes = unsplicedGenes;
        mFragmentCount = 0;

        mCombinedKey = formTranscriptIds();

        mFragmentCountsByLength = null;
    }

    public CategoryCountsData(final String categoryStr, int fragLengths)
    {
        mCombinedKey = categoryStr;
        mTranscripts = Lists.newArrayList();
        mUnsplicedGenes = Lists.newArrayList();
        mFragmentCount = 0;
        mFragmentCountsByLength = new int[fragLengths];

        parseCombinedKey();
    }

    public void initialiseLengthCounts(int fragLengths)
    {
        if(fragLengths > 0)
            mFragmentCountsByLength = new int[fragLengths];
    }

    public final List<Integer> transcriptIds() { return mTranscripts; }
    public final List<String> unsplicedGeneIds() { return mUnsplicedGenes; }
    public final String combinedKey() { return mCombinedKey; }

    public String impliedType()
    {
        if(mUnsplicedGenes.isEmpty())
            return "SPLICED/LONG";
        else if(mTranscripts.isEmpty())
            return "UNSPLICED";
        else
            return "SHORT";
    }

    public boolean matches(final List<Integer> transcripts, final List<String> unsplicedGenes)
    {
        if(mTranscripts.size() != transcripts.size() || mUnsplicedGenes.size() != unsplicedGenes.size())
            return false;

        for(int transId : transcripts)
        {
            if(!mTranscripts.stream().anyMatch(x -> x == transId))
                return false;
        }

        for(String geneId : unsplicedGenes)
        {
            if(!mUnsplicedGenes.stream().anyMatch(x -> x.equals(geneId)))
                return false;
        }

        return true;
    }

    public final int fragmentCount() { return mFragmentCount; }
    public final int[] fragmentCountsByLength() { return mFragmentCountsByLength; }

    public void addCounts(int count)
    {
        mFragmentCount += count;
    }

    public void addCounts(int count, int lengthIndex)
    {
        mFragmentCount += count;
        mFragmentCountsByLength[lengthIndex] += count;
    }

    public void applyFrequencies(final List<int[]> lengthFrequencies)
    {
        for(int i = 0; i < mFragmentCountsByLength.length; ++i)
        {
            mFragmentCountsByLength[i] *= lengthFrequencies.get(i)[FL_FREQUENCY];
        }
    }

    private static final char DELIM = '-';

    private String formTranscriptIds()
    {
        // convert into an order list of ints
        List<Integer> transIds = Lists.newArrayList();

        for (Integer transId : mTranscripts)
        {
            int index = 0;
            while (index < transIds.size())
            {
                if (transId < transIds.get(index))
                    break;

                ++index;
            }

            transIds.add(index, transId);
        }

        List<String> items = Lists.newArrayList();

        for (Integer transId : transIds)
        {
            items.add(String.valueOf(transId));
        }

        items.addAll(mUnsplicedGenes);

        return appendStrList(items, DELIM);
    }

    private static final String GENE_INDENTIFIER = "ENSG";

    private void parseCombinedKey()
    {
        String[] items = mCombinedKey.split(String.valueOf(DELIM));

        for(int i = 0; i < items.length; ++i)
        {
            if(items[i].contains(GENE_INDENTIFIER))
            {
                mUnsplicedGenes.add(items[i]);
            }
            else
            {
                mTranscripts.add(Integer.parseInt(items[i]));
            }
        }
    }

    public String toString()
    {
        return String.format("trans(%d) genes(%d)  key(%s) count(%d)",
                mTranscripts.size(), mUnsplicedGenes.size(), mCombinedKey, mFragmentCount);
    }


}
