package com.hartwig.hmftools.linx.utils;

import static com.hartwig.hmftools.common.purple.gender.Gender.MALE;
import static com.hartwig.hmftools.linx.analysis.ClusteringPrep.populateChromosomeBreakendMap;
import static com.hartwig.hmftools.linx.analysis.SvSampleAnalyser.setSvCopyNumberData;
import static com.hartwig.hmftools.linx.types.SvConstants.DEFAULT_PROXIMITY_DISTANCE;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache;
import com.hartwig.hmftools.linx.LinxConfig;
import com.hartwig.hmftools.linx.analysis.ClusterAnalyser;
import com.hartwig.hmftools.linx.annotators.LineElementAnnotator;
import com.hartwig.hmftools.linx.cn.CnDataLoader;
import com.hartwig.hmftools.linx.cn.CnSegmentBuilder;
import com.hartwig.hmftools.linx.cn.LohEvent;
import com.hartwig.hmftools.linx.fusion.FusionDisruptionAnalyser;
import com.hartwig.hmftools.linx.types.SvBreakend;
import com.hartwig.hmftools.linx.types.SvCluster;
import com.hartwig.hmftools.linx.types.SvVarData;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

public class LinxTester
{
    public String SampleId;
    public List<SvVarData> AllVariants;
    public LinxConfig Config;
    public ClusterAnalyser Analyser;
    public CnDataLoader CnDataLoader;
    public LineElementAnnotator LineAnnotator;
    public FusionDisruptionAnalyser FusionAnalyser;

    private CnSegmentBuilder mCnSegmentBuilder;
    private int mNextVarId;

    private static final Logger LOGGER = LogManager.getLogger(LinxTester.class);

    public LinxTester()
    {
        Config = new LinxConfig(DEFAULT_PROXIMITY_DISTANCE);

        Analyser = new ClusterAnalyser(Config);
        CnDataLoader = new CnDataLoader( "", null);
        Analyser.setCnDataLoader(CnDataLoader);

        LineAnnotator = new LineElementAnnotator();
        Analyser.setLineAnnotator(LineAnnotator);

        Analyser.setRunValidationChecks(true);

        FusionAnalyser = null;

        SampleId = "TEST";
        AllVariants = Lists.newArrayList();

        Analyser.setSampleData(SampleId, AllVariants);
        mNextVarId = 0;

        mCnSegmentBuilder = new CnSegmentBuilder();

        // Configurator.setRootLevel(Level.DEBUG);
    }

    public void initialiseFusions(EnsemblDataCache geneTransCache)
    {
        FusionAnalyser = new FusionDisruptionAnalyser(null, Config, geneTransCache, null);
        FusionAnalyser.setHasValidConfigData(true);
    }

    public final int nextVarId() { return mNextVarId++; }
    public void logVerbose(boolean toggle)
    {
        Config.LogVerbose = toggle;

        if(toggle)
            Configurator.setRootLevel(Level.TRACE);

        Analyser.getChainFinder().setLogVerbose(toggle);
        Analyser.getLinkFinder().setLogVerbose(toggle);
    }

    public void addAndCluster(SvVarData var1, SvVarData var2)
    {
        clearClustersAndSVs();
        AllVariants.add(var1);
        AllVariants.add(var2);
        preClusteringInit();
        Analyser.clusterAndAnalyse();
    }

    public void preClusteringInit()
    {
        preClusteringInit(false);
    }

    public void preClusteringInit(boolean includePloidyCalcs)
    {
        // have to manually trigger breakend map creation since the CN data creation uses it
        Analyser.getState().reset();
        populateChromosomeBreakendMap(AllVariants, Analyser.getState());

        populateCopyNumberData(includePloidyCalcs);

        Analyser.setSampleData(SampleId, AllVariants);
        Analyser.preClusteringPreparation();
    }

    public void addLohEvent(final SvBreakend breakend1, final SvBreakend breakend2)
    {
        if(breakend1.orientation() != 1 || breakend2.orientation() != -1 || !breakend1.chromosome().equals(breakend2.chromosome()))
            return;

        LohEvent lohEvent = new LohEvent(breakend1.chromosome(), breakend1.position(), breakend2.position(),
                breakend1.getSV().typeStr(), breakend2.getSV().typeStr(), 1, breakend1.getSV().id(), breakend2.getSV().id());

        CnDataLoader.getLohData().add(lohEvent);
    }

    public void addClusterAndSVs(final SvCluster cluster)
    {
        Analyser.getClusters().add(cluster);
        AllVariants.addAll(cluster.getSVs());
    }

    public void clearClustersAndSVs()
    {
        // in case SVs are to be used again and re-clustered
        for(SvVarData var : AllVariants)
        {
            var.setCluster(null);
        }

        AllVariants.clear();
        Analyser.getState().reset();
        Analyser.getClusters().clear();
    }

    public final List<SvCluster> getClusters() { return Analyser.getClusters(); }

    public boolean hasClusterWithSVs(final List<SvVarData> svList)
    {
        return findClusterWithSVs(svList) != null;
    }

    public final SvCluster findClusterWithSVs(final List<SvVarData> svList)
    {
        for(final SvCluster cluster : Analyser.getClusters())
        {
            if(cluster.getSvCount() != svList.size())
                continue;

            boolean hasAll = true;

            for(final SvVarData var : svList)
            {
                if(!cluster.getSVs().contains(var))
                {
                    hasAll = false;
                    break;
                }
            }

            if(hasAll)
                return cluster;
        }

        return null;
    }

    public void setNonClusterAllelePloidies(double otherAllele, double undisruptedAllele)
    {
        mCnSegmentBuilder.setAllelePloidies(otherAllele, undisruptedAllele);
    }

    public void populateCopyNumberData(boolean includePloidyCalcs)
    {
        mCnSegmentBuilder.createCopyNumberData(CnDataLoader, Analyser.getState().getChrBreakendMap());

        if(includePloidyCalcs)
            CnDataLoader.calculateAdjustedPloidy(SampleId);

        mCnSegmentBuilder.setSamplePurity(CnDataLoader, 1, 2, MALE);

        CnDataLoader.createChrCopyNumberMap();

        setSvCopyNumberData(
                AllVariants,
                CnDataLoader.getSvPloidyCalcMap(),
                CnDataLoader.getSvIdCnDataMap(),
                CnDataLoader.getChrCnDataMap());
    }

}
