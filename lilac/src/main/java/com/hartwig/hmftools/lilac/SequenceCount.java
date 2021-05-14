package com.hartwig.hmftools.lilac;

import static java.lang.Math.min;

import static com.hartwig.hmftools.common.utils.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.lilac.LilacConfig.LL_LOGGER;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.lilac.fragment.AminoAcidFragment;
import com.hartwig.hmftools.lilac.fragment.NucleotideFragment;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Pair;

public final class SequenceCount
{
    private final int mMinCount;
    private final Map<String,Integer>[] mSeqCountsList;

    public SequenceCount(int minCount, final Map<String,Integer>[] seqCounts)
    {
        mMinCount = minCount;
        mSeqCountsList = seqCounts;
    }

    public final int getLength()
    {
        return mSeqCountsList.length;
    }

    public final Map<String,Integer> get(int locus)
    {
        if (locus >= mSeqCountsList.length || mSeqCountsList[locus] == null)
        {
            LL_LOGGER.error("invalid sequence count index({}) size({}) look-up", locus, mSeqCountsList.length);
            return Maps.newHashMap();
        }

        return mSeqCountsList[locus];
    }

    public static SequenceCount nucleotides(int minCount, final List<NucleotideFragment> fragments)
    {
        int length = fragments.stream().mapToInt(x -> x.maxLoci()).max().orElse(0) + 1;

        Map<String,Integer>[] seqCountsList = new Map[length];
        for(int i = 0; i < length; ++i)
        {
            seqCountsList[i] = Maps.newHashMap();
        }

        for(NucleotideFragment fragment : fragments)
        {
            for(int index = 0; index < fragment.getNucleotideLoci().size(); ++index)
            {
                int locus = fragment.getNucleotideLoci().get(index);
                String nucleotide = fragment.getNucleotides().get(index);
                increment(seqCountsList, locus, nucleotide);
            }
        }

        return new SequenceCount(minCount, seqCountsList);
    }

    public static SequenceCount aminoAcids(int minCount, final List<AminoAcidFragment> fragments)
    {
        int length = fragments.stream().mapToInt(x -> x.maxAminoAcidLoci()).max().orElse(0) + 1;

        Map<String,Integer>[] seqCountsList = new Map[length];
        for(int i = 0; i < length; ++i)
        {
            seqCountsList[i] = Maps.newHashMap();
        }

        for(AminoAcidFragment fragment : fragments)
        {
            for(int index = 0; index < fragment.getAminoAcidLoci().size(); ++index)
            {
                int locus = fragment.getAminoAcidLoci().get(index);
                String aminoAcid = fragment.getAminoAcids().get(index);
                increment(seqCountsList, locus, aminoAcid);
            }
        }

        return new SequenceCount(minCount, seqCountsList);
    }

    public final List<Integer> heterozygousLoci()
    {
        List<Integer> indices = Lists.newArrayList();

        for(int i = 0; i < mSeqCountsList.length; ++i)
        {
            if(isHeterozygous(i))
                indices.add(i);
        }

        return indices;
    }

    public final List<Integer> homozygousIndices()
    {
        List<Integer> indices = Lists.newArrayList();

        for(int i = 0; i < mSeqCountsList.length; ++i)
        {
            if(isHomozygous(i))
                indices.add(i);
        }

        return indices;
    }

    private final boolean isHomozygous(int index)
    {
        Map<String,Integer> seqCounts = get(index);
        return seqCounts.values().stream().filter(x -> x >= mMinCount).count() == 1;
    }

    private final boolean isHeterozygous(int index)
    {
        Map<String,Integer> seqCounts = get(index);
        return seqCounts.values().stream().filter(x -> x >= mMinCount).count() > 1;
    }

    public final List<String> getMinCountSequences(int index) // formally sequenceAt()
    {
        Map<String,Integer> seqCounts = get(index);

        return seqCounts.entrySet().stream()
                .filter(x -> x.getValue() >= mMinCount)
                .map(x -> x.getKey()).collect(Collectors.toList());
    }

    public final int depth(int index)
    {
        Map<String,Integer> seqCounts = get(index);
        return seqCounts.values().stream().mapToInt(x -> x).sum();
    }

    private static void increment(Map<String,Integer>[] seqCountsList, int index, String aminoAcid)
    {
        if(seqCountsList[index] == null)
        {
            seqCountsList[index] = Maps.newHashMap();
        }

        Map<String,Integer> seqCounts = seqCountsList[index];

        Integer count = seqCounts.get(aminoAcid);
        if(count != null)
            seqCounts.put(aminoAcid, count + 1);
        else
            seqCounts.put(aminoAcid, 1);
    }

    public final void writeVertically(final String fileName)
    {
        try
        {
            BufferedWriter writer = createBufferedWriter(fileName, false);

            for(int i = 0; i < getLength(); ++i)
            {
                StringJoiner lineBuilder = new StringJoiner("\t");
                lineBuilder.add(String.valueOf(i));

                Map<String,Integer> seqMap = mSeqCountsList[i];

                List<Pair<String,Integer>> sortedCounts = Lists.newArrayList();

                for(Map.Entry<String,Integer> entry : seqMap.entrySet())
                {
                    int index = 0;
                    while(index < sortedCounts.size())
                    {
                        if(entry.getValue() > sortedCounts.get(index).getSecond())
                            break;

                        ++index;
                    }

                    sortedCounts.add(index, new Pair(entry.getKey(), entry.getValue()));
                }

                for(int j = 0; j <= min(5, sortedCounts.size() - 1); ++j)
                {
                    Pair<String,Integer> pair = sortedCounts.get(j);
                    lineBuilder.add(pair.getFirst());
                    lineBuilder.add(String.valueOf(pair.getSecond()));
                }

                writer.write(lineBuilder.toString());
                writer.newLine();
            }

            writer.close();
        }
        catch(IOException e)
        {
            LL_LOGGER.error("failed to write {}: {}", fileName, e.toString());
            return;
        }

        /*

        for (i in 0 until length) {
            val lineBuilder = StringJoiner("\t").add(i.toString())
            val baseCountList = count[i].map { (k, v) -> Pair(k, v) }.sortedBy { it.second }.reversed()
            for (j in 0..min(5, baseCountList.size - 1)) {
                val (base, count) = baseCountList[j]
                lineBuilder.add(base).add(count.toString())
            }

            file.appendText(lineBuilder.toString() + "\n")
        }
         */
    }
}
