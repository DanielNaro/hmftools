package com.hartwig.hmftools.linx.gene;

import static java.lang.Math.max;
import static java.lang.Math.min;

import static com.hartwig.hmftools.common.drivercatalog.DriverType.DEL;
import static com.hartwig.hmftools.common.drivercatalog.LikelihoodMethod.AMP;
import static com.hartwig.hmftools.common.purple.copynumber.CopyNumberMethod.BAF_WEIGHTED;
import static com.hartwig.hmftools.linx.fusion.FusionFinder.BIOTYPE_PROTEIN_CODING;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalog;
import com.hartwig.hmftools.common.drivercatalog.DriverCategory;
import com.hartwig.hmftools.common.drivercatalog.DriverType;
import com.hartwig.hmftools.common.drivercatalog.ImmutableDriverCatalog;
import com.hartwig.hmftools.common.drivercatalog.LikelihoodMethod;
import com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.purple.gene.GeneCopyNumber;
import com.hartwig.hmftools.common.purple.gene.ImmutableGeneCopyNumber;
import com.hartwig.hmftools.common.purple.segment.SegmentSupport;
import com.hartwig.hmftools.common.variant.structural.annotation.EnsemblGeneData;
import com.hartwig.hmftools.common.variant.structural.annotation.ExonData;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneAnnotation;
import com.hartwig.hmftools.common.variant.structural.annotation.TranscriptData;
import com.hartwig.hmftools.linx.gene.SvGeneTranscriptCollection;

public class GeneTestUtils
{
    public static GeneAnnotation createGeneAnnotation(int svId, boolean isStart, final String geneName, String stableId, int strand,
            final String chromosome, long position, int orientation)
    {
        String karyotypeBand = "";

        GeneAnnotation gene = new GeneAnnotation(svId, isStart, geneName, stableId, strand, karyotypeBand);
        gene.setPositionalData(chromosome, position, (byte)orientation);

        return gene;
    }

    // Ensembl data types
    public static EnsemblDataCache createGeneDataCache()
    {
        return new EnsemblDataCache("", RefGenomeVersion.HG37);
    }
    public static EnsemblGeneData createEnsemblGeneData(String geneId, String geneName, String chromosome, int strand, long geneStart, long geneEnd)
    {
        return new EnsemblGeneData(geneId, geneName, chromosome, (byte)strand, geneStart, geneEnd,  "");
    }

    public static void addTransExonData(EnsemblDataCache geneTransCache, final String geneId, List<TranscriptData> transDataList)
    {
        geneTransCache.getTranscriptDataMap().put(geneId, transDataList);
    }

    public static void addGeneData(EnsemblDataCache geneTransCache, final String chromosome, List<EnsemblGeneData> geneDataList)
    {
        geneTransCache.getChrGeneDataMap().put(chromosome, geneDataList);
    }

    public static int getCodingBases(final Long start, final Long end)
    {
        if(start != null && end != null)
            return (int)(end - start) + 1;
        return 0;
    }

    public static TranscriptData createTransExons(final String geneId, int transId, byte strand,
            long[] exonStarts, int[] exonEndPhases, int exonLength)
    {
        return createTransExons(geneId, transId, strand,exonStarts, exonEndPhases, exonLength, false);
    }

    public static String generateTransName(int transId) { return String.format("TRAN%04d", transId); }

    public static TranscriptData createTransExons(final String geneId, int transId, byte strand,
            long[] exonStarts, int[] exonEndPhases, int exonLength, boolean isCanonical)
    {
        if(exonStarts.length == 0 || exonStarts.length != exonEndPhases.length)
            return null;

        int exonCount = exonStarts.length;
        long transStart = exonStarts[0];
        long transEnd = exonStarts[exonCount-1] + exonLength;

        Long codingStart = null;
        Long codingEnd = null;

        int[] exonPhases = new int[exonCount];

        // work out phases and coding start & end
        for(int i = 0; i < exonCount; ++i)
        {
            long exonStart = exonStarts[i];

            int exonEndPhase = exonEndPhases[i];

            if(strand == 1)
                exonPhases[i] = i > 0 ? exonEndPhases[i-1] : -1;
            else
                exonPhases[i] = i < exonCount - 1 ? exonEndPhases[i+1] : -1;

            if(codingStart == null && ((strand == 1 && exonEndPhase != -1) || (strand == -1 && exonPhases[i] != -1)))
            {
                codingStart = new Long(exonStart + exonLength / 2);
            }
            else if(codingStart != null && codingEnd == null
            && ((strand == 1 && exonEndPhase == -1) || (strand == -1 && exonPhases[i] == -1)))
            {
                codingEnd = new Long(exonStart + exonLength / 2);
            }
        }

        TranscriptData transData = new TranscriptData(transId, generateTransName(transId), geneId, isCanonical, strand, transStart, transEnd,
                codingStart, codingEnd, BIOTYPE_PROTEIN_CODING);

        List<ExonData> exons = Lists.newArrayList();

        for(int i = 0; i < exonCount; ++i)
        {
            long exonStart = exonStarts[i];
            long exonEnd = exonStarts[i] + exonLength;
            int exonRank = strand == 1 ? i + 1 : exonCount - i;

            exons.add(new ExonData(transId, exonStart, exonEnd, exonRank, exonPhases[i], exonEndPhases[i]));
        }

        transData.setExons(exons);

        return transData;
    }

