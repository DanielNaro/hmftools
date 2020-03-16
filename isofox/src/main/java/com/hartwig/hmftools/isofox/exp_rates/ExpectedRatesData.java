package com.hartwig.hmftools.isofox.exp_rates;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.sig_analyser.common.SigMatrix;

public class ExpectedRatesData
{
    public final String Id;

    // equivalent of buckets - 0-N transcripts and the fragment type (eg SHORT, SPLICED etc)
    public final List<String> Categories;

    // equivalent of signature names - all transcript names (ie StableId) and a GeneId for each gene's unspliced region
    public final List<String> TranscriptIds;

    private SigMatrix mTranscriptDefinitions;

    public ExpectedRatesData(final String id)
    {
        Id = id;
        Categories = Lists.newArrayList();
        TranscriptIds = Lists.newArrayList();
        mTranscriptDefinitions = null;
    }

    public SigMatrix getTranscriptDefinitions() { return mTranscriptDefinitions; }

    public boolean validData()
    {
        if(Categories.isEmpty() || mTranscriptDefinitions == null)
            return false;

        if(mTranscriptDefinitions.Cols != TranscriptIds.size())
            return false;

        if(mTranscriptDefinitions.Rows != Categories.size())
            return false;

        return true;
    }

    public void initialiseTranscriptDefinitions()
    {
        if(Categories.isEmpty() || TranscriptIds.isEmpty())
            return;

        mTranscriptDefinitions = new SigMatrix(Categories.size(), TranscriptIds.size());
    }

    public int getTranscriptIndex(final String trans)
    {
        for(int i = 0; i < TranscriptIds.size(); ++i)
        {
            if(TranscriptIds.get(i).equals(trans))
                return i;
        }

        return -1;
    }

    public int getCategoryIndex(final String category)
    {
        for(int i = 0; i < Categories.size(); ++i)
        {
            if(Categories.get(i).equals(category))
                return i;
        }

        return -1;
    }

    public void addCategory(final String category)
    {
        if(!Categories.contains(category))
            Categories.add(category);
    }

}
