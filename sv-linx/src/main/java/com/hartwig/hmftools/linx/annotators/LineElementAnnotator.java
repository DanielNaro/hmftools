package com.hartwig.hmftools.linx.annotators;

import static java.lang.Math.abs;

import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.BND;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.DEL;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.INS;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.SGL;
import static com.hartwig.hmftools.linx.analysis.SvClassification.isFilteredResolvedType;
import static com.hartwig.hmftools.linx.analysis.SvClassification.setClusterResolvedState;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.refGenomeChromosome;
import static com.hartwig.hmftools.linx.types.SvCluster.isSpecificCluster;
import static com.hartwig.hmftools.linx.types.SvConstants.MIN_DEL_LENGTH;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.region.GenomeRegion;
import com.hartwig.hmftools.common.region.GenomeRegions;
import com.hartwig.hmftools.linx.types.SvBreakend;
import com.hartwig.hmftools.linx.types.SvCluster;
import com.hartwig.hmftools.linx.types.SvLinkedPair;
import com.hartwig.hmftools.linx.types.SvVarData;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LineElementAnnotator {

    private final List<GenomeRegion> mKnownLineElements;
    private PseudoGeneFinder mPseudoGeneFinder;

    public static String KNOWN_LINE_ELEMENT = "Known";
    public static String NO_LINE_ELEMENT = "None";
    public static String SUSPECTED_LINE_ELEMENT = "Suspect";

    private static int PERMITTED_DISTANCE = 5000;

    public static String POLY_A_MOTIF = "AAAAAAAAAAA";
    public static String POLY_T_MOTIF = "TTTTTTTTTTT";

    private static final int LE_COL_CHR = 0;
    private static final int LE_COL_POS_START = 1;
    private static final int LE_COL_POS_END = 2;

    private static final Logger LOGGER = LogManager.getLogger(LineElementAnnotator.class);

    public LineElementAnnotator()
    {
        mPseudoGeneFinder = null;
        mKnownLineElements = Lists.newArrayList();
    }

    public void setPseudoGeneFinder(final PseudoGeneFinder pseudoGeneFinder)
    {
        mPseudoGeneFinder = pseudoGeneFinder;
    }

    public void loadLineElementsFile(final String filename)
    {
        if(filename.isEmpty())
            return;

        try {

            BufferedReader fileReader = new BufferedReader(new FileReader(filename));

            String line;
            while ((line = fileReader.readLine()) != null) {

                if(line.contains("Chromosome"))
                    continue;

                // parse CSV data
                String[] items = line.split(",");

                if(items.length < LE_COL_POS_END+1)
                    continue;

                final GenomeRegion genomeRegion = GenomeRegions.create(
                        refGenomeChromosome(items[LE_COL_CHR]),
                        Long.parseLong(items[LE_COL_POS_START]),
                        Long.parseLong(items[LE_COL_POS_END]));

                mKnownLineElements.add(genomeRegion);

//                LOGGER.debug("loaded line element: chr({}) pos({}-{})",
//                        genomeRegion.chromosome(), genomeRegion.start(), genomeRegion.end());
            }

            LOGGER.info("loaded {} known line elements from file: {}", mKnownLineElements.size(), filename);
        }
        catch(IOException exception)
        {
            LOGGER.error("Failed to read line element CSV file({})", filename);
        }
    }

    public String isLineElement(final SvVarData svData, final boolean useStart)
    {
        if(mKnownLineElements.isEmpty())
            return NO_LINE_ELEMENT;

        for(final GenomeRegion genomeRegion : mKnownLineElements)
        {
            if(!genomeRegion.chromosome().equals(svData.chromosome(useStart)))
                continue;

            // test if the SV falls within the LE +/- a buffer
            if(svData.position(useStart) >= genomeRegion.start() - PERMITTED_DISTANCE
            && svData.position(useStart) <= genomeRegion.end() + PERMITTED_DISTANCE)
            {
                LOGGER.debug("var({}) found in known line element({} -> {})",
                        svData.posId(), genomeRegion.chromosome(), genomeRegion.start(), genomeRegion.end());
                return KNOWN_LINE_ELEMENT;
            }
        }

        return NO_LINE_ELEMENT;
    }

    public static boolean hasPolyAorTMotif(final SvVarData var)
    {
        return var.getSvData().insertSequence().contains(POLY_A_MOTIF) || var.getSvData().insertSequence().contains(POLY_T_MOTIF);
    }

    public void markLineCluster(final SvCluster cluster, int proximityLength)
    {
        /* Identify a suspected LINE element if:
           - has 2+ BND within 5kb NOT forming a short DB bases on the LINE arm
                AND at least one SV also within 5kb having poly A/T INS sequence
                AND either the 2 BNDs going to different chromosomes or forming a short DB on their non-line (remote) arm
           - OR at least 1 BND with a remote SGL forming a 30 base DB (ie on the remote arm)
                AND EITHER at least one SV also within 5kb OR the remote SGL having poly A/T INS sequence
           - OR there are 2+ breakends within 5kb which both have a polyA insertion sequence

           Resolve the cluster as type = Line if:
            -  has a suspected line element
            -  every variant in the cluster is part of a KNOWN line element
        */

        if(isFilteredResolvedType(cluster.getResolvedType()))
            return;

        boolean hasSuspected = false;
        boolean hasPolyAorT = false;
        long knownCount = cluster.getSVs().stream().filter(SvVarData::inLineElement).count();

        final Map<String, List<SvBreakend>> chrBreakendMap = cluster.getChrBreakendMap();

        for (Map.Entry<String, List<SvBreakend>> entry : chrBreakendMap.entrySet())
        {
            final List<SvBreakend> breakendList = entry.getValue();

            for (int i = 0; i < breakendList.size(); ++i)
            {
                final SvBreakend breakend = breakendList.get(i);
                final SvVarData var = breakend.getSV();

                // skip if already marked when handled on its other chromosome (ie for a BND)
                if (var.getLineElement(breakend.usesStart()).contains(SUSPECTED_LINE_ELEMENT))
                    continue;

                List<SvVarData> polyAtSVs = Lists.newArrayList();

                if(hasPolyAorTMotif(var))
                    polyAtSVs.add(var);

                List<String> uniqueBndChromosomes = Lists.newArrayList();
                boolean hasRemoteShortBndDB = false;
                List<SvVarData> linkingBnds = Lists.newArrayList();

                boolean isSuspectGroup = false;

                if (var.type() == BND)
                {
                    uniqueBndChromosomes.add(breakend.chromosome());
                    uniqueBndChromosomes.add(var.chromosome(!breakend.usesStart()));

                    linkingBnds.add(var);

                    // test for a remote SGL in a DB
                    final SvLinkedPair dbPair = var.getDBLink(!breakend.usesStart());

                    if (dbPair != null && dbPair.length() <= MIN_DEL_LENGTH && dbPair.getOtherSV(var).type() == SGL
                    && dbPair.getOtherSV(var).getCluster() == cluster)
                    {
                        hasRemoteShortBndDB = true;
                        final SvVarData sgl = dbPair.getOtherSV(var);
                        uniqueBndChromosomes.add(sgl.chromosome(true));
                        linkingBnds.add(sgl);

                        if (hasPolyAorTMotif(sgl) && !polyAtSVs.contains(sgl))
                        {
                            polyAtSVs.add(sgl);
                        }
                    }
                }

                if(hasRemoteShortBndDB && !polyAtSVs.isEmpty())
                {
                    isSuspectGroup = true;
                }
                else
                {
                    // search for proximate BNDs and/or SVs with poly A/T
                    for (int j = i + 1; j < breakendList.size(); ++j)
                    {
                        final SvBreakend prevBreakend = breakendList.get(j - 1);
                        final SvBreakend nextBreakend = breakendList.get(j);

                        if (nextBreakend.position() - breakend.position() > proximityLength)
                            break;

                        final SvVarData nextSV = nextBreakend.getSV();

                        if(hasPolyAorTMotif(nextSV))
                        {
                            if(!polyAtSVs.contains(nextSV))
                                polyAtSVs.add(nextSV);
                        }

                        if(polyAtSVs.size() >= 2)
                        {
                            isSuspectGroup = true;
                            break;
                        }

                        if (nextSV.type() == BND)
                        {
                            final SvLinkedPair dbPair = nextSV.getDBLink(nextBreakend.usesStart());
                            final SvLinkedPair nextDbPair = prevBreakend.getSV().getDBLink(prevBreakend.usesStart());

                            if (dbPair == null || dbPair != nextDbPair || dbPair.length() > MIN_DEL_LENGTH)
                            {
                                // now check the other chromosome for this BND or whether it forms a short DB
                                final String otherChr = nextSV.chromosome(!nextBreakend.usesStart());

                                if(!uniqueBndChromosomes.contains(otherChr))
                                {
                                    linkingBnds.add(nextSV);
                                    uniqueBndChromosomes.add(otherChr);
                                }
                                else
                                {
                                    final SvLinkedPair remoteDbPair = nextSV.getDBLink(!nextBreakend.usesStart());

                                    if(remoteDbPair != null && remoteDbPair.length() <= MIN_DEL_LENGTH
                                    && remoteDbPair.getOtherSV(nextSV) == prevBreakend.getSV())
                                    {
                                        linkingBnds.add(nextSV);
                                        hasRemoteShortBndDB = true;
                                    }
                                }
                            }
                        }

                        if ((uniqueBndChromosomes.size() >= 3 || hasRemoteShortBndDB) && !polyAtSVs.isEmpty())
                        {
                            isSuspectGroup = true;
                            break;
                        }
                    }
                }

                if(!polyAtSVs.isEmpty())
                    hasPolyAorT = true;

                if (!isSuspectGroup)
                    continue;

                // gather up all breakends within 5K of this suspect line element
                List<SvBreakend> proximateBreakends = Lists.newArrayList(breakend);

                for (int j = i + 1; j < breakendList.size(); ++j)
                {
                    final SvBreakend nextBreakend = breakendList.get(j);

                    if (abs(nextBreakend.position() - breakend.position()) > proximityLength)
                        break;

                    proximateBreakends.add(nextBreakend);
                }

                // and in the reverse direction
                for (int j = i - 1; j >= 0; --j)
                {
                    final SvBreakend prevBreakend = breakendList.get(j);

                    if (abs(breakend.position() - prevBreakend.position()) > proximityLength)
                        break;

                    proximateBreakends.add(prevBreakend);
                }

                // check for a proximate DEL matching exon positions which would invalidate this as a LINE cluster
                if(mPseudoGeneFinder != null)
                {
                    final SvBreakend pseduoDel = proximateBreakends.stream()
                            .filter(x -> x.getSV().type() == DEL)
                            .filter(x -> mPseudoGeneFinder.variantMatchesPseudogeneExons(x.getSV()))
                            .findFirst().orElse(null);

                    if(pseduoDel != null)
                    {
                        LOGGER.debug("cluster({}) proximate DEL({}) matches pseudogene exon boundaries",
                                cluster.id(), pseduoDel.getSV().posId());
                        continue;
                    }
                }

                final String linkingIdsStr = linkingBnds.stream().map(x -> x.id()).collect(Collectors.toList()).toString();

                LOGGER.debug("cluster({}) lineChr({}) uniqueChr({}) linkingSVs({}) remoteShortDB({}) proxPolyATCount({})",
                        cluster.id(), breakend.chromosome(), uniqueBndChromosomes.toString(), linkingIdsStr,
                        hasRemoteShortBndDB, polyAtSVs.size());

                hasSuspected = true;

                // otherwise mark every breakend in this proximity as suspect line
                proximateBreakends.forEach(x -> x.getSV().setLineElement(SUSPECTED_LINE_ELEMENT, x.usesStart()));
            }
        }

        if(cluster.getSvCount() == knownCount && cluster.getTypeCount(BND) >= 1)
        {
            LOGGER.debug("cluster({}) marked as line with all known({})", cluster.id(), knownCount);
            cluster.markAsLine();
            return;
        }
        else if(hasSuspected)
        {
            long svInLineCount = cluster.getSVs().stream().filter(x-> x.inLineElement()).count();

            if(LOGGER.isDebugEnabled())
            {
                long polyAorT = cluster.getSVs().stream().filter(x -> hasPolyAorTMotif(x)).count();
                long suspectLine = cluster.getSVs().stream()
                        .filter(x -> (x.getLineElement(true).contains(SUSPECTED_LINE_ELEMENT)
                        || x.getLineElement(false).contains(SUSPECTED_LINE_ELEMENT))).count();

                LOGGER.debug("cluster({}) anyLine({}) suspect({}) known({}) polyAT({})",
                        cluster.id(), svInLineCount, suspectLine, knownCount, polyAorT);
            }

            if(cluster.getSvCount() <= 10 || svInLineCount * 2 >= cluster.getSvCount())
            {
                cluster.markAsLine();
            }
        }
        else if(hasPolyAorT)
        {
            int sglCount = cluster.getSglBreakendCount();

            if(sglCount >= 1 && sglCount <= 2 && sglCount == cluster.getSvCount())
            {
                LOGGER.debug("cluster({}) marked as line with poly A/T SGL", cluster.id());
                cluster.markAsLine();
            }
            else if(cluster.getTypeCount(INS) == 1 && cluster.getSvCount() == 1)
            {
                cluster.markAsLine();
            }
        }
    }


}