    public static TranscriptData createTransExons(final String geneId, int transId, byte strand,
            long[] exonStarts, int exonLength, Long codingStart, Long codingEnd, boolean isCanonical, final String biotype)
    {
        if(exonStarts.length == 0 || exonLength <= 0)
            return null;

        int exonCount = exonStarts.length;
        long transStart = exonStarts[0];
        long transEnd = exonStarts[exonCount-1] + exonLength;

        final List<ExonData> exons = Lists.newArrayList();

        boolean hasCodingBases = codingStart != null && codingEnd != null;

        // work out phases based on coding start & end
        boolean inCoding = false;
        boolean finishedCoding = false;
        int lastExonEndPhase = -1;

        if(strand == 1)
        {
            for (int i = 0; i < exonCount; ++i)
            {
                long exonStart = exonStarts[i];
                long exonEnd = exonStart + exonLength;
                int exonRank = i + 1;

                int exonPhase = 0;
                int exonStartPhase = -1;
                int exonEndPhase = -1;
                long exonCodingStart = 0;

                if (hasCodingBases && !finishedCoding)
                {
                    if (!inCoding)
                    {
                        if (codingStart <= exonEnd)
                        {
                            inCoding = true;
                            exonPhase = 0;
                            exonStartPhase = codingStart == exonStart ? 0 : -1;
                            exonCodingStart = codingStart;
                        }
                    }
                    else
                    {
                        exonPhase = lastExonEndPhase;
                        exonStartPhase = lastExonEndPhase;
                        exonCodingStart = exonStart;
                    }

                    if (inCoding)
                    {
                        long exonCodingBases = min(exonEnd, codingEnd) - exonCodingStart + 1;
                        exonEndPhase = (int) ((exonPhase + exonCodingBases) % 3);
                        lastExonEndPhase = exonEndPhase;

                        if (codingEnd <= exonEnd)
                        {
                            finishedCoding = true;
                            inCoding = false;
                            exonEndPhase = -1;
                        }
                    }
                }

                exons.add(new ExonData(transId, exonStart, exonEnd, exonRank, exonStartPhase, exonEndPhase));
            }
        }
        else
        {
            for (int i = exonCount-1; i >= 0; --i)
            {
                long exonStart = exonStarts[i];
                long exonEnd = exonStart + exonLength;
                int exonRank = exonCount - i;

                int exonPhase = 0;
                int exonStartPhase = -1;
                int exonEndPhase = -1;
                long exonCodingEnd = 0;

                if (hasCodingBases && !finishedCoding)
                {
                    if (!inCoding)
                    {
                        if (codingEnd >= exonStart)
                        {
                            inCoding = true;
                            exonPhase = 0;
                            exonStartPhase = codingStart == exonStart ? 0 : -1;
                            exonCodingEnd = codingEnd;
                        }
                    }
                    else
                    {
                        exonPhase = lastExonEndPhase;
                        exonStartPhase = lastExonEndPhase;
                        exonCodingEnd = exonEnd;
                    }

                    if (inCoding)
                    {
                        long exonCodingBases = exonCodingEnd - max(exonStart, codingStart) + 1;
                        exonEndPhase = (int) ((exonPhase + exonCodingBases) % 3);
                        lastExonEndPhase = exonEndPhase;

                        if (codingStart >= exonStart)
                        {
                            finishedCoding = false;
                            inCoding = false;
                            exonEndPhase = -1;
                        }
                    }
                }

                exons.add(0, new ExonData(transId, exonStart, exonEnd, exonRank, exonStartPhase, exonEndPhase));
            }
        }

        TranscriptData transData = new TranscriptData(transId, generateTransName(transId), geneId, isCanonical, strand, transStart, transEnd,
                codingStart, codingEnd, biotype);

        transData.setExons(exons);

        return transData;

    }

}
