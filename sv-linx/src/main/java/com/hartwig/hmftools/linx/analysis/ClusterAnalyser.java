package com.hartwig.hmftools.linx.analysis;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.UNDER_CLUSTERING;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.reportUnderclustering;
import static com.hartwig.hmftools.linx.analysis.ClusteringPrep.annotateNearestSvData;
import static com.hartwig.hmftools.linx.analysis.ClusteringPrep.associateBreakendCnEvents;
import static com.hartwig.hmftools.linx.analysis.ClusteringPrep.populateChromosomeBreakendMap;
import static com.hartwig.hmftools.linx.analysis.ClusteringPrep.setSimpleVariantLengths;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.DOUBLE_MINUTES;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.FOLDBACK_MATCHES;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.annotateChainedClusters;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.annotateTemplatedInsertions;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.findIncompleteFoldbackCandidates;
import static com.hartwig.hmftools.linx.analysis.ClusterAnnotations.runAnnotation;
import static com.hartwig.hmftools.linx.analysis.SvClassification.isSimpleSingleSV;
import static com.hartwig.hmftools.linx.analysis.SimpleClustering.checkClusterDuplicates;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.copyNumbersEqual;
import static com.hartwig.hmftools.linx.annotators.LineElementAnnotator.markLineCluster;
import static com.hartwig.hmftools.linx.types.ResolvedType.LINE;
import static com.hartwig.hmftools.linx.types.ResolvedType.NONE;
import static com.hartwig.hmftools.linx.types.ResolvedType.SIMPLE_GRP;
import static com.hartwig.hmftools.linx.types.SvCluster.isSpecificCluster;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.utils.PerformanceCounter;
import com.hartwig.hmftools.linx.chaining.ChainFinder;
import com.hartwig.hmftools.linx.chaining.LinkFinder;
import com.hartwig.hmftools.linx.cn.CnDataLoader;
import com.hartwig.hmftools.linx.gene.SvGeneTranscriptCollection;
import com.hartwig.hmftools.linx.types.SvCluster;
import com.hartwig.hmftools.linx.types.SvVarData;
import com.hartwig.hmftools.linx.LinxConfig;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClusterAnalyser {

    private final LinxConfig mConfig;
    private final ClusteringState mState;

    private SvFilters mFilters;
    private SimpleClustering mSimpleClustering;
    private ComplexClustering mComplexClustering;

    private CnDataLoader mCnDataLoader;
    private DoubleMinuteFinder mDmFinder;

    private String mSampleId;
    private final List<SvCluster> mClusters;
    private List<SvVarData> mAllVariants;
    private ChainFinder mChainFinder;
    private LinkFinder mLinkFinder;

    private boolean mRunValidationChecks;

    PerformanceCounter mPcClustering;
    PerformanceCounter mPcChaining;
    PerformanceCounter mPcAnnotation;

    public static int SMALL_CLUSTER_SIZE = 3;

    private static final Logger LOGGER = LogManager.getLogger(ClusterAnalyser.class);

    public ClusterAnalyser(final LinxConfig config)
    {
        mConfig = config;
        mState = new ClusteringState();
        mClusters = Lists.newArrayList();

        mFilters = new SvFilters(mState);
        mSimpleClustering = new SimpleClustering(mState, mConfig);
        mComplexClustering = new ComplexClustering(mState, mClusters, mSimpleClustering);

        mCnDataLoader = null;
        mSampleId = "";
        mAllVariants = Lists.newArrayList();
        mLinkFinder = new LinkFinder();
        mChainFinder = new ChainFinder();
        mDmFinder = new DoubleMinuteFinder();

        if(mConfig.hasMultipleSamples())
        {
            mChainFinder.getDiagnostics().setOutputDir(mConfig.OutputDataPath, mConfig.LogChainingMaxSize);
            mDmFinder.setOutputDir(mConfig.OutputDataPath);
        }

        mChainFinder.setUseAllelePloidies(true); // can probably remove and assume always in place
        mChainFinder.setLogVerbose(mConfig.LogVerbose);
        mLinkFinder.setLogVerbose(mConfig.LogVerbose);

        mRunValidationChecks = false; // emabled in unit tests and after changes to merging-rule flow

        mPcClustering = new PerformanceCounter("Clustering");
        mPcChaining = new PerformanceCounter("Chaining");
        mPcAnnotation = new PerformanceCounter("Annotation");
    }

    public final ClusteringState getState() { return mState; }

    public void setCnDataLoader(CnDataLoader cnDataLoader)
    {
        mCnDataLoader = cnDataLoader;
        mState.setSampleCnEventData(mCnDataLoader.getLohData(), mCnDataLoader.getHomLossData());

        mDmFinder.setCopyNumberAnalyser(cnDataLoader);
        mComplexClustering.setCopyNumberAnalyser(cnDataLoader);
    }

    public void setGeneCollection(final SvGeneTranscriptCollection geneTransCache)
    {
        mDmFinder.setGeneTransCache(geneTransCache);
    }

    // access for unit testing
    public final ChainFinder getChainFinder() { return mChainFinder; }
    public final LinkFinder getLinkFinder() { return mLinkFinder; }

    public void setRunValidationChecks(boolean toggle) { mRunValidationChecks = toggle; }

    public void setSampleData(final String sampleId, List<SvVarData> allVariants)
    {
        mSampleId = sampleId;
        mAllVariants = allVariants;
        mClusters.clear();
        mSimpleClustering.initialise(sampleId);
        mChainFinder.setSampleId(sampleId);
    }

    public final List<SvCluster> getClusters() { return mClusters; }

    public void preClusteringPreparation()
    {
        mState.reset();

        populateChromosomeBreakendMap(mAllVariants, mState);
        mFilters.applyFilters();

        annotateNearestSvData(mState.getChrBreakendMap());

        LinkFinder.findDeletionBridges(mState.getChrBreakendMap());

        setSimpleVariantLengths(mState);
    }

    public boolean clusterAndAnalyse()
    {
        mClusters.clear();
        mDmFinder.clear();

        mPcClustering.start();
        mFilters.clusterExcludedVariants(mClusters);
        mSimpleClustering.clusterByProximity(mClusters);
        mPcClustering.pause();

        // mark line clusters since these are excluded from most subsequent logic
        mClusters.forEach(x -> markLineCluster(x, mConfig.ProximityDistance));

        associateBreakendCnEvents(mSampleId, mState);

        if(mRunValidationChecks)
        {
            if(!mSimpleClustering.validateClustering(mClusters))
            {
                LOGGER.info("exiting with cluster-validation errors");
                return false;
            }
        }

        mPcChaining.start();
        findLimitedChains();
        mPcChaining.pause();

        mPcClustering.resume();
        mSimpleClustering.mergeClusters(mSampleId, mClusters);
        mPcClustering.pause();

        // log basic clustering details
        mClusters.stream().filter(x -> x.getSvCount() > 1).forEach(SvCluster::logDetails);

        // INVs and other SV-pairs which make foldbacks are now used in the inconsistent clustering logic
        FoldbackFinder.markFoldbacks(mState.getChrBreakendMap());

        // subclonal clusters won't be merged any further
        mClusters.forEach(x -> x.markSubclonal());

        mPcClustering.resume();
        mComplexClustering.applyRules(mSampleId);
        mPcClustering.stop();

        mPcChaining.resume();
        findLinksAndChains();
        dissolveSimpleGroups();
        mPcChaining.stop();

        if(mRunValidationChecks)
        {
            if(!mSimpleClustering.validateClustering(mClusters) || !checkClusterDuplicates(mClusters))
            {
                LOGGER.warn("exiting with cluster-validation errors");
                return false;
            }
        }

        mPcAnnotation.start();

        // final clean-up and analysis
        for(SvCluster cluster : mClusters)
        {
            if(!cluster.isResolved() && cluster.getResolvedType() != NONE)
            {
                // any cluster with a long DEL or DUP not merged can now be marked as resolved
                if(cluster.getSvCount() == 1 && cluster.getResolvedType().isSimple())
                    cluster.setResolved(true, cluster.getResolvedType());
            }

            cluster.cacheLinkedPairs();
            cluster.buildArmClusters();

            // isSpecificCluster(cluster);

            reportClusterFeatures(cluster);
        }

        reportOtherFeatures();

        mPcAnnotation.stop();
        return true;
    }

    public void findLimitedChains()
    {
        // chain small clusters and only assembled links in larger ones
        for(SvCluster cluster : mClusters)
        {
            if(isSimpleSingleSV(cluster))
            {
                mDmFinder.analyseCluster(cluster);
                setClusterResolvedState(cluster, false);
                continue;
            }

            // more complicated clusters for now
            boolean isSimple = cluster.getSvCount() <= SMALL_CLUSTER_SIZE && cluster.isConsistent() && !cluster.hasVariedPloidy();

            mLinkFinder.findAssembledLinks(cluster);
            cluster.setPloidyReplication(mConfig.ChainingSvLimit);

            if(isSimple)
                mDmFinder.analyseCluster(cluster);

            // then look for fully-linked clusters, ie chains involving all SVs
            findChains(cluster, !isSimple);

            if(isSimple)
            {
                setClusterResolvedState(cluster, false);

                if(cluster.isFullyChained(true))
                {
                    LOGGER.debug("cluster({}) simple and consistent with {} SVs", cluster.id(), cluster.getSvCount());
                }
            }
        }
    }

    private void findLinksAndChains()
    {
        for (SvCluster cluster : mClusters)
        {
            // isSpecificCluster(cluster);

            if (cluster.getResolvedType() == LINE) // only simple assembly links for LINE clusters
                continue;

            if (cluster.getResolvedType() != NONE) // any cluster previously resolved and not modified does not need to be chained again
                continue;

            // these are either already chained or no need to chain
            if (isSimpleSingleSV(cluster) || cluster.isFullyChained(false) || cluster.getSvCount() < 2)
            {
                setClusterResolvedState(cluster, true);
                continue;
            }

            cluster.dissolveLinksAndChains();

            // look for and mark clusters has DM candidates, which can subsequently affect chaining
            mDmFinder.analyseCluster(cluster, true);

            cluster.setPloidyReplication(mConfig.ChainingSvLimit);

            // no need to re-find assembled TIs

            // then look for fully-linked clusters, ie chains involving all SVs
            findChains(cluster, false);

            setClusterResolvedState(cluster, true);
            cluster.logDetails();
        }
    }

    private void dissolveSimpleGroups()
    {
        // break apart any clusters of simple SVs which aren't likely or required to be chained
        List<SvCluster> simpleGroups = mClusters.stream()
                .filter(x -> x.getResolvedType() == SIMPLE_GRP)
                .filter(x -> x.getLohEvents().isEmpty())
                .filter(x -> x.getAssemblyLinkedPairs().isEmpty())
                .collect(Collectors.toList());

        for(SvCluster cluster : simpleGroups)
        {
            mClusters.remove(cluster);

            LOGGER.debug("cluster({}: {}) de-merged into simple SVs", cluster.id(), cluster.getDesc());

            for(SvVarData var : cluster.getSVs())
            {
                SvCluster newCluster = new SvCluster(mState.getNextClusterId());
                newCluster.addVariant(var);

                mDmFinder.analyseCluster(newCluster);

                setClusterResolvedState(newCluster, true);
                mClusters.add(newCluster);
            }
        }
    }

    private void setClusterResolvedState(SvCluster cluster, boolean isFinal)
    {
        SvClassification.setClusterResolvedState(cluster, isFinal,
                mState.getDelCutoffLength(), mState.getDupCutoffLength(), mConfig.ProximityDistance);
    }

    private void findChains(SvCluster cluster, boolean assembledLinksOnly)
    {
        if(!assembledLinksOnly)
            isSpecificCluster(cluster);

        int svCount = cluster.getSvCount();

        if(mConfig.ChainingSvLimit > 0 && svCount > mConfig.ChainingSvLimit)
        {
            LOGGER.debug("sample({}) skipping large cluster({}) with SV counts: unique({}) replicated({})",
                    mSampleId, cluster.id(), cluster.getSvCount(), svCount);
            return;
        }

        cluster.getChains().clear();
        mChainFinder.initialise(cluster);
        mChainFinder.formChains(assembledLinksOnly);
        mChainFinder.addChains(cluster);

        if(!assembledLinksOnly)
            mChainFinder.getDiagnostics().diagnoseChains();

        cluster.setValidAllelePloidySegmentPerc(mChainFinder.getValidAllelePloidySegmentPerc());
        mChainFinder.clear(); // release any refs to clusters and SVs
    }

    private void reportOtherFeatures()
    {
        annotateTemplatedInsertions(mClusters, mState.getChrBreakendMap());

        if(runAnnotation(mConfig.RequiredAnnotations, UNDER_CLUSTERING))
        {
            reportUnderclustering(mSampleId, mClusters, mState.getChrBreakendMap());
        }
    }

    private void reportClusterFeatures(final SvCluster cluster)
    {
        annotateChainedClusters(cluster);

        if(runAnnotation(mConfig.RequiredAnnotations, FOLDBACK_MATCHES))
        {
            findIncompleteFoldbackCandidates(mSampleId, cluster, mState.getChrBreakendMap(), mCnDataLoader);
        }

        if(runAnnotation(mConfig.RequiredAnnotations, DOUBLE_MINUTES))
            mDmFinder.reportCluster(mSampleId, cluster);
    }

    public void close()
    {
        mDmFinder.close();
        mChainFinder.getDiagnostics().close();
        mSimpleClustering.close();
    }

    public void logStats()
    {
        mPcClustering.logStats();
        mPcChaining.logStats();
        mPcAnnotation.logStats();
    }

}
