package com.hartwig.hmftools.svanalysis.analysis;

import static java.lang.Math.abs;
import static java.lang.Math.max;

import static com.hartwig.hmftools.svanalysis.analysis.SvClusteringMethods.DEFAULT_PROXIMITY_DISTANCE;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.PERMITED_DUP_BE_DISTANCE;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.breakendsMatch;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.copyNumbersEqual;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.getProximity;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_RECIPROCAL_TRANS;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.SVI_END;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.SVI_START;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.haveLinkedAssemblies;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.isStart;
import static com.hartwig.hmftools.svanalysis.types.SvLinkedPair.ASSEMBLY_MATCH_DIFF;
import static com.hartwig.hmftools.svanalysis.types.SvLinkedPair.ASSEMBLY_MATCH_MATCHED;
import static com.hartwig.hmftools.svanalysis.types.SvLinkedPair.LINK_TYPE_DB;
import static com.hartwig.hmftools.svanalysis.types.SvLinkedPair.LINK_TYPE_TI;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantType;
import com.hartwig.hmftools.svanalysis.types.SvBreakend;
import com.hartwig.hmftools.svanalysis.types.SvChain;
import com.hartwig.hmftools.svanalysis.types.SvCluster;
import com.hartwig.hmftools.svanalysis.types.SvVarData;
import com.hartwig.hmftools.svanalysis.types.SvLinkedPair;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LinkFinder
{
    private boolean mLogVerbose;

    public static int MIN_TEMPLATED_INSERTION_LENGTH = 30;
    private static int MAX_TEMPLATED_INSERTION_LENGTH = 500;
    public static int CLUSTER_SIZE_ANALYSIS_LIMIT = 500;
    public static int SHORT_DB_LENGTH = 30;

    public static String TRANS_TYPE_TRANS = "TRANS";
    public static String TRANS_TYPE_SPAN = "SPAN";

    private static final Logger LOGGER = LogManager.getLogger(LinkFinder.class);

    public LinkFinder()
    {
    }

    public void setLogVerbose(boolean toggle) { mLogVerbose = toggle; }

    public void findLinkedPairs(final String sampleId, SvCluster cluster)
    {
        List<SvLinkedPair> assemblyLinkedPairs = createAssemblyLinkedPairs(cluster);
        cluster.setAssemblyLinkedPairs(assemblyLinkedPairs);

        if(cluster.hasLinkingLineElements())
            return;

        List<SvLinkedPair> inferredLinkedPairs = createInferredLinkedPairs(cluster, false);

        // findSpanningSVs(sampleId, cluster, inferredLinkedPairs);

        List<SvLinkedPair> singleBELinkedPairs = createSingleBELinkedPairs(cluster);
        inferredLinkedPairs.addAll(singleBELinkedPairs);

        cluster.setInferredLinkedPairs(inferredLinkedPairs);
    }

    public List<SvLinkedPair> createAssemblyLinkedPairs(SvCluster cluster)
    {
        List<SvLinkedPair> linkedPairs = Lists.newArrayList();

        // find 2 breakends with matching assembly info and form them into a linked pair
        // if have have multiple assembly info and it doesn't match, don't link them
        if(cluster.getCount() < 2)
            return linkedPairs;

        for (int i = 0; i < cluster.getCount(); ++i) {

            SvVarData var1 = cluster.getSVs().get(i);

            if(var1.type() == StructuralVariantType.INS || var1.isNullBreakend())
                continue;

            // make note of SVs which line up exactly with other SVs
            // these will be used to eliminate transitive SVs later on
            if(var1.isDupBreakend(true) && var1.isDupBreakend(false))
            {
                continue;
            }

            for(int be1 = SVI_START; be1 <= SVI_END; ++be1)
            {
                boolean v1Start = isStart(be1);

                for (int j = i+1; j < cluster.getCount(); ++j)
                {
                    SvVarData var2 = cluster.getSVs().get(j);

                    if(var1.equals(var2, true))
                        continue;

                    if(var2.type() == StructuralVariantType.INS || var2.isNullBreakend())
                        continue;

                    if(var2.isDupBreakend(true) && var2.isDupBreakend(false))
                        continue;

                    for(int be2 = SVI_START; be2 <= SVI_END; ++be2)
                    {
                        boolean v2Start = isStart(be2);

                        if (!haveLinkedAssemblies(var1, var2, v1Start, v2Start))
                            continue;

                        // check wasn't already created
                        boolean v1Linked = var1.isAssemblyMatched(v1Start);
                        boolean v2Linked = var2.isAssemblyMatched(v2Start);
                        if(v1Linked || v2Linked)
                        {
                            if (v1Linked && v2Linked)
                            {
                                // both linked but to other variants
                            }
                            else if (v1Linked)
                            {
                                var2.setAssemblyMatchType(ASSEMBLY_MATCH_DIFF, v2Start);
                            }
                            else if (v2Linked)
                            {
                                var1.setAssemblyMatchType(ASSEMBLY_MATCH_DIFF, v1Start);
                            }

                            continue;
                        }

                        // form a new TI from these 2 BEs
                        SvLinkedPair newPair = new SvLinkedPair(var1, var2, LINK_TYPE_TI, v1Start, v2Start);
                        newPair.setIsInferred(false);
                        var1.setAssemblyMatchType(ASSEMBLY_MATCH_MATCHED, v1Start);
                        var2.setAssemblyMatchType(ASSEMBLY_MATCH_MATCHED, v2Start);
                        var1.setLinkedPair(newPair, v1Start);
                        var2.setLinkedPair(newPair, v1Start);

                        linkedPairs.add(newPair);

                        // to avoid logging unlikely long TIs
                        LOGGER.debug("cluster({}) adding assembly linked {} pair({}) length({})",
                                cluster.getId(), newPair.linkType(), newPair.toString(), newPair.length());
                    }
                }
            }
        }

        return linkedPairs;
    }
    public static boolean areLinkedSection(final SvVarData v1, final SvVarData v2, boolean v1Start, boolean v2Start)
    {
        return areLinkedSection(v1, v2, v1Start, v2Start, false);
    }

    public static boolean areLinkedSection(final SvVarData v1, final SvVarData v2, boolean v1Start, boolean v2Start, boolean checkCopyNumberMatch)
    {
        // templated insertions are allowed to traverse the centromere
        if(v1.position(v1Start) < 0 || v2.position(v1Start) < 0)
            return false;

        if(!v1.chromosome(v1Start).equals(v2.chromosome(v2Start)))
            return false;

        // start apart and heading towards each other
        long pos1 = v1.position(v1Start);
        boolean headsLeft1 = (v1.orientation(v1Start) == 1);
        long pos2 = v2.position(v2Start);
        boolean headsLeft2 = (v2.orientation(v2Start) == 1);

        boolean breakendsFace = false;
        if(pos1 < pos2 && !headsLeft1 && headsLeft2)
            breakendsFace = true;
        else if(pos2 < pos1 && headsLeft1 && !headsLeft2)
            breakendsFace = true;

        if(!breakendsFace)
            return false;

        if(checkCopyNumberMatch)
        {
            boolean skipReplicated = v1.isReplicatedSv() || v1.getReplicatedCount() > 0 || v2.isReplicatedSv() || v2.getReplicatedCount() > 0;

            if(!skipReplicated)
            {
                double cn1 = v1.copyNumberChange(v1Start);
                double cn2 = v2.copyNumberChange(v2Start);

                if(!copyNumbersEqual(cn1, cn2))
                    return false;
            }
        }

        return true;
    }

    public static boolean areSectionBreak(final SvVarData v1, final SvVarData v2, boolean v1Start, boolean v2Start)
    {
        if(!v1.chromosome(v1Start).equals(v2.chromosome(v2Start)))
            return false;

        // start apart or equal and heading same direction
        long pos1 = v1.position(v1Start);
        boolean headsLeft1 = (v1.orientation(v1Start) == 1);
        long pos2 = v2.position(v2Start);
        boolean headsLeft2 = (v2.orientation(v2Start) == 1);

        if(pos1 <= pos2 && headsLeft1 && !headsLeft2)
            return true;

        if(pos2 <= pos1 && !headsLeft1 && headsLeft2)
            return true;

        return false;
    }


    public List<SvLinkedPair> createInferredLinkedPairs(SvCluster cluster, boolean allowSingleBEs)
    {
        return createInferredLinkedPairs(cluster, cluster.getSVs(), allowSingleBEs);
    }

    public List<SvLinkedPair> createInferredLinkedPairs(SvCluster cluster, List<SvVarData> svList, boolean allowSingleBEs)
    {
        List<SvLinkedPair> linkedPairs = Lists.newArrayList();

        // exclude large clusters for now due to processing times until the algo is better refined
        if(svList.size() >= CLUSTER_SIZE_ANALYSIS_LIMIT)
            return linkedPairs;

        if(cluster.hasLinkingLineElements())
            return linkedPairs;

        for (int i = 0; i < svList.size(); ++i)
        {
            SvVarData var1 = svList.get(i);

            if(var1.type() == StructuralVariantType.INS || (var1.isNullBreakend() && !allowSingleBEs))
                continue;

            if(var1.isDupBreakend(true) && var1.isDupBreakend(false))
                continue;

            for(int be1 = SVI_START; be1 <= SVI_END; ++be1)
            {
                boolean v1Start = isStart(be1);

                if(var1.isNullBreakend() && !v1Start)
                    continue;

                // if an assembly linked pair has already been created for this breakend, look no further
                if(var1.isAssemblyMatched(v1Start))
                    continue;

                for (int j = i+1; j < svList.size(); ++j)
                {
                    SvVarData var2 = svList.get(j);

                    if(var1.equals(var2, true))
                        continue;

                    if(var2.type() == StructuralVariantType.INS || (var2.isNullBreakend() && !allowSingleBEs))
                        continue;

                    if(var2.isDupBreakend(true) && var2.isDupBreakend(false))
                        continue;

                    for(int be2 = SVI_START; be2 <= SVI_END; ++be2)
                    {
                        boolean v2Start = isStart(be2);

                        if(var1.isNullBreakend() && !v1Start)
                            continue;

                        if(var2.isAssemblyMatched(v2Start))
                            continue;

                        if (!areLinkedSection(var1, var2, v1Start, v2Start))
                            continue;

                            // form a new TI from these 2 BEs
                        SvLinkedPair newPair = new SvLinkedPair(var1, var2, LINK_TYPE_TI, v1Start, v2Start);

                        if(newPair.linkType() == LINK_TYPE_DB)
                        {
                            // was considered too short to be a TI and so converted
                            var1.setDBLink(newPair, v1Start);
                            var2.setDBLink(newPair, v2Start);
                            continue;
                        }

                        // insert in order of increasing length
                        int index = 0;
                        boolean skipNewPair = false;
                        int linkClashCount = 0;

                        for (; index < linkedPairs.size(); ++index)
                        {
                            final SvLinkedPair pair = linkedPairs.get(index);

                            // check for matching BEs on a pair that is much shorter, and if so skip creating this new linked pair
                            if(newPair.length() > pair.length() && newPair.hasLinkClash(pair))
                            {
                                // allow a TI if only a DB has been found
                                skipNewPair = true;
                                break;
                            }

                            if (pair.length() > newPair.length())
                                break;
                        }

                        if(skipNewPair)
                            continue;

                        if (index >= linkedPairs.size())
                            linkedPairs.add(newPair);
                        else
                            linkedPairs.add(index, newPair);

                        // if(newPair.length() < mUtils.getBaseDistance())
                        if(mLogVerbose && !var1.isReplicatedSv() && !var2.isReplicatedSv())
                        {
                            // to avoid logging unlikely long TIs
                            LOGGER.debug("cluster({}) adding inferred linked {} pair({}) length({}) at index({})",
                                    cluster.getId(), newPair.linkType(), newPair.toString(), newPair.length(), index);
                        }
                    }
                }
            }
        }

        // prior to consolidating linked pairs, check for duplicate BE in the spanning SVs
        // matchDuplicateBEToLinkedPairs(linkedPairs, spanningSVs);

        if(linkedPairs.isEmpty())
            return linkedPairs;

        LOGGER.debug("cluster({}) has {} inferred linked pairs", cluster.getId(), linkedPairs.size());

        return linkedPairs;
    }

    public static void findDeletionBridges(final Map<String, List<SvBreakend>> chrBreakendMap)
    {
        for (final Map.Entry<String, List<SvBreakend>> entry : chrBreakendMap.entrySet())
        {
            final List<SvBreakend> breakendList = entry.getValue();

            for (int i = 0; i < breakendList.size() - 1; ++i)
            {
                final SvBreakend breakend = breakendList.get(i);
                final SvBreakend nextBreakend = breakendList.get(i+1);
                SvVarData var1 = breakend.getSV();
                SvVarData var2 = nextBreakend.getSV();

                if(var1 == var2)
                    continue;

                if(areSectionBreak(var1, var2, breakend.usesStart(), nextBreakend.usesStart()))
                {
                    SvLinkedPair dbPair = new SvLinkedPair(var1, var2, LINK_TYPE_DB, breakend.usesStart(), nextBreakend.usesStart());
                    var1.setDBLink(dbPair, breakend.usesStart());
                    var2.setDBLink(dbPair, nextBreakend.usesStart());

                    // can then skip the next breakend
                    ++i;
                }
                else if(areLinkedSection(var1, var2, breakend.usesStart(), nextBreakend.usesStart())
                && nextBreakend.position() - breakend.position() <= MIN_TEMPLATED_INSERTION_LENGTH)
                {
                    // will be converted into a DB
                    SvLinkedPair dbPair = new SvLinkedPair(var1, var2, LINK_TYPE_TI, breakend.usesStart(), nextBreakend.usesStart());
                    var1.setDBLink(dbPair, breakend.usesStart());
                    var2.setDBLink(dbPair, nextBreakend.usesStart());

                    // can then skip the next breakend
                    ++i;
                }
            }
        }
    }

    public static boolean inDeletionBridge(final SvVarData var, final SvVarData other, boolean useStart)
    {
        if(var.getDBLink(useStart) == null)
            return false;

        return (var.getDBLink(useStart).first() == other || var.getDBLink(useStart).second() == other);
    }

    public static boolean arePairedDeletionBridges(final SvVarData var1, final SvVarData var2)
    {
        if(var1.getDBLink(true) == null || var1.getDBLink(false) == null
        || var2.getDBLink(true) == null || var2.getDBLink(false) == null)
        {
            return false;
        }

        if(var1.getDBLink(true) == var2.getDBLink(true) && var1.getDBLink(false) == var2.getDBLink(false))
            return true;
        else if(var1.getDBLink(true) == var2.getDBLink(false) && var1.getDBLink(false) == var2.getDBLink(true))
            return true;
        else
            return false;
    }

    private void findSpanningSVs(final String sampleId, SvCluster cluster, final List<SvLinkedPair> linkedPairs)
    {
        if(cluster.getCount() >= CLUSTER_SIZE_ANALYSIS_LIMIT)
            return;

        if(cluster.hasLinkingLineElements())
            return;

        List<SvVarData> spanningSVs = Lists.newArrayList();

        for (int i = 0; i < cluster.getCount(); ++i)
        {
            SvVarData var1 = cluster.getSVs().get(i);

            if (var1.type() == StructuralVariantType.INS || var1.isNullBreakend())
                continue;

            // make note of SVs which line up exactly with other SVs
            // these will be used to eliminate transitive SVs later on
            if (var1.isDupBreakend(true) && var1.isDupBreakend(false))
            {
                spanningSVs.add(var1);
            }
        }

        if(spanningSVs.isEmpty())
            return;

        // prior to consolidating linked pairs, check for duplicate BE in the spanning SVs
        matchDuplicateBEToLinkedPairs(linkedPairs, spanningSVs);

        LOGGER.debug("sample({}) cluster({}) has {} possible spanning SVs", sampleId, cluster.getId(), spanningSVs.size());

        cluster.setSpanningSVs(spanningSVs);
    }

    public List<SvLinkedPair> createSingleBELinkedPairs(SvCluster cluster)
    {
        List<SvLinkedPair> linkedPairs = Lists.newArrayList();

        for (int i = 0; i < cluster.getCount(); ++i)
        {
            SvVarData var1 = cluster.getSVs().get(i);

            if(!var1.isNullBreakend() || var1.isNoneSegment())
                continue;

            for (int j = i+1; j < cluster.getCount(); ++j)
            {
                SvVarData var2 = cluster.getSVs().get(j);

                if(var1.equals(var2, true))
                    continue;

                if(!var2.isNullBreakend() || var2.isNoneSegment())
                    continue;

                if (!areSectionBreak(var1, var2, true, true) && !areLinkedSection(var1, var2, true, true))
                {
                    continue;
                }

                // allow if the length is within the DB-TI cutoff
                long length = getProximity(var1, var2, true, true);

                if(length > MIN_TEMPLATED_INSERTION_LENGTH)
                    continue;

                // form a new from these 2 single breakends
                SvLinkedPair newPair = new SvLinkedPair(var1, var2, SvLinkedPair.LINK_TYPE_SGL, false, false);

                // insert in order
                int index = 0;
                boolean skipNewPair = false;
                for (; index < linkedPairs.size(); ++index)
                {
                    SvLinkedPair pair = linkedPairs.get(index);

                    // check for a matching BE on a pair that is shorter, and if so skip creating this new linked pair
                    if(pair.hasAnySameVariant(newPair) && newPair.length() > pair.length())
                    {
                        skipNewPair = true;
                        break;
                    }

                    if (pair.length() > newPair.length())
                        break;
                }

                if(skipNewPair)
                    continue;

                if (index >= linkedPairs.size())
                    linkedPairs.add(newPair);
                else
                    linkedPairs.add(index, newPair);

                if(newPair.length() < DEFAULT_PROXIMITY_DISTANCE)
                {
                    // to avoid logging unlikely long TIs
                    LOGGER.debug("cluster({}) adding inferred single-BE linked {} pair({}) length({}) at index({})",
                            cluster.getId(), newPair.linkType(), newPair.toString(), newPair.length(), index);
                }
            }
        }

        if(!linkedPairs.isEmpty())
        {
            LOGGER.debug("cluster({}) has {} inferred single-BE linked pairs",
                    cluster.getId(), linkedPairs.size());
        }

        return linkedPairs;
    }

    private void matchDuplicateBEToLinkedPairs(final List<SvLinkedPair> linkedPairs, final List<SvVarData> spanningSVs)
    {
        // link spanning SVs with any single linked pairs
        for(SvVarData spanningSV : spanningSVs)
        {
            SvVarData startLink = null;
            SvVarData endLink = null;

            for(SvLinkedPair pair : linkedPairs) {

                if (pair.length() > MAX_TEMPLATED_INSERTION_LENGTH) {
                    continue;
                }

                if(breakendsMatch(spanningSV, pair.first(), true, !pair.firstLinkOnStart(), PERMITED_DUP_BE_DISTANCE)) {
                    startLink = pair.first();
                }
                else if(breakendsMatch(spanningSV, pair.second(), true, !pair.secondLinkOnStart(), PERMITED_DUP_BE_DISTANCE)) {
                    startLink = pair.second();
                }
                else
                {
                    continue;
                }

                if(breakendsMatch(spanningSV, pair.first(), false, !pair.firstLinkOnStart(), PERMITED_DUP_BE_DISTANCE)) {
                    endLink = pair.first();
                }
                else if(breakendsMatch(spanningSV, pair.second(), false, !pair.secondLinkOnStart(), PERMITED_DUP_BE_DISTANCE))
                {
                    endLink = pair.second();
                }
                else
                {
                    continue;
                }

                // match found on both ends
                LOGGER.debug("spanSV({}) linked to linked pair({} and {})",
                        spanningSV.posId(), startLink.posId(), endLink.posId());

                startLink.setTransData(TRANS_TYPE_TRANS, pair.length(), spanningSV.id());
                endLink.setTransData(TRANS_TYPE_TRANS, pair.length(), spanningSV.id());

                String svLinkData = startLink.id() + "_" + endLink.id();
                spanningSV.setTransData(TRANS_TYPE_SPAN, pair.length(), svLinkData);

                break;
            }
        }
    }

    public void resolveTransitiveSVs(final String sampleId, SvCluster cluster)
    {
        if (cluster.getLinkedPairs().isEmpty() || cluster.getSpanningSVs().isEmpty())
            return;

        // attempt to matching spanning SVs to the ends of one or more linked pairs
        // these can only span short TIs (ie not DBs or long TIs)
        for(SvVarData spanningSV : cluster.getSpanningSVs())
        {
            boolean startMatched = false;
            boolean endMatched = false;
            SvVarData startLink = null;
            SvVarData endLink = null;
            SvLinkedPair startPair = null;
            SvLinkedPair endPair = null;
            boolean startLinkOnStart = false;
            boolean endLinkOnStart = false;

            List<SvVarData> transitiveSVs = Lists.newArrayList();

            for(SvLinkedPair pair : cluster.getLinkedPairs()) {

                if (pair.length() > MAX_TEMPLATED_INSERTION_LENGTH) {
                    continue;
                }

                if (!startMatched)
                {
                    if(breakendsMatch(spanningSV, pair.first(), true, !pair.firstLinkOnStart(), PERMITED_DUP_BE_DISTANCE)) {
                        startLink = pair.first();
                        startLinkOnStart = !pair.firstLinkOnStart();
                    }
                    else if(breakendsMatch(spanningSV, pair.second(), true, !pair.secondLinkOnStart(), PERMITED_DUP_BE_DISTANCE)) {
                        startLink = pair.second();
                        startLinkOnStart = !pair.secondLinkOnStart();
                    }
                    else
                    {
                        continue;
                    }

                    startMatched = true;
                    startPair = pair;
                }

                if (!endMatched)
                {
                    if(breakendsMatch(spanningSV, pair.first(), false, !pair.firstLinkOnStart(), PERMITED_DUP_BE_DISTANCE)) {
                        endLink = pair.first();
                        endLinkOnStart = !pair.firstLinkOnStart();
                    }
                    else if(breakendsMatch(spanningSV, pair.second(), false, !pair.secondLinkOnStart(), PERMITED_DUP_BE_DISTANCE))
                    {
                        endLink = pair.second();
                        endLinkOnStart = !pair.secondLinkOnStart();
                    }
                    else
                    {
                        continue;
                    }

                    endPair = pair;
                    endMatched = true;
                }


                if(startMatched && endMatched)
                    break;
            }

            if(startMatched && endMatched)
            {
                boolean samePair = (startPair == endPair);
                int tiLength = 0;
                boolean hasValidTransData = true;

                LOGGER.debug("cluster({}) spanSV({}) linked to transitives({} and {}) from {} linked pair",
                        cluster.getId(), spanningSV.posId(), startLink.posId(), endLink.posId(),
                        samePair ? "same" : "diff");

                if(samePair)
                {
                    tiLength = startPair.length();
                    transitiveSVs.add(startLink);
                    transitiveSVs.add(endLink);
                }
                else {

                    // now additionally check if these SVs are part of a chain, and if so whether any intermediary transitive SVs
                    // are also covered by this span
                    SvChain startChain = cluster.findChain(startPair);
                    SvChain endChain = cluster.findChain(endPair);

                    if (startChain != null && endChain == startChain) {

                        // now walk the chain and collect up all transitive SVs
                        int startIndex = startChain.getSvIndex(startLink, startLinkOnStart);
                        int endIndex = startChain.getSvIndex(endLink, endLinkOnStart);

                        if(startIndex == -1 || endIndex == -1)
                        {
                            LOGGER.error("cluster({}) chain({}) index not found({} - {}) links({} & {})",
                                    cluster.getId(), startChain.getId(), startIndex, endIndex, startLink, endLink);
                            return;
                        }

                        int totalTILength = 0;

                        if (startIndex != endIndex) {

                            int startI = startIndex <= endIndex ? startIndex : endIndex;
                            int endI = startIndex > endIndex ? startIndex : endIndex;

                            for(int i = startI; i <= endI; ++i)
                            {
                                final SvLinkedPair pair = startChain.getLinkedPairs().get(i);

                                if(transitiveSVs.contains(pair.first()) || transitiveSVs.contains(pair.second()))
                                {
                                    LOGGER.debug("cluster({}) chain({}) attempt to re-add trans SVs, invalid",
                                            cluster.getId(), startChain.getId());

                                    transitiveSVs.clear();

                                    // manually add the link SVs
                                    totalTILength = 0;
                                    transitiveSVs.add(startLink);
                                    transitiveSVs.add(endLink);
                                    break;
                                }

                                totalTILength += pair.length();

                                if(totalTILength > MAX_TEMPLATED_INSERTION_LENGTH * 3)
                                {
                                    LOGGER.debug("cluster({}) chain({}) exceed valid totalLen({}) at index({}), invalid",
                                            cluster.getId(), startChain.getId(), totalTILength, i);

                                    hasValidTransData = false;
                                    break;
                                }

                                LOGGER.debug("cluster({}) chain({}) including index({}) totalLen({}) linkPair({}))",
                                        cluster.getId(), startChain.getId(), i, totalTILength, pair.toString());

                                transitiveSVs.add(pair.first());
                                transitiveSVs.add(pair.second());
                            }

                            if(hasValidTransData)
                            {
                                LOGGER.debug("cluster({}) spanSV({}) covers {} linked pairs",
                                        cluster.getId(), spanningSV.id(), transitiveSVs.size()/2);

                                tiLength = totalTILength;
                            }
                        }
                        else
                        {
                            LOGGER.warn("cluster({}) chain({}) linked pairs have same index({}) but diff linked pair",
                                    cluster.getId(), startChain.getId(), startIndex);
                        }
                    }
                    else if (startChain == null || endChain == null) {

                        // ignore any intermediary linked SVs from the single chain for now
                        tiLength = startPair.length() + endPair.length();

                        if(tiLength < MAX_TEMPLATED_INSERTION_LENGTH * 3) {

                            transitiveSVs.add(startLink);
                            transitiveSVs.add(endLink);
                        }
                        else
                        {
                            hasValidTransData = false;
                        }
                    }
                    else
                    {
                        hasValidTransData = false;
                        LOGGER.debug("cluster({}) linked pairs have diff chains({} and {})",
                                cluster.getId(), startChain.getId(), endChain.getId());
                    }
                }

                if(hasValidTransData) {

                    // mark all transitive SVs
                    for (SvVarData transSv : transitiveSVs) {
                        String svLinkData = spanningSV.id();
                        transSv.setTransData(TRANS_TYPE_TRANS, tiLength, svLinkData);
                    }

                    // and mark the spanning SV
                    String svLinkData = startLink.id() + "_" + endLink.id();
                    spanningSV.setTransData(TRANS_TYPE_SPAN, tiLength, svLinkData);
                }
            }
        }
    }

}
