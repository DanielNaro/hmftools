package com.hartwig.hmftools.common.hla;

import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion.V37;
import static com.hartwig.hmftools.common.region.BaseRegion.positionWithin;
import static com.hartwig.hmftools.common.region.BaseRegion.positionsOverlap;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.gene.GeneData;
import com.hartwig.hmftools.common.genome.position.GenomePosition;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeFunctions;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class HlaCommon
{
    public static final String HLA_CHROMOSOME_V37 = "6";
    public static final String HLA_CHROMOSOME_V38 = RefGenomeFunctions.enforceChrPrefix(HLA_CHROMOSOME_V37);
    //After lifting HLA genes with UCSC, the genes are still on chr6
    public static final String HLA_CHROMOSOME_CHM13 = RefGenomeFunctions.enforceChrPrefix(HLA_CHROMOSOME_V37);

    public static final List<String> HLA_GENES = Lists.newArrayList("HLA-A","HLA-B","HLA-C","HLA-DQA1","HLA-DQB1","HLA-DRB1");

    public static final List<GeneData> HLA_GENE_DATA = Lists.newArrayList();
    public static final Logger SG_LOGGER = LogManager.getLogger(HlaCommon.class);

    public static String hlaChromosome(final RefGenomeVersion version) {
        switch (version){
            case V37:
                return HLA_CHROMOSOME_V37;
            case V38:
                return HLA_CHROMOSOME_V38;
            case HS1:
                return HLA_CHROMOSOME_CHM13;
            default:
                throw new IllegalArgumentException();
        } }

    public static void populateGeneData(final List<GeneData> geneDataList)
    {
        SG_LOGGER.warn("geneDataList: {}", geneDataList);
        SG_LOGGER.warn("geneDataList.size: {}",
                geneDataList.size());
        SG_LOGGER.warn("HLA_GENES: {}",
                HLA_GENES);
        SG_LOGGER.warn("HLA_GENES.size: {}",
                HLA_GENES.size());
        HLA_GENE_DATA.addAll(geneDataList.stream()
                .filter(x -> HLA_GENES.contains(x.GeneName)).collect(Collectors.toList()));
    }

    public static boolean containsPosition(final GenomePosition position)
    {
        return containsPosition(position.chromosome(), position.position());
    }

    public static boolean containsPosition(final String chromosome, final int position)
    {
        if(!chromosome.equals(HLA_CHROMOSOME_V37) && !chromosome.equals(HLA_CHROMOSOME_V38) && !chromosome.equals(HLA_CHROMOSOME_CHM13))
            return false;

        return HLA_GENE_DATA.stream()
                .anyMatch(x -> chromosome.equals(x.Chromosome) && positionWithin(position, x.GeneStart, x.GeneEnd));
    }

    public static boolean overlaps(final String chromosome, final int posStart, final int posEnd)
    {
        if(!chromosome.equals(HLA_CHROMOSOME_V37) && !chromosome.equals(HLA_CHROMOSOME_V38))
            return false;

        return HLA_GENE_DATA.stream()
                .anyMatch(x -> chromosome.equals(x.Chromosome) && positionsOverlap(posStart, posEnd, x.GeneStart, x.GeneEnd));
    }
}
