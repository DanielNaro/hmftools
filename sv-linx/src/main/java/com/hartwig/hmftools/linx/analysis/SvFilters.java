package com.hartwig.hmftools.linx.analysis;

import static java.lang.Math.abs;
import static java.lang.Math.max;

import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.BND;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.INS;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.INV;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.SGL;
import static com.hartwig.hmftools.linx.analysis.SimpleClustering.hasLowCNChangeSupport;
import static com.hartwig.hmftools.linx.types.ResolvedType.DUP_BE;
import static com.hartwig.hmftools.linx.types.ResolvedType.LOW_VAF;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_END;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_START;
import static com.hartwig.hmftools.linx.types.SvVarData.isStart;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.linx.cn.SvCNData;
import com.hartwig.hmftools.linx.types.ResolvedType;
import com.hartwig.hmftools.linx.types.SvBreakend;
import com.hartwig.hmftools.linx.types.SvCluster;
import com.hartwig.hmftools.linx.types.SvVarData;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SvFilters
{
    private Map<SvVarData,ResolvedType> mExcludedSVs; // SV and exclusion reason eg duplicate breakends

    private final ClusteringState mState;

    private static final Logger LOGGER = LogManager.getLogger(SvFilters.class);

    public SvFilters(final ClusteringState state)
    {
        mState = state;
        mExcludedSVs = Maps.newHashMap();
    }

    private static final double LOW_VAF_THRESHOLD = 0.05;
    private static final int SHORT_INV_DISTANCE = 100;
    private static final int ISOLATED_BND_DISTANCE = 5000;

    private static final int PERMITED_SGL_DUP_BE_DISTANCE = 1;
    private static final int PERMITED_DUP_BE_DISTANCE = 35;

    public void applyFilters()
    {
        filterBreakends();
        // filterInferreds();
    }

    private void filterBreakends()
    {
        mExcludedSVs.clear();

        // filter out duplicate breakends and low-CN change support INVs and BNDs
        // breakends aren't actually removed until all chromosomes have been processed so that the indices can be preserved for various tests
        Map<String,List<SvBreakend>> breakendRemovalMap = Maps.newHashMap();

        for(Map.Entry<String, List<SvBreakend>> entry : mState.getChrBreakendMap().entrySet())
        {
            final String chromosome = entry.getKey();
            List<SvBreakend> breakendList = entry.getValue();

            int breakendCount = breakendList.size();

            List<SvBreakend> removalList = breakendRemovalMap.get(chromosome);

            if(removalList == null)
            {
                removalList = Lists.newArrayList();
                breakendRemovalMap.put(chromosome, removalList);
            }

            for(int i = 0; i < breakendCount; ++i)
            {
                final SvBreakend breakend = breakendList.get(i);
                final SvVarData var = breakend.getSV();

                if(var.isInferredSgl())
                    continue;

                // first check for SGLs already marked for removal
                if(var.type() == SGL && isSingleDuplicateBreakend(breakendList.get(i).getSV()))
                {
                    mExcludedSVs.put(var, DUP_BE);
                    removalList.add(breakend);
                    continue;
                }

                if((var.type() == BND || (var.type() == SGL && !var.isInferredSgl())) && isIsolatedLowVafBnd(var))
                {
                    LOGGER.debug("SV({}) filtered low VAF isolated BND or SGL", var.id());
                    removalList.add(breakend);

                    if(var.type() == BND)
                        removeRemoteBreakend(breakend.getOtherBreakend(), breakendRemovalMap);

                    mExcludedSVs.put(var, LOW_VAF);
                    continue;
                }

                if(i >= breakendCount - 1)
                    break;

                SvBreakend nextBreakend = breakendList.get(i + 1);

                if(var.type() == INV && nextBreakend.getSV() == var && isLowVafInversion(breakend, nextBreakend))
                {
                    LOGGER.debug("SV({}) filtered low VAF / CN change INV", var.id());
                    removalList.add(breakend);
                    removalList.add(nextBreakend);
                    mExcludedSVs.put(var, LOW_VAF);
                    continue;
                }

                if(nextBreakend.getSV() == var)
                    continue;

                SvVarData nextVar = nextBreakend.getSV();

                long distance = nextBreakend.position() - breakend.position();

                if(distance > PERMITED_DUP_BE_DISTANCE || breakend.orientation() != nextBreakend.orientation())
                    continue;

                if(var.type() == SGL || nextVar.type() == SGL)
                {
                    if(distance <= PERMITED_SGL_DUP_BE_DISTANCE)
                    {
                        if(var.type() == SGL)
                        {
                            mExcludedSVs.put(var, DUP_BE);
                            removalList.add(breakend);
                        }
                        else if(nextVar.type() == SGL)
                        {
                            mExcludedSVs.put(nextVar, DUP_BE);
                            removalList.add(nextBreakend);

                        }
                    }
                }
                else if(var.type() == nextVar.type() && var.isEquivBreakend())
                {
                    // 2 non-SGL SVs may be duplicates, so check their other ends
                    SvBreakend otherBe = breakend.getOtherBreakend();
                    SvBreakend nextOtherBe = nextBreakend.getOtherBreakend();

                    if(otherBe.chromosome().equals(nextOtherBe.chromosome())
                            && abs(otherBe.position() - nextOtherBe.position()) <= PERMITED_DUP_BE_DISTANCE)
                    {
                        // remove both of the duplicates breakends now

                        // select the one with assembly if only has as them
                        if((var.getTIAssemblies(true).isEmpty() && !nextVar.getTIAssemblies(true).isEmpty())
                                || (var.getTIAssemblies(false).isEmpty() && !nextVar.getTIAssemblies(false).isEmpty()))
                        {
                            mExcludedSVs.put(var, DUP_BE);
                            removalList.add(breakend);

                            if(breakend.chromosome().equals(otherBe.chromosome()))
                            {
                                removalList.add(otherBe);
                            }
                            else
                            {
                                removeRemoteBreakend(otherBe, breakendRemovalMap);
                            }
                        }
                        else
                        {
                            mExcludedSVs.put(nextVar, DUP_BE);
                            removalList.add(nextBreakend);

                            if(nextBreakend.chromosome().equals(nextOtherBe.chromosome()))
                            {
                                removalList.add(nextOtherBe);
                            }
                            else
                            {
                                removeRemoteBreakend(nextOtherBe, breakendRemovalMap);
                            }
                        }
                    }
                }
            }
        }

        // now remove filtered breakends
        for(Map.Entry<String,List<SvBreakend>> entry : breakendRemovalMap.entrySet())
        {
            final List<SvBreakend> removalList = entry.getValue();

            if(removalList.isEmpty())
                continue;

            final List<SvBreakend> breakendList = mState.getChrBreakendMap().get(entry.getKey());
            removalList.stream().forEach(x -> breakendList.remove(x));

            // and reset indices after excluding breakends
            for (int i = 0; i < breakendList.size(); ++i)
            {
                final SvBreakend breakend = breakendList.get(i);
                breakend.setChrPosIndex(i);
            }
        }
    }

    public void clusterExcludedVariants(List<SvCluster> clusters)
    {
        for(Map.Entry<SvVarData,ResolvedType> excludedSv : mExcludedSVs.entrySet())
        {
            SvVarData var = excludedSv.getKey();
            ResolvedType exclusionReason = excludedSv.getValue();

            SvCluster newCluster = new SvCluster(mState.getNextClusterId());
            newCluster.addVariant(var);
            newCluster.setResolved(true, exclusionReason);
            clusters.add(newCluster);
        }
    }

    private boolean isIsolatedLowVafBnd(final SvVarData var)
    {
        if(!hasLowCNChangeSupport(var))
            return false;

        for(int se = SE_START; se <= SE_END; ++se)
        {
            if(se == SE_END && var.isSglBreakend())
                continue;

            final SvBreakend breakend = var.getBreakend(se);
            final List<SvBreakend> breakendList = mState.getChrBreakendMap().get(breakend.chromosome());

            int i = breakend.getChrPosIndex();

            boolean isolatedDown = (i == 0) ? true
                    : breakend.position() - breakendList.get(i - 1).position() >= ISOLATED_BND_DISTANCE;

            boolean isolatedUp = (i >= breakendList.size() - 1) ? true
                    : breakendList.get(i + 1).position() - breakend.position() >= ISOLATED_BND_DISTANCE;

            if(!isolatedDown || !isolatedUp)
                return false;
        }

        return true;
    }

    private boolean isLowVafInversion(final SvBreakend breakend, final SvBreakend nextBreakend)
    {
        final SvVarData var = breakend.getSV();

        if(nextBreakend.getSV() != var || nextBreakend.position() - breakend.position() > SHORT_INV_DISTANCE)
            return false;

        return hasLowCNChangeSupport(var) || var.calcVaf(true) < LOW_VAF_THRESHOLD;
    }

    private boolean isSingleDuplicateBreakend(final SvVarData var)
    {
        return var.type() == SGL && !var.isInferredSgl() && var.isEquivBreakend();
    }

    private void removeRemoteBreakend(final SvBreakend breakend, Map<String,List<SvBreakend>> breakendRemovalMap)
    {
        List<SvBreakend> otherList = breakendRemovalMap.get(breakend.chromosome());
        if(otherList == null)
        {
            otherList = Lists.newArrayList();
            breakendRemovalMap.put(breakend.chromosome(), otherList);

        }

        otherList.add(breakend);
    }


}
