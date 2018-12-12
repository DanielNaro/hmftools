package com.hartwig.hmftools.svanalysis.analysis;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.BND;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.DEL;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.DUP;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.INV;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.SGL;
import static com.hartwig.hmftools.svanalysis.analysis.LinkFinder.MIN_TEMPLATED_INSERTION_LENGTH;
import static com.hartwig.hmftools.svanalysis.analysis.LinkFinder.areLinkedSection;
import static com.hartwig.hmftools.svanalysis.analysis.LinkFinder.arePairedDeletionBridges;
import static com.hartwig.hmftools.svanalysis.analysis.LinkFinder.areSectionBreak;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.CHROMOSOME_ARM_P;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.CHROMOSOME_ARM_Q;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.PERMITED_DUP_BE_DISTANCE;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.areVariantsLinkedByDistance;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.calcConsistency;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.copyNumbersEqual;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.getArmFromChrArm;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.getChrFromChrArm;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.getChromosomalArmLength;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.getVariantChrArm;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.isOverlapping;
import static com.hartwig.hmftools.svanalysis.analysis.SvUtilities.makeChrArmStr;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_LOW_QUALITY;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_DEL_EXT_TI;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_DEL_INT_TI;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_DUP_EXT_TI;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_DUP_INT_TI;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_NONE;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_RECIPROCAL_TRANS;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_SGL_PAIR_DEL;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_SGL_PAIR_DUP;
import static com.hartwig.hmftools.svanalysis.types.SvCluster.RESOLVED_TYPE_SGL_PLUS_INCONSISTENT;
import static com.hartwig.hmftools.svanalysis.types.SvLinkedPair.LINK_TYPE_DB;
import static com.hartwig.hmftools.svanalysis.types.SvLinkedPair.LINK_TYPE_TI;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.ASSEMBLY_TYPE_EQV;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.RELATION_TYPE_NEIGHBOUR;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.RELATION_TYPE_OVERLAP;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantType;
import com.hartwig.hmftools.svanalysis.types.SvBreakend;
import com.hartwig.hmftools.svanalysis.types.SvCluster;
import com.hartwig.hmftools.svanalysis.types.SvLinkedPair;
import com.hartwig.hmftools.svanalysis.types.SvVarData;
import com.hartwig.hmftools.svanalysis.types.SvLOH;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SvClusteringMethods {

    private static final Logger LOGGER = LogManager.getLogger(SvClusteringMethods.class);

    private Map<String, Integer> mChrArmSvCount;
    private Map<String, Double> mChrArmSvExpected;
    private Map<String, Double> mChrArmSvRate;
    private double mMedianChrArmRate;
    private int mNextClusterId;

    private Map<String, List<SvBreakend>> mChrBreakendMap; // every breakend on a chromosome, ordered by asending position
    // private Map<String, List<SvCNData>> mChrCNDataMap; // copy number segments recreated from SVs
    private Map<String, List<SvLOH>> mSampleLohData;
    private Map<String, Double> mTelomereCopyNumberMap;
    private Map<String, Double> mChromosomeCopyNumberMap; // max of telomere for convenience


    private long mDelDupCutoffLength;
    private int mProximityDistance;

    private static double REF_BASE_LENGTH = 10000000D;
    public static int MAX_SIMPLE_DUP_DEL_CUTOFF = 5000000;
    public static int MIN_SIMPLE_DUP_DEL_CUTOFF = 100000;
    public static int DEFAULT_PROXIMITY_DISTANCE = 5000;

    public static String CLUSTER_REASON_PROXIMITY = "Prox";
    public static String CLUSTER_REASON_LOH = "LOH";
    public static String CLUSTER_REASON_COMMON_ARMS = "ComArm";
    public static String CLUSTER_REASON_FOLDBACKS = "Foldback";
    public static String CLUSTER_REASON_SOLO_SINGLE = "Single";
    public static String CLUSTER_REASON_INV_OVERLAP = "InvOverlap";
    public static String CLUSTER_REASON_LONG_DEL_DUP = "LongDelDup";

    public SvClusteringMethods(int proximityLength)
    {
        mChrArmSvCount = Maps.newHashMap();
        mChrArmSvExpected = Maps.newHashMap();
        mChrArmSvRate = Maps.newHashMap();
        mMedianChrArmRate = 0;
        mNextClusterId = 0;

        mDelDupCutoffLength = 0;
        mProximityDistance = proximityLength;

        mChrBreakendMap = new HashMap();
        mTelomereCopyNumberMap = new HashMap();
        mChromosomeCopyNumberMap = new HashMap();
        mSampleLohData = null;
    }

    public Map<String, List<SvBreakend>> getChrBreakendMap() { return mChrBreakendMap; }
    public Map<String, Double> getTelomereCopyNumberMap() { return mTelomereCopyNumberMap; }
    public int getNextClusterId() { return mNextClusterId++; }
    public void setSampleLohData(final Map<String, List<SvLOH>> data) { mSampleLohData = data; }
    public long getDelDupCutoffLength() { return mDelDupCutoffLength; }
    public int getProximityDistance() { return mProximityDistance; }

    public void clusterByBaseDistance(List<SvVarData> allVariants, List<SvCluster> clusters)
    {
        mNextClusterId = 0;

        List<SvVarData> unassignedVariants = Lists.newArrayList(allVariants);

        // assign each variant once to a cluster using proximity as a test
        int currentIndex = 0;

        while(currentIndex < unassignedVariants.size())
        {
            SvVarData currentVar = unassignedVariants.get(currentIndex);

            // make a new cluster
            SvCluster newCluster = new SvCluster(getNextClusterId());

            // first remove the current SV from consideration
            newCluster.addVariant(currentVar);
            unassignedVariants.remove(currentIndex); // index will remain the same and so point to the next item

            // exceptions to proximity clustering
            if(isEquivSingleBreakend(currentVar))
            {
                newCluster.setResolved(true, RESOLVED_LOW_QUALITY);
                clusters.add(newCluster);
                continue;
            }

            // and then search for all other linked ones
            findLinkedSVsByDistance(newCluster, unassignedVariants, true);

            // check for invalid clusters - currently just overlapping DELs
            if(newCluster.getCount() > 1 && newCluster.getCount() == newCluster.getTypeCount(DEL))
            {
                addDelGroupClusters(clusters, newCluster);
            }
            else
            {
                clusters.add(newCluster);
            }
        }
    }

    private void addDelGroupClusters(List<SvCluster> clusters, SvCluster delGroupCluster)
    {
        // only cluster if proximate and not overlapping
        int currentIndex = 0;

        List<SvVarData> delSVs = Lists.newArrayList();
        delSVs.addAll(delGroupCluster.getSVs());

        while(currentIndex < delSVs.size())
        {
            SvVarData currentVar = delSVs.get(currentIndex);

            SvCluster newCluster = new SvCluster(getNextClusterId());

            newCluster.addVariant(currentVar);
            delSVs.remove(currentIndex);

            // and then search for all other linked ones
            findLinkedSVsByDistance(newCluster, delSVs, false);

            clusters.add(newCluster);
        }
    }

    private void findLinkedSVsByDistance(SvCluster cluster, List<SvVarData> unassignedVariants, boolean allowOverlaps)
    {
        // look for any other SVs which form part of this cluster based on proximity
        int currentIndex = 0;

        while (currentIndex < unassignedVariants.size())
        {
            SvVarData currentVar = unassignedVariants.get(currentIndex);

            if(isEquivSingleBreakend(currentVar))
            {
                ++currentIndex;
                continue;
            }

            // compare with all other SVs in this cluster
            boolean matched = false;
            for (SvVarData otherVar : cluster.getSVs())
            {
                if(!allowOverlaps && isOverlapping(currentVar, otherVar))
                {
                    matched = false;
                    break;
                }

                // test each possible linkage
                if (!areVariantsLinkedByDistance(currentVar, otherVar, mProximityDistance))
                    continue;

                //if(!allowOverlaps && !(currentVar.position(false) < otherVar.position(true) || otherVar.position(false) < currentVar.position(true)))
                //    continue;

                cluster.addVariant(currentVar);
                currentVar.addClusterReason(CLUSTER_REASON_PROXIMITY, otherVar.id());

                if(otherVar.getClusterReason().isEmpty())
                    otherVar.addClusterReason(CLUSTER_REASON_PROXIMITY, currentVar.id());

                matched = true;
                break;
            }

            if(matched)
            {
                unassignedVariants.remove(currentIndex);

                // as soon as a new SV is added to this cluster, need to start checking from the beginning again
                currentIndex = 0;
            }
            else
            {
                ++currentIndex;
            }
        }
    }

    public void mergeClusters(final String sampleId, List<SvCluster> clusters)
    {
        // first apply replication rules since this can affect consistency
        for(SvCluster cluster : clusters)
        {
            // check for sub-clonal / low-copy-number supported variants
            if(cluster.getCount() == 1 && isLowQualityVariant(cluster.getSVs().get(0)))
            {
                cluster.setResolved(true, RESOLVED_LOW_QUALITY);
                continue;
            }

            if(cluster.hasLinkingLineElements())
                continue;

            applyCopyNumberReplication(cluster);

            markClusterLongDelDups(cluster);
            markClusterInversions(cluster);
        }

        int initClusterCount = clusters.size();

        int iterations = 0;

        // the merge must be run a few times since as clusters grow, more single SVs and other clusters
        // will then fall within the bounds of the new larger clusters
        boolean foundMerges = true;

        while(foundMerges && iterations < 5)
        {
            if(mergeOnOverlappingInvDupDels(clusters))
                foundMerges = true;

            if(mergeOnUnresolvedSingles(clusters))
                foundMerges = true;

            ++iterations;
        }

        mergeOnLOHEvents(sampleId, clusters);

        // checkClusterDuplicates(clusters);

        if(clusters.size() < initClusterCount)
        {
            LOGGER.debug("reduced cluster count({} -> {}) iterations({})", initClusterCount, clusters.size(), iterations);
        }
    }

    public static void checkClusterDuplicates(List<SvCluster> clusters)
    {
        for(int i = 0; i < clusters.size(); ++i)
        {
            final SvCluster cluster1 = clusters.get(i);
            for(int j = i + 1; j < clusters.size(); ++j)
            {
                final SvCluster cluster2 = clusters.get(j);

                if(cluster1 == cluster2 || cluster1.id() == cluster2.id())
                {
                    LOGGER.error("cluster({}) exists twice in list", cluster1.id());
                    return;
                }
            }
        }
    }

    private static void applyCopyNumberReplication(SvCluster cluster)
    {
        // use the relative copy number change to replicate some SVs within a cluster
        if(!cluster.hasVariedCopyNumber())
            return;

        // avoid what will likely be balanced translocations
        if(cluster.getCount() == 2 && cluster.getTypeCount(BND) == 2)
            return;

        // first establish the lowest copy number change
        int minCopyNumber = cluster.getMinCopyNumber();
        int maxCopyNumber = cluster.getMaxCopyNumber();

        if(maxCopyNumber > 5 * minCopyNumber)
        {
            LOGGER.debug("cluster({}) skipping replication for large CN variation(min={} max={})",
                    cluster.id(), minCopyNumber, maxCopyNumber);
            return;
        }

        // replicate the SVs which have a higher copy number than their peers
        int clusterCount = cluster.getCount();

        for(int i = 0; i < clusterCount; ++i)
        {
            SvVarData var = cluster.getSVs().get(i);
            int calcCopyNumber = var.impliedCopyNumber(true);

            if(calcCopyNumber <= minCopyNumber)
                continue;

            int svMultiple = calcCopyNumber / minCopyNumber;

            LOGGER.debug("cluster({}) replicating SV({}) {} times, copyNumChg({} vs min={})",
                    cluster.id(), var.posId(), svMultiple, calcCopyNumber, minCopyNumber);

            var.setReplicatedCount(svMultiple);

            for(int j = 1; j < svMultiple; ++j)
            {
                SvVarData newVar = new SvVarData(var);
                cluster.addVariant(newVar);
            }
        }
    }

    public static double LOW_QUALITY_CN_CHANGE = 0.5;

    public boolean isLowQualityVariant(final SvVarData var)
    {
        if(var.isNullBreakend())
            return var.copyNumberChange(true) < LOW_QUALITY_CN_CHANGE;
        else
            return var.copyNumberChange(true) < LOW_QUALITY_CN_CHANGE && var.copyNumberChange(false) < LOW_QUALITY_CN_CHANGE;
    }

    public boolean isEquivSingleBreakend(final SvVarData var)
    {
        if(!var.isNullBreakend())
            return false;

        if(var.isDupBreakend(true) || var.isDupBreakend(false))
            return true;

        return  (var.getAssemblyData(true).contains(ASSEMBLY_TYPE_EQV)
                || var.getAssemblyData(false).contains(ASSEMBLY_TYPE_EQV));
    }

    private void mergeOnLOHEvents(final String sampleId, List<SvCluster> clusters)
    {
        if(mSampleLohData == null)
            return;

        // first extract all the SVs from the LOH events
        final List<SvLOH> lohList = mSampleLohData.get(sampleId);

        if(lohList == null)
            return;

        for(final SvLOH lohEvent : lohList)
        {
            if(lohEvent.StartSV.isEmpty() || lohEvent.EndSV.isEmpty() || lohEvent.StartSV.equals("0") || lohEvent.EndSV.equals("0"))
                continue;

            SvCluster lohClusterStart = null;
            SvVarData lohSvStart = null;
            SvCluster lohClusterEnd = null;
            SvVarData lohSvEnd = null;

            for(SvCluster cluster : clusters)
            {
                if(cluster.hasLinkingLineElements())
                    continue;

                for(final SvVarData var : cluster.getSVs())
                {
                    if(var.id().equals(lohEvent.StartSV))
                    {
                        lohClusterStart = cluster;
                        lohSvStart = var;
                    }
                    if(var.id().equals(lohEvent.EndSV))
                    {
                        lohClusterEnd = cluster;
                        lohSvEnd = var;
                    }

                    if(lohSvStart != null && lohSvEnd != null)
                        break;
                }
            }

            if(lohClusterEnd == null || lohClusterStart == null)
            {
                // LOGGER.error("sample({}) start varId({}) not found in any cluster", sampleId, lohEvent.StartSV);
                continue;
            }

            if(lohClusterStart == lohClusterEnd)
                continue;

            LOGGER.debug("cluster({} svs={}) merges in other cluster({} svs={}) on LOH event(sv1={} sv2={} len={})",
                    lohClusterStart.id(), lohClusterStart.getUniqueSvCount(), lohClusterEnd.id(), lohClusterEnd.getUniqueSvCount(),
                    lohEvent.StartSV, lohEvent.EndSV, lohEvent.Length);

            lohSvStart.addClusterReason(CLUSTER_REASON_LOH, lohSvEnd.id());
            lohSvEnd.addClusterReason(CLUSTER_REASON_LOH, lohSvStart.id());

            lohClusterStart.mergeOtherCluster(lohClusterEnd);
            clusters.remove(lohClusterEnd);
        }
    }

    private void markClusterInversions(final SvCluster cluster)
    {
        if(cluster.getTypeCount(INV) == 0 || cluster.hasLinkingLineElements())
            return;

        // skip cluster-2s which resolved to a simple type
        if(cluster.isResolved() && cluster.isSyntheticSimpleType())
            return;

        for (final SvVarData var : cluster.getSVs())
        {
            if(var.type() == INV && !var.isCrossArm())
            {
                cluster.registerInversion(var);
            }
        }
    }

    private void markClusterLongDelDups(final SvCluster cluster)
    {
        if(cluster.hasLinkingLineElements())
            return;

        if(cluster.isResolved() && cluster.isSyntheticSimpleType())
        {
            if(cluster.getSynDelDupLength() >= mDelDupCutoffLength)
            {
                for (final SvVarData var : cluster.getSVs())
                {
                    cluster.registerLongDelDup(var);
                }
            }

            return;
        }

        if(cluster.getTypeCount(DEL) > 0 || cluster.getTypeCount(DUP) > 0)
        {
            for(final SvVarData var : cluster.getSVs())
            {
                if((var.type() == DUP || var.type() == DEL) && var.length() >= mDelDupCutoffLength && !var.isCrossArm())
                {
                    cluster.registerLongDelDup(var);
                }
            }
        }
    }

    private boolean mergeOnOverlappingInvDupDels(List<SvCluster> clusters)
    {
        // merge any clusters with overlapping inversions, long dels or long dups on the same arm
        int initClusterCount = clusters.size();

        final List<StructuralVariantType> requiredTypes = Lists.newArrayList();
        requiredTypes.add(INV);

        int index1 = 0;
        while(index1 < clusters.size())
        {
            SvCluster cluster1 = clusters.get(index1);

            if(cluster1.getInversions().isEmpty() && cluster1.getLongDelDups().isEmpty() || cluster1.hasLinkingLineElements())
            {
                ++index1;
                continue;
            }

            List<SvVarData> cluster1Svs = Lists.newArrayList();
            cluster1Svs.addAll(cluster1.getLongDelDups());
            cluster1Svs.addAll(cluster1.getInversions());

            int index2 = index1 + 1;
            while(index2 < clusters.size())
            {
                SvCluster cluster2 = clusters.get(index2);

                if(cluster2.getInversions().isEmpty() && cluster2.getLongDelDups().isEmpty() || cluster2.hasLinkingLineElements())
                {
                    ++index2;
                    continue;
                }

                List<SvVarData> cluster2Svs = Lists.newArrayList();
                cluster2Svs.addAll(cluster2.getLongDelDups());
                cluster2Svs.addAll(cluster2.getInversions());

                boolean canMergeClusters = false;

                for (final SvVarData var1 : cluster1Svs)
                {
                    for (final SvVarData var2 : cluster2Svs)
                    {
                        if(!var1.chromosome(true).equals(var2.chromosome(true)))
                            continue;

                        if(var1.position(false) < var2.position(true) || var1.position(true) > var2.position(false))
                            continue;

                        LOGGER.debug("cluster({}) SV({} {}) and cluster({}) SV({} {}) have inversion or longDelDup overlap",
                                cluster1.id(), var1.posId(), var1.type(), cluster2.id(), var2.posId(), var2.type());

                        var1.addClusterReason(var2.type() == INV ? CLUSTER_REASON_INV_OVERLAP : CLUSTER_REASON_LONG_DEL_DUP, var2.id());
                        var2.addClusterReason(var1.type() == INV ? CLUSTER_REASON_INV_OVERLAP : CLUSTER_REASON_LONG_DEL_DUP, var1.id());

                        canMergeClusters = true;
                        break;
                    }


                    if(canMergeClusters)
                        break;
                }

                if(canMergeClusters)
                {
                    cluster1.mergeOtherCluster(cluster2);
                    clusters.remove(index2);
                }
                else
                {
                    ++index2;
                }
            }

            ++index1;
        }

        return clusters.size() < initClusterCount;
    }

    private boolean mergeOnUnresolvedSingles(List<SvCluster> clusters)
    {
        // merge clusters with 1 unresolved single with the following rules:
        // 2 x cluster-1s with SGLs that are each other's nearest neighbours
        //
        // use the chr-breakend map to walk through and find the closest links
        // only apply a rule between the 2 closest breakends at the exclusions of the cluster on their other end
        // unless the other breakend is a short, simple SV

        boolean foundMerges = false;

        for (final Map.Entry<String, List<SvBreakend>> entry : mChrBreakendMap.entrySet())
        {
            // final String chromosome = entry.getKey();
            final List<SvBreakend> breakendList = entry.getValue();
            int breakendCount = breakendList.size();

            for(int i = 0; i < breakendList.size(); ++i)
            {
                final SvBreakend breakend = breakendList.get(i);
                SvVarData var = breakend.getSV();
                final SvCluster cluster = var.getCluster();

                // take the point of view of the cluster with the solo single
                if(cluster.getCount() != 1 || cluster.getSVs().get(0).type() != SGL || cluster.isResolved())
                    continue;

                // now look for a proximate cluster with either another solo single or needing one to be resolved
                // check previous and next breakend's cluster
                SvBreakend prevBreakend = (i > 0) ? breakendList.get(i - 1) : null;
                SvBreakend nextBreakend = (i < breakendCount - 1) ? breakendList.get(i + 1) : null;

                if(prevBreakend != null && prevBreakend.getSV().isSimpleType() && prevBreakend.getSV().length() < mDelDupCutoffLength)
                    prevBreakend = null;

                if(nextBreakend != null && nextBreakend.getSV().isSimpleType() && nextBreakend.getSV().length() < mDelDupCutoffLength)
                    nextBreakend = null;

                // additionally check that breakend after the next one isn't a closer SGL to the next breakend,
                // which would invalidate this one being the nearest neighbour
                long prevProximity = prevBreakend != null ? abs(breakend.position() - prevBreakend.position()) : -1;
                long nextProximity = nextBreakend != null ? abs(breakend.position() - nextBreakend.position()) : -1;

                if(nextBreakend != null && i < breakendCount - 2)
                {
                    final SvBreakend followingBreakend = breakendList.get(i + 2);
                    final SvCluster followingCluster = followingBreakend.getSV().getCluster();

                    if(followingCluster.getCount() == 1 && followingBreakend.getSV().type() == SGL && !followingCluster.isResolved())
                    {
                        long followingProximity = abs(nextBreakend.position() - followingBreakend.position());

                        if (followingProximity < nextProximity)
                            nextBreakend = null;
                    }
                }

                if(nextBreakend == null && prevBreakend == null)
                    continue;

                SvBreakend otherBreakend = null;

                if(nextBreakend != null && prevBreakend != null)
                {
                    otherBreakend = nextProximity < prevProximity ? nextBreakend : prevBreakend;
                }
                else if(nextBreakend != null)
                {
                    otherBreakend = nextBreakend;
                }
                else
                {
                    otherBreakend = prevBreakend;
                }

                SvVarData otherVar = otherBreakend.getSV();
                final SvCluster otherCluster = otherVar.getCluster();

                final String resolvedType = canResolveWithSoloSingle(otherCluster, cluster);

                if(!resolvedType.equals(RESOLVED_TYPE_NONE))
                {
                    otherCluster.mergeOtherCluster(cluster);
                    otherCluster.setResolved(true, resolvedType);

                    clusters.remove(cluster);
                    foundMerges = true;
                    break;
                }
            }
        }

        return foundMerges;
    }

    private final String canResolveWithSoloSingle(SvCluster otherCluster, SvCluster soloSingleCluster)
    {
        // 3 cases:
        // - 2 x SGLs could form a simple DEL or DUP
        // - 2 x SGLs + another SV could form a simple cluster-2 resolved type
        // - a SGL + another SV could form a simple cluster-2 resolved type

        final SvVarData soloSingle = soloSingleCluster.getSVs().get(0);

        if(otherCluster.getCount() == 1)
        {
            final SvVarData otherVar = otherCluster.getSVs().get(0);

            if(otherVar.type() == SGL)
            {
                if(otherCluster.isResolved())
                    return RESOLVED_TYPE_NONE;

                // to form a simple del or dup, they need to have different orientations
                if(soloSingle.orientation(true) == otherVar.orientation(true))
                    return RESOLVED_TYPE_NONE;

                // check copy number consistency
                double cn1 = soloSingle.copyNumberChange(true);
                double cn2 = otherVar.copyNumberChange(true);

                if(!copyNumbersEqual(cn1, cn2))
                    return RESOLVED_TYPE_NONE;

                boolean ssFirst = soloSingle.position(true) < otherVar.position(true);
                boolean ssPosOrientation = soloSingle.orientation(true) == 1;

                StructuralVariantType syntheticType = (ssFirst == ssPosOrientation) ? DEL : DUP;

                long length = abs(soloSingle.position(true) - otherVar.position(true));

                LOGGER.debug("cluster({}) SV({}) and cluster({}) SV({}) syntheticType({}) length({})",
                        soloSingleCluster.id(), soloSingle.posId(), otherCluster.id(), otherVar.posId(), syntheticType, length);

                soloSingle.addClusterReason(CLUSTER_REASON_SOLO_SINGLE, syntheticType.toString() + "_" + otherVar.id());
                otherVar.addClusterReason(CLUSTER_REASON_SOLO_SINGLE, syntheticType.toString() + "_" + soloSingle.id());

                return syntheticType == DUP ? RESOLVED_TYPE_SGL_PAIR_DUP : RESOLVED_TYPE_SGL_PAIR_DEL;
            }
            else
            {
                boolean inconsistentOnStart;
                if(otherVar.hasInconsistentCopyNumberChange(true) && otherVar.chromosome(false).equals(soloSingle.chromosome(true)))
                {
                    inconsistentOnStart = true;
                }
                else if(otherVar.hasInconsistentCopyNumberChange(false) && otherVar.chromosome(true).equals(soloSingle.chromosome(true)))
                {
                    inconsistentOnStart = false;
                }
                else
                {
                    return RESOLVED_TYPE_NONE;
                }

                double cnInconsistency = otherVar.getSvData().ploidy() - otherVar.copyNumberChange(inconsistentOnStart);

                if(round(cnInconsistency) != round(soloSingle.copyNumberChange(true)))
                    return RESOLVED_TYPE_NONE;

                LOGGER.debug(String.format("cluster(%s) SV(%s) and cluster(%s) SV(%s) potentially resolve CN inconsistency(%.2f vs %.2f)",
                        soloSingleCluster.id(), soloSingle.posId(), otherCluster.id(), otherVar.posId(),
                        cnInconsistency, soloSingle.copyNumberChange(true)));

                soloSingle.addClusterReason(CLUSTER_REASON_SOLO_SINGLE, "CnInc_" + otherVar.id());
                otherVar.addClusterReason(CLUSTER_REASON_SOLO_SINGLE, "CnInc_" + soloSingle.id());

                return RESOLVED_TYPE_SGL_PLUS_INCONSISTENT;
            }
        }
        else
        {
            return RESOLVED_TYPE_NONE;
        }
    }

    public static void addClusterReason(SvCluster mergedCluster, final String reason, final String linkingVarId)
    {
        for(SvVarData var : mergedCluster.getSVs())
        {
            var.addClusterReason(reason, linkingVarId);
        }
    }

    private static int DEL_DUP_LENGTH_TRIM_COUNT = 5;
    private static int MAX_ARM_COUNT = 41; // excluding the 5 short arms

    public void setSimpleVariantLengths(final String sampleId)
    {
        mDelDupCutoffLength = 0;

        List<Long> lengthsList = Lists.newArrayList();

        int simpleArmCount = 0;

        for (final Map.Entry<String, List<SvBreakend>> entry : mChrBreakendMap.entrySet())
        {
            final String chromosome = entry.getKey();
            final List<SvBreakend> breakendList = entry.getValue();

            // first check for complex events on the arm since these will be skipped
            boolean pArmHasInversions = false;
            boolean qArmHasInversions = false;

            for(final SvBreakend breakend : breakendList)
            {
                final SvVarData var = breakend.getSV();

                if(var.type() != INV)
                    continue;

                if(!pArmHasInversions && (var.arm(true) == CHROMOSOME_ARM_P || var.arm(false) == CHROMOSOME_ARM_P))
                    pArmHasInversions = true;

                if(!qArmHasInversions && (var.arm(true) == CHROMOSOME_ARM_Q || var.arm(false) == CHROMOSOME_ARM_Q))
                    qArmHasInversions = true;

                if(pArmHasInversions && qArmHasInversions)
                    break;
            }

            // skip chromosome altogether
            if(pArmHasInversions && qArmHasInversions)
                continue;

            if(!pArmHasInversions)
                ++simpleArmCount;

            if(!qArmHasInversions)
                ++simpleArmCount;

            for(final SvBreakend breakend : breakendList)
            {
                if(!breakend.usesStart() || !(breakend.getSV().type() == DEL || breakend.getSV().type() == DUP))
                    continue;

                final SvVarData var = breakend.getSV();

                if(pArmHasInversions)
                {
                    if (var.arm(true) == CHROMOSOME_ARM_P || var.arm(false) == CHROMOSOME_ARM_P)
                        continue;
                }

                if(qArmHasInversions)
                {
                    if (var.arm(true) == CHROMOSOME_ARM_Q || var.arm(false) == CHROMOSOME_ARM_Q)
                        continue;
                }

                lengthsList.add(var.length());
            }

            // LOGGER.debug("sample({}) chr({}) svCount({} delDups({})", sampleId, chromosome, breakendList.size(), armCount);
        }

        int trimCount = (int)round(simpleArmCount / (double)MAX_ARM_COUNT * DEL_DUP_LENGTH_TRIM_COUNT);

        if(lengthsList.size() > trimCount)
        {
            Collections.sort(lengthsList);
            int lengthIndex = lengthsList.size() - trimCount - 1; // 10 items, index 0 - 9, exclude 5 - 9, select 9
            mDelDupCutoffLength = lengthsList.get(lengthIndex);
        }

        LOGGER.debug("sample({}) simple dels and dups: count({}) cutoff-length({}) simpleArms({}) trimCount({})",
                sampleId, lengthsList.size(), mDelDupCutoffLength, simpleArmCount, trimCount);

        // LOGGER.info("DEL_DUP_CUTOFF: {},{},{},{},{}",
        //        sampleId, lengthsList.size(), mDelDupCutoffLength, simpleArmCount, trimCount);

        mDelDupCutoffLength = min(max(mDelDupCutoffLength, MIN_SIMPLE_DUP_DEL_CUTOFF), MAX_SIMPLE_DUP_DEL_CUTOFF);
    }

    public void populateChromosomeBreakendMap(final List<SvVarData> allVariants)
    {
        mChrBreakendMap.clear();

        // add each SV's breakends to a map keyed by chromosome, with the breakends in order of position lowest to highest
        for (final SvVarData var : allVariants)
        {
            // add each breakend in turn
            for (int i = 0; i < 2; ++i)
            {
                boolean useStart = (i == 0);

                if (!useStart && var.isNullBreakend())
                    continue;

                final String chr = var.chromosome(useStart);
                long position = var.position(useStart);

                if (!mChrBreakendMap.containsKey(chr))
                {
                    List<SvBreakend> breakendList = Lists.newArrayList();
                    breakendList.add(var.getBreakend(useStart));
                    mChrBreakendMap.put(chr, breakendList);
                    continue;
                }

                // otherwise add the variant in order by ascending position
                List<SvBreakend> breakendList = mChrBreakendMap.get(chr);

                int index = 0;
                for (; index < breakendList.size(); ++index)
                {
                    final SvBreakend breakend = breakendList.get(index);

                    if (position < breakend.position())
                        break;
                }

                breakendList.add(index, var.getBreakend(useStart));
            }
        }

        // cache indicies for faster look-up
        for (Map.Entry<String, List<SvBreakend>> entry : mChrBreakendMap.entrySet())
        {
            List<SvBreakend> breakendList = entry.getValue();

            for (int i = 0; i < breakendList.size(); ++i)
            {
                final SvBreakend breakend = breakendList.get(i);
                breakend.setChrPosIndex(i);

                if(i == 0 || i == breakendList.size() - 1)
                {
                    // cache implied telomere copy numbers per chromosome
                    final String chromosome = breakend.chromosome();
                    final String chrArm = makeChrArmStr(chromosome, breakend.arm());

                    double impliedCN;
                    if((breakend.orientation() == 1 && i == 0) || (breakend.orientation() == -1 && i == breakendList.size() - 1))
                    {
                        impliedCN = breakend.getSV().copyNumber(breakend.usesStart());
                    }
                    else
                    {
                        impliedCN = breakend.getSV().copyNumber(breakend.usesStart()) - breakend.getSV().copyNumberChange(breakend.usesStart());
                    }

                    mTelomereCopyNumberMap.put(chrArm, impliedCN);

                    if(i == 0)
                    {
                        if(breakend.arm() == CHROMOSOME_ARM_Q)
                            mTelomereCopyNumberMap.put(makeChrArmStr(chromosome, CHROMOSOME_ARM_P), impliedCN);
                    }
                    else
                    {
                        if(breakend.arm() == CHROMOSOME_ARM_P)
                            mTelomereCopyNumberMap.put(makeChrArmStr(chromosome, CHROMOSOME_ARM_Q), impliedCN);

                        // record chromosome max copy number
                        double maxCopyNumber = max(telomereCopyNumber(chromosome, CHROMOSOME_ARM_P), telomereCopyNumber(chromosome, CHROMOSOME_ARM_Q));
                        mChromosomeCopyNumberMap.put(chromosome, maxCopyNumber);
                    }
                }
            }
        }
    }

    public double chromosomeCopyNumber(final String chromosome)
    {
        Double copyNumber = mChromosomeCopyNumberMap.get(chromosome);
        return copyNumber != null ? copyNumber : 0;
    }

    public double telomereCopyNumber(final String chromosome, final String arm)
    {
        return telomereCopyNumber(makeChrArmStr(chromosome, arm));
    }

    public double telomereCopyNumber(final String chrArm)
    {
        Double copyNumber = mTelomereCopyNumberMap.get(chrArm);
        return copyNumber != null ? copyNumber : 0;
    }

    public void annotateNearestSvData()
    {
        // mark each SV's nearest other SV and its relationship - neighbouring or overlapping
        // and any duplicate breakends as well
        for(Map.Entry<String, List<SvBreakend>> entry : mChrBreakendMap.entrySet())
        {
            List<SvBreakend> breakendList = entry.getValue();

            int breakendCount = breakendList.size();

            for(int i = 0; i < breakendList.size(); ++i)
            {
                final SvBreakend breakend = breakendList.get(i);
                SvVarData var = breakend.getSV();

                final SvBreakend prevBreakend = (i > 0) ? breakendList.get(i - 1) : null;
                final SvBreakend nextBreakend = (i < breakendCount-1) ? breakendList.get(i + 1) : null;

                long closestDistance = -1;
                if(prevBreakend != null && prevBreakend.getSV() != var)
                {
                    long distance = breakend.position() - prevBreakend.position();
                    closestDistance = distance;
                }

                if(nextBreakend != null && nextBreakend.getSV() != var)
                {
                    long distance = nextBreakend.position() - breakend.position();
                    if(closestDistance < 0 || distance < closestDistance)
                        closestDistance = distance;

                    if(distance <= PERMITED_DUP_BE_DISTANCE && breakend.orientation() == nextBreakend.orientation())
                    {
                        var.setIsDupBreakend(true, breakend.usesStart());
                        nextBreakend.getSV().setIsDupBreakend(true, nextBreakend.usesStart());
                    }
                }

                if(closestDistance >= 0 && (var.getNearestSvDistance() == -1 || closestDistance < var.getNearestSvDistance()))
                    var.setNearestSvDistance(closestDistance);

                String relationType = "";
                if((prevBreakend != null && prevBreakend.getSV() == var) || (nextBreakend != null && nextBreakend.getSV() == var))
                    relationType = RELATION_TYPE_NEIGHBOUR;
                else
                    relationType = RELATION_TYPE_OVERLAP;

                var.setNearestSvRelation(relationType);
            }
        }
    }

    public void markInversionPairTypes(SvCluster cluster)
    {
        // determine overlap configurations

        /* 4 types of inversion pairs
        1. DEL with enclosed inverted TI (also know as 'Reciprocal INV') - Have 2 DSB and a ’TI' from the middle which is inverted.
            - outer breakends face out (the DEL)
            - TI enclosed
        2. DEL with external inverted TI
            - resultant type = DEL
            - length = other 2 breakends
        3. DUP with external inverted TI
            - 2 x TIs, but TI breakends don't overlap
            - type = DUP
            - TI and DUP length are interchangable, but choose shorter for TI
        4. DUP with enclosed inverted TI
            - no overlapping breakends
            - TI from the innermost 2
            - outer breakends face in
            - resultant type = DUP
            - length is outside 2 breakends (ie the other 2)
         */

        final SvVarData var1 = cluster.getSV(0);
        final SvVarData var2 = cluster.getSV(1);

        if(cluster.getLinkedPairs().isEmpty())
        {
            if(var1.orientation(true) == var2.orientation(true))
                return;

            // 2 INVs without any overlap can form 2 deletion bridges
            long syntheticLength;

            if(var1.orientation(true) == 1 && var1.position(false) < var2.position(true) + MIN_TEMPLATED_INSERTION_LENGTH)
            {
                // take the outermost positions for the DEL length
                syntheticLength = abs(var2.position(false) - var1.position(true));
            }
            else if(var2.orientation(true) == 1 && var2.position(false) < var1.position(true) + MIN_TEMPLATED_INSERTION_LENGTH)
            {
                syntheticLength = abs(var1.position(false) - var2.position(true));
            }
            else
            {
                LOGGER.warn("cluster({}) inversion-pair no DBs or TIs", cluster.id());
                return;
            }

            cluster.setSynDelDupData(syntheticLength, 0);
            boolean isResolved = syntheticLength < mDelDupCutoffLength;
            cluster.setResolved(isResolved, RESOLVED_TYPE_DEL_INT_TI);
            return;
        }

        resolveSyntheticDelDupCluster(cluster);
    }

    private boolean resolveSyntheticDelDupCluster(SvCluster cluster)
    {
        if(cluster.getLinkedPairs().isEmpty())
            return false;

        final SvVarData var1 = cluster.getSV(0);
        final SvVarData var2 = cluster.getSV(1);

        // first work out if there are 1 or 2 templated insertions
        SvLinkedPair linkedPair1 = cluster.getLinkedPairs().get(0);

        boolean v1OpenOnStart = linkedPair1.first().equals(var1) ? linkedPair1.firstUnlinkedOnStart() : linkedPair1.secondUnlinkedOnStart();
        boolean v2OpenOnStart = linkedPair1.first().equals(var2) ? linkedPair1.firstUnlinkedOnStart() : linkedPair1.secondUnlinkedOnStart();

        SvLinkedPair linkedPair2 = cluster.getLinkedPairs().size() == 2 ? cluster.getLinkedPairs().get(1) : null;

        if(linkedPair2 != null && !linkedPair1.hasLinkClash(linkedPair2))
        {
            // existing second link is fine to consider
        }
        else if(areLinkedSection(var1, var2, v1OpenOnStart, v2OpenOnStart, false))
        {
            linkedPair2 = new SvLinkedPair(var1, var2, LINK_TYPE_TI, v1OpenOnStart, v2OpenOnStart);
        }
        else if(areSectionBreak(var1, var2, v1OpenOnStart, v2OpenOnStart))
        {
            linkedPair2 = new SvLinkedPair(var1, var2, LINK_TYPE_DB, v1OpenOnStart, v2OpenOnStart);
        }
        else
        {
            LOGGER.error("cluster({}) ids({} & {}) neither TI nor DB", cluster.id(), var1.id(), var2.id());
            return false;
        }

        // set the TI link to be the shorter of the 2
        SvLinkedPair tiPair;
        SvLinkedPair otherPair;

        if(linkedPair1.linkType() == LINK_TYPE_TI && linkedPair2.linkType() == LINK_TYPE_TI)
        {
            tiPair = linkedPair1.length() < linkedPair2.length() ? linkedPair1 : linkedPair2;
            otherPair = linkedPair1 == tiPair ? linkedPair2 : linkedPair1;
        }
        else if(linkedPair1.linkType() == LINK_TYPE_TI)
        {
            // take the DB for the main DEL if one exists
            tiPair = linkedPair1;
            otherPair = linkedPair2;
        }
        else if(linkedPair2.linkType() == LINK_TYPE_TI)
        {
            tiPair = linkedPair2;
            otherPair = linkedPair1;
        }
        else
        {
            // 2 deletion bridges
            tiPair = linkedPair1.length() < linkedPair2.length() ? linkedPair1 : linkedPair2;
            otherPair = linkedPair1 == tiPair ? linkedPair2 : linkedPair1;
        }

        long tiPos1 = tiPair.first().position(tiPair.firstLinkOnStart());
        long tiPos2 = tiPair.second().position(tiPair.secondLinkOnStart());

        long otherPos1 = otherPair.first().position(otherPair.firstLinkOnStart());
        long otherPos2 = otherPair.second().position(otherPair.secondLinkOnStart());

        boolean isTIEnclosed = false;

        long lowerOtherPosLimit = otherPos1 < otherPos2 ? otherPos1 : otherPos2;
        lowerOtherPosLimit -= MIN_TEMPLATED_INSERTION_LENGTH;

        long upperOtherPosLimit = otherPos1 > otherPos2 ? otherPos1 : otherPos2;
        upperOtherPosLimit += MIN_TEMPLATED_INSERTION_LENGTH;

        if(tiPos1 >= lowerOtherPosLimit && tiPos1 <= upperOtherPosLimit
                && tiPos2 >= lowerOtherPosLimit && tiPos2 <= upperOtherPosLimit)
        {
            isTIEnclosed = true;
        }

        String resolvedType = "";

        if(tiPair.linkType() == LINK_TYPE_DB && otherPair != null && otherPair.linkType() == LINK_TYPE_DB)
        {
            resolvedType = RESOLVED_TYPE_DEL_INT_TI;
        }
        else
        {
            if (tiPair.linkType() == LINK_TYPE_DB || otherPair.linkType() == LINK_TYPE_DB)
            {
                if (!isTIEnclosed)
                {
                    resolvedType = RESOLVED_TYPE_DEL_EXT_TI;
                }
                else
                {
                    resolvedType = RESOLVED_TYPE_DEL_INT_TI;
                }
            }
            else
            {
                if (!isTIEnclosed)
                {
                    resolvedType = RESOLVED_TYPE_DUP_EXT_TI;
                }
                else
                {
                    resolvedType = RESOLVED_TYPE_DUP_INT_TI;
                }
            }
        }

        boolean isResolved = otherPair.length() < mDelDupCutoffLength;
        cluster.setResolved(isResolved, resolvedType);
        cluster.setSynDelDupData(otherPair.length(), tiPair.length());

        return true;
    }

    public void markBndPairTypes(SvCluster cluster)
    {
        final SvVarData var1 = cluster.getSV(0);
        final SvVarData var2 = cluster.getSV(1);

        /* possible configurations:
            1. Reciprocal Translocation
            - 2 DBs, no overlappying breakends OR
            - TIs converted to DBs since too short

            2. One set of breakends facing (the TI) the other facing away (the DEL)
            - DEL with TI

            3. Two sets of facing breakends so 2 TIs
            - but rather than a closed loop, one set remain unlinked (the overlap being the DUP)

            Other configurations are nothing
         */

        if(cluster.getLinkedPairs().isEmpty() && arePairedDeletionBridges(var1, var2))
        {
            cluster.setResolved(true, RESOLVED_TYPE_RECIPROCAL_TRANS);
            return;
        }

        // first work out if there are 1 or 2 templated insertions
        final SvLinkedPair lp1;
        if(cluster.getLinkedPairs().size() > 0 && cluster.getLinkedPairs().get(0).linkType() == LINK_TYPE_TI)
            lp1 = cluster.getLinkedPairs().get(0);
        else
            lp1 = null;

        final SvLinkedPair lp2;
        if(cluster.getLinkedPairs().size() > 1 && cluster.getLinkedPairs().get(1).linkType() == LINK_TYPE_TI)
            lp2 = cluster.getLinkedPairs().get(1);
        else
            lp2 = null;

        if(lp1 == null && lp2 == null)
        {
            LOGGER.debug("cluster({} {}) has no linked pairs", cluster.id(), cluster.getDesc());
            return;
        }

        final SvLinkedPair tiLinkedPair;

        if(lp1 != null && lp2 != null)
        {
            // take assembly pair if exists over inferred pair, otherwise take the shorter of the 2
            if(!lp1.isInferred())
                tiLinkedPair = lp1;
            else if(!lp2.isInferred())
                tiLinkedPair = lp2;
            else if(lp1.length() < lp2.length())
                tiLinkedPair = lp1;
            else
                tiLinkedPair = lp2;
        }
        else
        {
            tiLinkedPair = lp1 != null ? lp1 : lp2;
        }

        // must start and finish on the same chromosome
        boolean v1OpenOnStart = tiLinkedPair.first().equals(var1) ? tiLinkedPair.firstUnlinkedOnStart() : tiLinkedPair.secondUnlinkedOnStart();
        boolean v2OpenOnStart = tiLinkedPair.first().equals(var2) ? tiLinkedPair.firstUnlinkedOnStart() : tiLinkedPair.secondUnlinkedOnStart();

        if(!var1.chromosome(v1OpenOnStart).equals(var2.chromosome(v2OpenOnStart)))
            return;

        if(var1.orientation(v1OpenOnStart) == var2.orientation(v2OpenOnStart))
            return;

        String pairDesc = "";

        if(areSectionBreak(var1, var2, v1OpenOnStart, v2OpenOnStart))
        {
            pairDesc = RESOLVED_TYPE_DEL_EXT_TI;
        }
        else
        {
            pairDesc = RESOLVED_TYPE_DUP_EXT_TI;
        }

        long syntheticLength = abs(var1.position(v1OpenOnStart) - var2.position(v2OpenOnStart));

        boolean isResolved = syntheticLength < mDelDupCutoffLength;
        cluster.setResolved(isResolved, pairDesc);
        cluster.setSynDelDupData(syntheticLength, lp1.length());
    }

    public boolean markDelDupPairTypes(SvCluster cluster)
    {
        return resolveSyntheticDelDupCluster(cluster);
    }

    public void setChromosomalArmStats(final List<SvVarData> allVariants)
    {
        mChrArmSvCount.clear();
        mChrArmSvExpected.clear();
        mChrArmSvRate.clear();
        mMedianChrArmRate = 0;

        // form a map of unique arm to SV count
        for(final SvVarData var : allVariants)
        {
            String chrArmStart = getVariantChrArm(var,true);
            String chrArmEnd = getVariantChrArm(var,false);

            // ensure an entry exists
            if (!mChrArmSvCount.containsKey(chrArmStart))
            {
                mChrArmSvCount.put(chrArmStart, 0);
            }

            // exclude LINE elements from back-ground rates
            mChrArmSvCount.replace(chrArmStart, mChrArmSvCount.get(chrArmStart) + 1);

            if(!var.isNullBreakend())
            {
                if (!chrArmStart.equals(chrArmEnd) && !mChrArmSvCount.containsKey(chrArmEnd))
                {
                    mChrArmSvCount.put(chrArmEnd, 0);
                }

                mChrArmSvCount.replace(chrArmEnd, mChrArmSvCount.get(chrArmEnd) + 1);
            }
        }

        // now determine the background rate by taking the median value from amongst the arms
        // factoring in the arms which have no Q (14-16, 21-22) and excluding the X & Ys
        for(Map.Entry<String, Integer> entry : mChrArmSvCount.entrySet())
        {
            final String chrArm = entry.getKey();
            final String chromosome = getChrFromChrArm(chrArm);
            final String arm = getArmFromChrArm(chrArm);

            long chrArmLength = getChromosomalArmLength(chromosome, arm);
            int svCount = entry.getValue();
            double ratePerLength = svCount / (chrArmLength / REF_BASE_LENGTH); // the factor isn't important

            mChrArmSvRate.put(chrArm, ratePerLength);
            // LOGGER.debug("chrArm({}) ratePerMill({}) from count({}) length({})", chrArm, ratePerLength, svCount, chrArmLength);
        }

        mChrArmSvRate = sortByValue(mChrArmSvRate, false);

        mMedianChrArmRate = 0;
        int chrArmIndex = 0;
        for(Map.Entry<String, Double> entry : mChrArmSvRate.entrySet())
        {
            // LOGGER.debug("chrArm({}: {}) svRate({})", chrArmIndex, entry.getKey(), entry.getValue());

            if(chrArmIndex == 20)
                mMedianChrArmRate = entry.getValue();

           ++chrArmIndex;
        }

        LOGGER.debug(String.format("median SV rate(%.2f)", mMedianChrArmRate));

        // now create another map of expected SV count per arm using the median rate
        for(Map.Entry<String, Double> entry : mChrArmSvRate.entrySet())
        {
            final String chrArm = entry.getKey();
            final String chromosome = getChrFromChrArm(chrArm);
            final String arm = getArmFromChrArm(chrArm);

            long chrArmLength = getChromosomalArmLength(chromosome, arm);
            double expectedSvCount = (int) round((chrArmLength / REF_BASE_LENGTH) * mMedianChrArmRate);
            LOGGER.debug("chrArm({}) expectedSvCount({}) vs actual({})", chrArm, expectedSvCount, mChrArmSvCount.get(chrArm));

            mChrArmSvExpected.put(chrArm, expectedSvCount);
        }
    }

    public String getChrArmData(final SvVarData var)
    {
        String chrArmStart = getVariantChrArm(var,true);

        boolean hasEnd = !var.isNullBreakend();
        String chrArmEnd = hasEnd ? getVariantChrArm(var,false) : "";

        // report Start SV count : Expected SV Count : End SV Count : Expected SV Count
        return String.format("%d,%.2f,%d,%.2f",
                mChrArmSvCount.get(chrArmStart), mChrArmSvExpected.get(chrArmStart),
                hasEnd ? mChrArmSvCount.get(chrArmEnd) : 0, hasEnd ? mChrArmSvExpected.get(chrArmEnd) : 0.0);
    }

    private static Map<String, Double> sortByValue(Map<String, Double> unsortMap, final boolean order)
    {
        List<Map.Entry<String, Double>> list = new LinkedList<Map.Entry<String, Double>>(unsortMap.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<String, Double>>()
        {
            public int compare(Map.Entry<String, Double> o1,
                    Map.Entry<String, Double> o2)
            {
                if (order)
                {
                    return o1.getValue().compareTo(o2.getValue());
                }
                else
                {
                    return o2.getValue().compareTo(o1.getValue());

                }
            }
        });

        // Maintaining insertion order with the help of LinkedList
        Map<String, Double> sortedMap = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Double> entry : list)
        {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

}
