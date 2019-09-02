package com.hartwig.hmftools.linx.chaining;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;

import static com.hartwig.hmftools.linx.analysis.SvUtilities.copyNumbersEqual;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.formatPloidy;
import static com.hartwig.hmftools.linx.chaining.ChainFinder.MIN_CHAINING_PLOIDY_LEVEL;
import static com.hartwig.hmftools.linx.chaining.ChainPloidyLimits.UNCERTAINTY_SCALE_FACTOR;
import static com.hartwig.hmftools.linx.chaining.ChainPloidyLimits.calcPloidyUncertainty;
import static com.hartwig.hmftools.linx.chaining.ChainPloidyLimits.ploidyMatch;
import static com.hartwig.hmftools.linx.chaining.ChainingRule.ASSEMBLY;
import static com.hartwig.hmftools.linx.chaining.ChainingRule.FOLDBACK_SPLIT;
import static com.hartwig.hmftools.linx.chaining.LinkSkipType.CLOSING;
import static com.hartwig.hmftools.linx.chaining.LinkSkipType.PLOIDY_MISMATCH;
import static com.hartwig.hmftools.linx.chaining.SvChain.reconcileChains;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_END;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_PAIR;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_START;
import static com.hartwig.hmftools.linx.types.SvVarData.isStart;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.linx.cn.PloidyCalcData;
import com.hartwig.hmftools.linx.types.SvBreakend;
import com.hartwig.hmftools.linx.types.SvLinkedPair;
import com.hartwig.hmftools.linx.types.SvVarData;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChainLinkAllocator
{
    private int mClusterId;

    private final Map<SvLinkedPair,LinkSkipType> mSkippedPairs;
    private int mLinkIndex; // incrementing value for each link added to any chain
    private boolean mIsValid;
    private boolean mPairSkipped; // keep track of any excluded pair or SV without exiting the chaining routine
    private boolean mChainsSplit;
    private final List<SvLinkedPair> mUniquePairs; // cache of unique pairs added through chaining
    private int mNextChainId;

    // chaining state for each SV
    private final Map<SvVarData, SvChainState> mSvConnectionsMap;
    private final List<SvChainState> mSvCompletedConnections; // fully exhausted SVs are moved into this collection

    // references
    private final ChainPloidyLimits mPloidyLimits;
    private final List<SvChain> mChains;
    private final Map<SvBreakend, List<SvLinkedPair>> mSvBreakendPossibleLinks;
    private final List<SvVarData> mDoubleMinuteSVs;

    private static final Logger LOGGER = LogManager.getLogger(ChainLinkAllocator.class);

    public ChainLinkAllocator(
            final ChainPloidyLimits ploidyLimits,
            final Map<SvBreakend, List<SvLinkedPair>> svBreakendPossibleLinks,
            final List<SvChain> chains,
            final List<SvVarData> doubleMinuteSVs)
    {
        mPloidyLimits = ploidyLimits;
        mSvBreakendPossibleLinks = svBreakendPossibleLinks;
        mChains = chains;
        mDoubleMinuteSVs = doubleMinuteSVs;

        mSvConnectionsMap = Maps.newHashMap();
        mSvCompletedConnections = Lists.newArrayList();
        mUniquePairs = Lists.newArrayList();
        mSkippedPairs = Maps.newHashMap();
        mIsValid = true;
        mNextChainId = 0;
    }

    public final Map<SvVarData, SvChainState> getSvConnectionsMap() { return mSvConnectionsMap; }
    public final List<SvChainState> getSvCompletedConnections() { return mSvCompletedConnections; }

    public final List<SvLinkedPair> getUniquePairs() { return mUniquePairs; }

    public int getNextChainId() { return mNextChainId; }
    public int getLinkIndex() { return mLinkIndex; }

    public boolean isValid() { return mIsValid; }

    public boolean pairSkipped() { return mPairSkipped; }

    public void clearSkippedState()
    {
        mPairSkipped = false;
        mChainsSplit = false;
    }

    public void initialise(int clusterId)
    {
        mClusterId = clusterId;

        mIsValid = true;
        mLinkIndex = 0;
        mPairSkipped = false;
        mChainsSplit = false;
        mNextChainId = 0;

        mUniquePairs.clear();
        mSkippedPairs.clear();
        mSvConnectionsMap.clear();
        mSvCompletedConnections.clear();
    }

    public static boolean belowPloidyThreshold(final SvVarData var)
    {
        return var.ploidy() <= MIN_CHAINING_PLOIDY_LEVEL;
    }

    public void populateSvPloidyMap(final List<SvVarData> svList, boolean clusterHasReplication)
    {
        // make a cache of all unchained breakends in those of replicated SVs

        Double uniformClusterPloidy = null;

        if(!clusterHasReplication)
        {
            uniformClusterPloidy = svList.stream().mapToDouble(x-> x.ploidy()).average().getAsDouble();
        }

        for(final SvVarData var : svList)
        {
            if(belowPloidyThreshold(var))
            {
                // for now skip these
                LOGGER.debug("cluster({}) skipping SV({}) with low ploidy({} min={} max={})",
                        mClusterId, var.id(), formatPloidy(var.ploidy()), formatPloidy(var.ploidyMin()), formatPloidy(var.ploidyMax()));
                continue;
            }

            mSvConnectionsMap.put(var, new SvChainState(var, uniformClusterPloidy));
        }
    }

    protected void addAssemblyLinksToChains(final List<SvLinkedPair> assembledLinks, boolean hasReplication)
    {
        if(assembledLinks.isEmpty())
            return;

        if(!hasReplication)
        {
            for (SvLinkedPair pair : assembledLinks)
            {
                ProposedLinks proposedLink = new ProposedLinks(pair, ASSEMBLY);
                proposedLink.addBreakendPloidies(
                        pair.getBreakend(true), getUnlinkedBreakendCount(pair.getBreakend(true)),
                        pair.getBreakend(false), getUnlinkedBreakendCount(pair.getBreakend(false)));

                if(!proposedLink.isValid())
                {
                    LOGGER.debug("cluster({}) skipping assembled link({}) with low ploidy", mClusterId, proposedLink);
                    continue;
                }

                addLinks(proposedLink);
            }

            return;
        }

        // replicate any assembly links where the ploidy supports it, taking note of multiple connections between the same
        // breakend and other breakends eg if a SV has ploidy 2 and 2 different assembly links, it can only link once, whereas
        // if it has ploidy 2 and 1 link it should be made twice, and any higher combinations are unclear

        // first gather up all the breakends which have only one assembled link and record their ploidy
        List<SvBreakend> singleLinkBreakends = Lists.newArrayList();
        List<SvLinkedPair> bothMultiPairs = Lists.newArrayList();
        Map<SvBreakend,Double> breakendPloidies = Maps.newHashMap();

        // a working cache reduced as links as allocated
        List<SvLinkedPair> assemblyLinks = Lists.newArrayList(assembledLinks);

        // identify assembly links where both breakends have only 1 option, and links these immediately
        // make note of those where both breakends have multiple options
        int index = 0;
        while(index < assemblyLinks.size())
        {
            SvLinkedPair pair = assemblyLinks.get(index);
            final SvBreakend firstBreakend = pair.firstBreakend();
            final SvBreakend secondBreakend = pair.secondBreakend();

            boolean firstHasSingleConn = firstBreakend.getSV().getMaxAssembledBreakend() <= 1;
            boolean secondHasSingleConn = secondBreakend.getSV().getMaxAssembledBreakend() <= 1;

            double firstPloidy = getUnlinkedBreakendCount(firstBreakend);
            double secondPloidy = getUnlinkedBreakendCount(secondBreakend);

            if(firstPloidy == 0 || secondPloidy == 0)
            {
                LOGGER.debug("cluster({}) skipping assembled pair({}) with low ploidy({} & {})",
                        mClusterId, pair, formatPloidy(firstPloidy), formatPloidy(secondPloidy));

                assemblyLinks.remove(index);
                continue;
            }

            if(firstHasSingleConn && secondHasSingleConn)
            {
                ProposedLinks proposedLink = new ProposedLinks(pair, ASSEMBLY);
                proposedLink.addBreakendPloidies(firstBreakend, firstPloidy, secondBreakend, secondPloidy);
                addLinks(proposedLink);

                assemblyLinks.remove(index);
                continue;
            }

            ++index;

            if(firstHasSingleConn)
                singleLinkBreakends.add(firstBreakend);
            else if(secondHasSingleConn)
                singleLinkBreakends.add(secondBreakend);

            if(!firstHasSingleConn && !secondHasSingleConn)
                bothMultiPairs.add(pair);

            breakendPloidies.put(firstBreakend, firstPloidy);
            breakendPloidies.put(secondBreakend, secondPloidy);
        }

        // now process those pairs where one breakend has only one assembled link
        index = 0;
        while(index < assemblyLinks.size())
        {
            SvLinkedPair pair = assemblyLinks.get(index);

            if(!bothMultiPairs.contains(pair))
            {
                final SvBreakend firstBreakend = pair.firstBreakend();
                final SvBreakend secondBreakend = pair.secondBreakend();

                boolean firstHasSingleConn = singleLinkBreakends.contains(firstBreakend);
                boolean secondHasSingleConn = singleLinkBreakends.contains(secondBreakend);

                double firstPloidy = firstHasSingleConn ? getUnlinkedBreakendCount(firstBreakend)
                        : getMaxUnlinkedBreakendCount(firstBreakend);

                double secondPloidy = secondHasSingleConn ? getUnlinkedBreakendCount(secondBreakend)
                        : getMaxUnlinkedBreakendCount(secondBreakend);

                if(firstPloidy == 0 || secondPloidy == 0)
                {
                    LOGGER.debug("cluster({}) pair({}) assembly links already exhausted: first({}) second({})",
                            mClusterId, pair.toString(), formatPloidy(firstPloidy), formatPloidy(secondPloidy));
                    assemblyLinks.remove(index);
                    continue;
                }

                // for the breakend which has other links to make, want to avoid indicating it has been matched
                ProposedLinks proposedLink = new ProposedLinks(pair, ASSEMBLY);
                proposedLink.addBreakendPloidies(firstBreakend, firstPloidy, secondBreakend, secondPloidy);

                if(!firstHasSingleConn && proposedLink.exhaustBreakend(firstBreakend))
                {
                    proposedLink.overrideBreakendPloidyMatched(firstBreakend, false);
                }
                else if(!secondHasSingleConn && proposedLink.exhaustBreakend(secondBreakend))
                {
                    proposedLink.overrideBreakendPloidyMatched(secondBreakend, false);
                }

                LOGGER.debug("assembly multi-sgl-conn pair({}) ploidy({}): first(ploidy={} links={}) second(ploidy={} links={})",
                        pair.toString(), formatPloidy(proposedLink.ploidy()),
                        formatPloidy(proposedLink.breakendPloidy(firstBreakend)), firstBreakend.getSV().getMaxAssembledBreakend(),
                        formatPloidy(proposedLink.breakendPloidy(secondBreakend)), secondBreakend.getSV().getMaxAssembledBreakend());

                addLinks(proposedLink);
                assemblyLinks.remove(index);
                continue;
            }

            ++index;
        }

        // finally process the multi-connect options, most of which will now only have a single option left, and so the ploidy is known
        index = 0;
        boolean linkedPair = true;
        int iterations = 0;
        while(index < bothMultiPairs.size() && !bothMultiPairs.isEmpty())
        {
            ++iterations;
            final SvLinkedPair pair = bothMultiPairs.get(index);

            final SvBreakend firstBreakend = pair.firstBreakend();
            final SvBreakend secondBreakend = pair.secondBreakend();

            int firstRemainingLinks = firstBreakend.getSV().getAssembledLinkedPairs(firstBreakend.usesStart()).stream()
                    .filter(x -> bothMultiPairs.contains(x)).collect(Collectors.toList()).size();

            int secondRemainingLinks = secondBreakend.getSV().getAssembledLinkedPairs(secondBreakend.usesStart()).stream()
                    .filter(x -> bothMultiPairs.contains(x)).collect(Collectors.toList()).size();

            if(firstRemainingLinks == 0 || secondRemainingLinks == 0)
            {
                LOGGER.error("cluster({}) pair({}) unexpected remaining assembly link count: first({}) second({})",
                        mClusterId, pair.toString(), firstRemainingLinks, secondRemainingLinks);
                break;
            }

            double firstPloidy = getMaxUnlinkedBreakendCount(firstBreakend);
            double secondPloidy = getMaxUnlinkedBreakendCount(secondBreakend);

            if(firstPloidy == 0 || secondPloidy == 0)
            {
                LOGGER.debug("cluster({}) pair({}) assembly links already exhausted: first({}) second({})",
                        mClusterId, pair.toString(), formatPloidy(firstPloidy), formatPloidy(secondPloidy));
                bothMultiPairs.remove(index);
                continue;
            }

            ProposedLinks proposedLink = new ProposedLinks(pair, ASSEMBLY);

            if(firstRemainingLinks == 1 || secondRemainingLinks == 1)
            {
                proposedLink.addBreakendPloidies(firstBreakend, firstPloidy, secondBreakend, secondPloidy);
            }
            else if(!linkedPair)
            {
                proposedLink.addBreakendPloidies(
                        firstBreakend, firstPloidy/firstRemainingLinks,
                        secondBreakend, secondPloidy/secondRemainingLinks);
            }
            else
            {
                ++index;

                if(index >= bothMultiPairs.size())
                    index = 0;

                if(iterations > bothMultiPairs.size() * 3)
                {
                    LOGGER.debug("cluster({}) assembly multi-connection breakends missed", mClusterId);
                    break;
                }

                continue;
            }

            if(firstRemainingLinks > 1 && proposedLink.exhaustBreakend(firstBreakend))
            {
                proposedLink.overrideBreakendPloidyMatched(firstBreakend, false);
            }

            if(secondRemainingLinks > 1 && proposedLink.exhaustBreakend(secondBreakend))
            {
                proposedLink.overrideBreakendPloidyMatched(secondBreakend, false);
            }

            LOGGER.debug("assembly multi-conn pair({}) ploidy({}): first(ploidy={} links={}) second(ploidy={} links={})",
                    pair.toString(), formatPloidy(proposedLink.ploidy()),
                    formatPloidy(firstPloidy), firstBreakend.getSV().getMaxAssembledBreakend(),
                    formatPloidy(secondPloidy), secondBreakend.getSV().getMaxAssembledBreakend());

            addLinks(proposedLink);
            linkedPair = true;
            bothMultiPairs.remove(index);
        }

        if(!mChains.isEmpty())
        {
            LOGGER.debug("created {} partial chains from {} assembly links", mChains.size(), assembledLinks.size());
        }
    }

    public void processProposedLinks(List<ProposedLinks> proposedLinksList)
    {
        boolean linkAdded = false;

        while (!proposedLinksList.isEmpty())
        {
            ProposedLinks proposedLinks = proposedLinksList.get(0);

            // in case an earlier link has invalidated the chain
            if (proposedLinks.targetChain() != null && !mChains.contains(proposedLinks.targetChain()))
                break;

            proposedLinksList.remove(0);

            if (!proposedLinks.isValid())
            {
                LOGGER.error("cluster({}) skipping invalid proposed links: {}", mClusterId, proposedLinks.toString());
                continue;
            }

            linkAdded |= addLinks(proposedLinks);

            if (!mIsValid)
                return;

            if (proposedLinks.multiConnection()) // stop after the first complex link is made
                break;
        }

        if (linkAdded)
        {
            if(mChainsSplit)
            {
                mSkippedPairs.clear(); // any skipped links can now be re-evaluated
            }
            else
            {
                List<SvLinkedPair> pairsToRemove = mSkippedPairs.entrySet().stream()
                        .filter(x -> x.getValue() != PLOIDY_MISMATCH)
                        .map(x -> x.getKey())
                        .collect(Collectors.toList());

                pairsToRemove.stream().forEach(x -> mSkippedPairs.remove(x));
            }
        }
    }

    public boolean addLinks(final ProposedLinks proposedLinks)
    {
        // if a chain is specified, add the links to it
        // otherwise look for a chain which can link in these new pairs
        // and if none can be found, create a new chain with them

        // if no chain has a ploidy matching that of the new link and the new link is lower, then split the chain
        // if the chain has a lower ploidy, then only assign the ploidy of the chain
        // if the chain has a matching ploidy then recalculate it with the new SV's ploidy and uncertainty

        SvLinkedPair newPair = proposedLinks.Links.get(0);

        final String topRule = proposedLinks.topRule().toString();
        proposedLinks.Links.forEach(x -> x.setLinkReason(topRule, mLinkIndex));

        boolean addLinksToNewChain = true;
        boolean reconcileChains = false;

        if (proposedLinks.targetChain() != null)
        {
            addLinksToNewChain = false;
            addComplexLinksToExistingChain(proposedLinks);
            reconcileChains = true;
        }
        else if (proposedLinks.multiConnection())
        {
            // if no chain has been specified then don't search for one - this is managed by the specific rule-finder
        }
        else
        {
            SvChain targetChain = null;

            boolean pairLinkedOnFirst = false;
            boolean addToStart = false;
            boolean matchesChainPloidy = false;
            double newSvPloidy = 0;

            boolean allowChainSplits = allowDmChainSplits(proposedLinks.Links.get(0));

            boolean pairPloidyMatched = ploidyMatch(newPair.firstBreakend(), newPair.secondBreakend());

            // test every chain for whether the link would close it and look for a chain which can connect with this link

            // if one of the breakends in this new link has its other breakend in another chain and is exhausted, then force it
            // to connect to that existing chain
            // if both breakends meet this condition and the chain ploidies cannot be matched, then skip this link

            SvChain[] requiredChains = new SvChain[SE_PAIR];

            List<SvChain> allChains = Lists.newArrayList();

            for(int se = SE_START; se <= SE_END; ++se)
            {
                SvBreakend pairBreakend = newPair.getBreakend(se);

                final BreakendPloidy breakendPloidyData = getBreakendPloidyData(pairBreakend);

                if(breakendPloidyData.exhaustedInChain())
                {
                    allChains.add(breakendPloidyData.MaxPloidyChain);
                    requiredChains[se] = breakendPloidyData.MaxPloidyChain;
                    continue;
                }

                for(SvChain chain : breakendPloidyData.Chains)
                {
                    if(!allChains.contains(chain))
                        allChains.add(chain);
                }
            }

            if(requiredChains[SE_START] != null || requiredChains[SE_END] != null)
            {
                if(requiredChains[SE_START] != null && requiredChains[SE_END] != null)
                {
                    if (!ploidyMatch(
                            requiredChains[SE_START].ploidy(), requiredChains[SE_START].ploidyUncertainty(),
                            requiredChains[SE_END].ploidy(), requiredChains[SE_END].ploidyUncertainty()) && !pairPloidyMatched)
                    {
                        if(!allowChainSplits)
                        {
                            LOGGER.trace("skipping linked pair({}) with 2 required chains({} & {}) with diff ploidies",
                                    newPair.toString(), requiredChains[SE_START].id(), requiredChains[SE_END].id());
                            addSkippedPair(newPair, PLOIDY_MISMATCH);
                            return false;
                        }
                    }
                    else
                    {
                        matchesChainPloidy = true;
                    }

                    if (requiredChains[SE_START] == requiredChains[SE_END])
                    {
                        LOGGER.trace("skipping linked pair({}) would close existing chain({})",
                                newPair.toString(), requiredChains[SE_START].id());
                        addSkippedPair(newPair, CLOSING);
                        return false;
                    }
                }

                // if both breakends are in exhausted chains, take either
                targetChain = requiredChains[SE_START] != null ? requiredChains[SE_START] : requiredChains[SE_END];

                if(targetChain.getOpenBreakend(true) == newPair.firstBreakend())
                {
                    pairLinkedOnFirst = true;
                    addToStart = true;
                }
                else if(targetChain.getOpenBreakend(true) == newPair.secondBreakend())
                {
                    pairLinkedOnFirst = false;
                    addToStart = true;
                }
                else if(targetChain.getOpenBreakend(false) == newPair.firstBreakend())
                {
                    pairLinkedOnFirst = true;
                    addToStart = false;
                }
                else if(targetChain.getOpenBreakend(false) == newPair.secondBreakend())
                {
                    pairLinkedOnFirst = false;
                    addToStart = false;
                }

                double newUncertainty = pairLinkedOnFirst ? newPair.second().ploidyUncertainty() : newPair.first().ploidyUncertainty();

                // check new link matches the target chain
                if(!ploidyMatch(targetChain.ploidy(), targetChain.ploidyUncertainty(), proposedLinks.ploidy(), newUncertainty)
                && !pairPloidyMatched)
                {
                    if(!allowChainSplits)
                    {
                        LOGGER.trace("skipping targetChain({} ploidy={}) for proposedLink({}) on ploidy mismatch",
                                targetChain.id(), formatPloidy(targetChain.ploidy()), proposedLinks);

                        addSkippedPair(newPair, PLOIDY_MISMATCH);
                        return false;
                    }
                    else
                    {
                        matchesChainPloidy = false;
                        newSvPloidy = proposedLinks.ploidy();
                    }
                }
                else
                {
                    matchesChainPloidy = true;
                    newSvPloidy = targetChain.ploidy();
                }

                LOGGER.trace("pair({}) links {} breakend to chain({}) as only possible connection",
                        newPair, pairLinkedOnFirst ? "first" : "second", targetChain.id());

                if(matchesChainPloidy)
                {
                    // mark this breakend as to-be-exhausted unless its on both ends of the chain
                    for (int se = SE_START; se <= SE_END; ++se)
                    {
                        if (requiredChains[se] != null)
                        {
                            final SvChain requiredChain = requiredChains[se];
                            final SvBreakend breakend = newPair.getBreakend(se);

                            if (!(requiredChain.getOpenBreakend(true) == breakend && requiredChain.getOpenBreakend(false) == breakend))
                            {
                                proposedLinks.overrideBreakendPloidyMatched(breakend, true);
                            }
                        }
                    }
                }
            }
            else
            {
                // look for any chain which can take this link with matching ploidy
                for (SvChain chain : allChains)
                {
                    boolean[] canAddToStart = { false, false };
                    boolean linksToFirst = false;

                    boolean ploidyMatched = ploidyMatch(
                            proposedLinks.ploidy(), chain.ploidyUncertainty(), chain.ploidy(), chain.ploidyUncertainty());

                    for (int se = SE_START; se <= SE_END; ++se)
                    {
                        final SvBreakend chainBreakend = chain.getOpenBreakend(isStart(se));

                        if (chainBreakend == null)
                            continue;

                        if (chainBreakend == newPair.firstBreakend())
                            linksToFirst = true;
                        else if (chainBreakend == newPair.secondBreakend())
                            linksToFirst = false;
                        else
                            continue;

                        canAddToStart[se] = true;
                   }

                    if (!canAddToStart[SE_START] && !canAddToStart[SE_END])
                        continue;

                    boolean couldCloseChain =
                            (canAddToStart[SE_START] && canAddToStart[SE_END]) ? chain.linkWouldCloseChain(newPair) : false;

                    if (couldCloseChain)
                    {
                        LOGGER.trace("skipping linked pair({}) would close existing chain({})", newPair.toString(), chain.id());
                        addSkippedPair(newPair, CLOSING);
                        return false;
                    }

                    final SvBreakend newBreakend = linksToFirst ? newPair.secondBreakend() : newPair.firstBreakend();

                    // check whether a match was expected
                    if (!ploidyMatched)
                    {
                        if (proposedLinks.linkPloidyMatch())
                            continue;

                        if (targetChain != null && targetChain.ploidy() > chain.ploidy())
                            continue; // stick with the larger ploidy chain
                    }
                    else if (targetChain != null && matchesChainPloidy)
                    {
                        // stick with existing matched chain even if there are other equivalent options
                        continue;
                    }

                    targetChain = chain;
                    addToStart = canAddToStart[SE_START];
                    pairLinkedOnFirst = linksToFirst;

                    newSvPloidy = proposedLinks.breakendPloidy(newBreakend);

                    if (ploidyMatched)
                    {
                        matchesChainPloidy = true;
                    }
                }
            }

            // for now don't allow chains to be split, so prevent a mismatch if the chain has a higher ploidy
            if(!allowChainSplits && targetChain != null && !matchesChainPloidy && targetChain.ploidy() > proposedLinks.ploidy())
            {
                LOGGER.debug("skipping targetChain({} ploidy={}) for proposedLink({}) on ploidy mismatch",
                        targetChain.id(), formatPloidy(targetChain.ploidy()), proposedLinks);

                targetChain = null;
            }

            if (targetChain != null)
            {
                addLinksToNewChain = false;
                reconcileChains = true;
                addLinksToExistingChain(proposedLinks, targetChain, addToStart, pairLinkedOnFirst, matchesChainPloidy, newSvPloidy);
            }
        }

        if(addLinksToNewChain)
        {
            reconcileChains = addLinksToNewChain(proposedLinks);
        }

        if (reconcileChains)
        {
            // now see if any partial chains can be linked
            reconcileChains(mChains);
        }

        // moved to after chain reconciliation so can test whether breakends in open in chains or not
        registerNewLink(proposedLinks);
        ++mLinkIndex;

        return true;
    }

    private void addComplexLinksToExistingChain(final ProposedLinks proposedLinks)
    {
        // scenarios:
        // - ploidy matches - add the new link and recalculate the chain ploidy
        // - foldback or complex dup with 2-1 ploidy match - replicate the chain accordingly and halve the chain ploidy
        // - foldback or complex dup with chain greater than 2x the foldback or complex dup
        //      - split off the excess and then replicate and halve the remainder
        // - foldback where the foldback itself is a chain, connecting to a single other breakend which may also be chained
        //      - split the non-foldback chain and add both connections
        // - complex dup
        //      - around a single SV - just add the 2 new links
        //      - around a chain - split chain if > 2x ploidy, then add the 2 new links

        SvChain targetChain = proposedLinks.targetChain();
        boolean matchesChainPloidy = proposedLinks.linkPloidyMatch();
        double newSvPloidy = proposedLinks.ploidy();

        boolean requiresNewChain = !matchesChainPloidy && targetChain.ploidy() > newSvPloidy * 2;

        if (requiresNewChain)
        {
            SvChain newChain = new SvChain(mNextChainId++);
            mChains.add(newChain);

            // copy the existing links into a new chain and set to the ploidy difference
            newChain.copyFrom(targetChain);

            if (targetChain.ploidy() > newSvPloidy * 2)
            {
                // chain will have its ploidy halved a  nyway so just split off the excess
                newChain.setPloidyData(targetChain.ploidy() - newSvPloidy * 2, targetChain.ploidyUncertainty());
                targetChain.setPloidyData(newSvPloidy * 2, targetChain.ploidyUncertainty());
            }

            LOGGER.debug("new chain({}) ploidy({}) from chain({}) ploidy({}) from new SV ploidy({})",
                    newChain.id(), formatPloidy(newChain.ploidy()),
                    targetChain.id(), formatPloidy(targetChain.ploidy()), formatPloidy(newSvPloidy));
        }

        LOGGER.debug("duplicating chain({} links={} sv={}) for multi-connect {}",
                targetChain.id(), targetChain.getLinkCount(), targetChain.getSvCount(), proposedLinks.getSplittingRule());

        if (proposedLinks.getSplittingRule() == FOLDBACK_SPLIT)
        {
            final SvChain foldbackChain = proposedLinks.foldbackChain();

            if(foldbackChain != null)
            {
                targetChain.foldbackChainOnChain(foldbackChain, proposedLinks.Links.get(0), proposedLinks.Links.get(1));
                mChains.remove(foldbackChain);
            }
            else
            {
                targetChain.foldbackChainOnLink(proposedLinks.Links.get(0), proposedLinks.Links.get(1));
            }
        }
        else
        {
            targetChain.duplicateChainOnLink(proposedLinks.Links.get(0), proposedLinks.Links.get(1));
        }

        double newPloidy = targetChain.ploidy() * 0.5;
        double newUncertainty = targetChain.ploidyUncertainty() / UNCERTAINTY_SCALE_FACTOR;
        targetChain.setPloidyData(newPloidy, newUncertainty);
        mChainsSplit = true;

        for (SvLinkedPair pair : proposedLinks.Links)
        {
            LOGGER.debug("index({}) method({}) adding pair({} ploidy={}) to existing chain({}) ploidy({} unc={}) match({})",
                    mLinkIndex, proposedLinks.topRule(), pair.toString(), formatPloidy(proposedLinks.ploidy()), targetChain.id(),
                    formatPloidy(targetChain.ploidy()), formatPloidy(targetChain.ploidyUncertainty()), proposedLinks.ploidyMatchType());
        }
    }

    private void addLinksToExistingChain(final ProposedLinks proposedLinks, SvChain targetChain,
            boolean addToStart, boolean pairLinkedOnFirst, boolean matchesChainPloidy, double newSvPloidy)
    {
        // no longer allow chain splitting if higher ploidy than the proposed link
        final SvLinkedPair newPair = proposedLinks.Links.get(0);
        final SvBreakend newSvBreakend = pairLinkedOnFirst ? newPair.secondBreakend() : newPair.firstBreakend();

        if(!matchesChainPloidy && targetChain.ploidy() > proposedLinks.ploidy())
        {
            SvChain newChain = new SvChain(mNextChainId++);
            mChains.add(newChain);

            // copy the existing links into a new chain and set to the ploidy difference
            newChain.copyFrom(targetChain);

            mChainsSplit = true;

            // chain will have its ploidy halved anyway so just split off the excess
            newChain.setPloidyData(targetChain.ploidy() - newSvPloidy, targetChain.ploidyUncertainty());
            targetChain.setPloidyData(newSvPloidy, targetChain.ploidyUncertainty());

            LOGGER.debug("new chain({}) ploidy({}) from chain({}) ploidy({}) from new SV ploidy({})",
                    newChain.id(), formatPloidy(newChain.ploidy()),
                    targetChain.id(), formatPloidy(targetChain.ploidy()), formatPloidy(newSvPloidy));
        }

        PloidyCalcData ploidyData;

        if (matchesChainPloidy || targetChain.ploidy() > newSvPloidy)
        {
            if (!proposedLinks.linkPloidyMatch())
            {
                ploidyData = calcPloidyUncertainty(
                        new PloidyCalcData(proposedLinks.ploidy(), newSvBreakend.ploidyUncertainty()),
                        new PloidyCalcData(targetChain.ploidy(), targetChain.ploidyUncertainty()));
            }
            else
            {
                ploidyData = calcPloidyUncertainty(
                        new PloidyCalcData(proposedLinks.breakendPloidy(newSvBreakend), newSvBreakend.ploidyUncertainty()),
                        new PloidyCalcData(targetChain.ploidy(), targetChain.ploidyUncertainty()));
            }

            targetChain.setPloidyData(ploidyData.PloidyEstimate, ploidyData.PloidyUncertainty);
        }
        else
        {
            // ploidy of the link is higher so keep the chain ploidy unch and reduce what can be allocated from this link
            proposedLinks.setLowerPloidy(targetChain.ploidy());
        }

        targetChain.addLink(proposedLinks.Links.get(0), addToStart);

        LOGGER.debug("index({}) method({}) adding pair({} ploidy={}) to existing chain({}) ploidy({} unc={}) match({})",
                mLinkIndex, proposedLinks.topRule(), newPair.toString(), formatPloidy(proposedLinks.ploidy()), targetChain.id(),
                formatPloidy(targetChain.ploidy()), formatPloidy(targetChain.ploidyUncertainty()), proposedLinks.ploidyMatchType());
    }

    private boolean addLinksToNewChain(final ProposedLinks proposedLinks)
    {
        boolean reconcileChains = false;
        final SvLinkedPair newPair = proposedLinks.Links.get(0);

        // where more than one links is being added, they may not be able to be added to the same chain
        // eg a chained foldback replicating another breakend - the chain reconciliation step will join them back up
        SvChain newChain = null;
        for (final SvLinkedPair pair : proposedLinks.Links)
        {
            if (newChain != null)
            {
                if (newChain.canAddLinkedPairToStart(pair))
                {
                    newChain.addLink(pair, true);
                }
                else if (newChain.canAddLinkedPairToEnd(pair))
                {
                    newChain.addLink(pair, false);
                }
                else
                {
                    newChain = null;
                    reconcileChains = true;
                }
            }

            if (newChain == null)
            {
                newChain = new SvChain(mNextChainId++);
                mChains.add(newChain);

                newChain.addLink(pair, true);

                PloidyCalcData ploidyData;

                if (!proposedLinks.linkPloidyMatch() || proposedLinks.multiConnection())
                {
                    ploidyData = calcPloidyUncertainty(
                            new PloidyCalcData(proposedLinks.ploidy(), newPair.first().ploidyUncertainty()),
                            new PloidyCalcData(proposedLinks.ploidy(), newPair.second().ploidyUncertainty()));
                }
                else
                {
                    // blend the ploidies of the 2 SVs
                    ploidyData = calcPloidyUncertainty(
                            new PloidyCalcData(proposedLinks.breakendPloidy(newPair.firstBreakend()),
                                    newPair.first().ploidyUncertainty()),
                            new PloidyCalcData(proposedLinks.breakendPloidy(newPair.secondBreakend()),
                                    newPair.second().ploidyUncertainty()));
                }

                newChain.setPloidyData(ploidyData.PloidyEstimate, ploidyData.PloidyUncertainty);
            }

            LOGGER.debug("index({}) method({}) adding pair({} ploidy={}) to new chain({}) ploidy({} unc={}) match({})",
                    mLinkIndex, proposedLinks.topRule(), pair.toString(), formatPloidy(proposedLinks.ploidy()), newChain.id(),
                    formatPloidy(newChain.ploidy()), formatPloidy(newChain.ploidyUncertainty()), proposedLinks.ploidyMatchType());
        }

        return reconcileChains;
    }

    private void registerNewLink(final ProposedLinks proposedLink)
    {
        List<SvBreakend> exhaustedBreakends = Lists.newArrayList();
        boolean canUseMaxPloidy = proposedLink.topRule() == ASSEMBLY;

        for (final SvLinkedPair newPair : proposedLink.Links)
        {
            mPloidyLimits.assignLinkPloidy(newPair, proposedLink.ploidy());

            mSkippedPairs.remove(newPair);

            removeOppositeLinks(newPair);

            for (int se = SE_START; se <= SE_END; ++se)
            {
                final SvBreakend breakend = newPair.getBreakend(se);

                if (exhaustedBreakends.contains(breakend))
                    continue;

                final SvBreakend otherPairBreakend = newPair.getOtherBreakend(breakend);
                final SvVarData var = breakend.getSV();

                SvChainState svConn = mSvConnectionsMap.get(var);

                if (otherPairBreakend == null || breakend == null)
                {
                    LOGGER.error("cluster({}) invalid breakend in proposed link: {}", mClusterId, proposedLink.toString());
                    mIsValid = false;
                    return;
                }

                boolean beIsStart = breakend.usesStart();

                if (svConn == null || svConn.breakendExhaustedVsMax(beIsStart))
                {
                    LOGGER.error("breakend({}) breakend already exhausted: {} with proposedLink({})",
                            breakend.toString(), svConn != null ? svConn.toString() : "null", proposedLink.toString());
                    mIsValid = false;
                    return;
                }

                // scenarios:
                // 1. First connection:
                //  - ploidy-matched (most common scenario), exhaust the connection
                //  - not matched - just register ploidy against breakend
                // 2. Other breakend is exhausted:
                //  - if this breakend is now fully contained within chains, it also must be exhausted

                if(proposedLink.exhaustBreakend(breakend))
                {
                    svConn.set(beIsStart, svConn.Ploidy);
                }
                else if(!svConn.hasConnections())
                {
                    // first connection
                    if(proposedLink.linkPloidyMatch())
                        svConn.set(beIsStart, svConn.Ploidy);
                    else
                        svConn.add(beIsStart, proposedLink.ploidy());
                }
                else
                {
                    updateBreakendAllocatedPloidy(svConn, breakend);
                }

                boolean breakendExhausted = canUseMaxPloidy ? svConn.breakendExhaustedVsMax(beIsStart)
                        : svConn.breakendExhausted(beIsStart);

                if (breakendExhausted)
                {
                    LOGGER.trace("{} breakend exhausted: {}", beIsStart? "start" : "end", svConn.toString());
                    exhaustedBreakends.add(breakend);
                }

                svConn.addConnection(otherPairBreakend, beIsStart);
            }

            // track unique pairs to avoid conflicts (eg end-to-end and start-to-start)
            if (!matchesExistingPair(newPair))
            {
                mUniquePairs.add(newPair);
            }
        }

        // clean up breakends and SVs which have been fully allocated
        for (final SvBreakend breakend : exhaustedBreakends)
        {
            final SvVarData var = breakend.getSV();

            SvChainState svConn = mSvConnectionsMap.get(var);

            if (svConn != null)
            {
                boolean otherBreakendExhausted = canUseMaxPloidy ? svConn.breakendExhaustedVsMax(!breakend.usesStart())
                        : svConn.breakendExhausted(!breakend.usesStart());

                if (otherBreakendExhausted)
                {
                    checkSvComplete(svConn);
                }
            }

            // since this breakend has been exhausted, remove any links which depend on it
            removePossibleLinks(breakend);
        }
    }

    private void updateBreakendAllocatedPloidy(SvChainState svConn, final SvBreakend breakend)
    {
        final List<SvChain> chains = mChains.stream().filter(x -> x.getSvList().contains(breakend.getSV())).collect(Collectors.toList());

        double openChainedPloidy = 0;
        double containedChainPloidy = 0;

        for(final SvChain chain : chains)
        {
            double chainPloidy = chain.ploidy();

            if(chain.getOpenBreakend(true) == breakend)
                openChainedPloidy += chainPloidy;

            if(chain.getOpenBreakend(false) == breakend)
                openChainedPloidy += chainPloidy;

            containedChainPloidy += chain.getLinkedPairs().stream()
                    .filter(x -> x.hasBreakend(breakend))
                    .count() * chainPloidy;
        }

        boolean otherBreakendExhausted = svConn.breakendExhausted(!breakend.usesStart());
        if(openChainedPloidy == 0 && otherBreakendExhausted)
        {
            svConn.set(breakend.usesStart(), svConn.Ploidy);
        }
        else
        {
            svConn.set(breakend.usesStart(), containedChainPloidy);
        }
    }

    private void removePossibleLinks(SvBreakend breakend)
    {
        List<SvLinkedPair> possibleLinks = mSvBreakendPossibleLinks.get(breakend);

        if (possibleLinks == null)
            return;

        mSvBreakendPossibleLinks.remove(breakend);

        for(SvLinkedPair pair : possibleLinks)
        {
            final SvBreakend otherBreakend = pair.getOtherBreakend(breakend);

            List<SvLinkedPair> otherPossibles = mSvBreakendPossibleLinks.get(otherBreakend);

            if (otherPossibles == null)
                continue;

            otherPossibles.remove(pair);

            if (otherPossibles.isEmpty())
                mSvBreakendPossibleLinks.remove(otherBreakend);
        }
    }

    private void removeOppositeLinks(final SvLinkedPair pair)
    {
        // check for an opposite pairing between these 2 SVs - need to look into other breakends' lists

        for(int se = SE_START; se <= SE_END; ++se)
        {
            SvBreakend otherBreakend = pair.getBreakend(se).getOtherBreakend();

            if(otherBreakend == null)
                continue;

            List<SvLinkedPair> possibleLinks = mSvBreakendPossibleLinks.get(otherBreakend);

            if (possibleLinks == null)
                continue;

            if(possibleLinks.isEmpty())
            {
                mSvBreakendPossibleLinks.remove(otherBreakend);
                continue;
            }

            SvBreakend otherPairBreakend = pair.getBreakend(!isStart(se)).getOtherBreakend();

            if(otherPairBreakend == null)
                continue;

            for (SvLinkedPair otherPair : possibleLinks)
            {
                if (otherPair.hasBreakend(otherBreakend) && otherPair.hasBreakend(otherPairBreakend))
                {
                    possibleLinks.remove(otherPair);

                    if (possibleLinks.isEmpty())
                        mSvBreakendPossibleLinks.remove(otherBreakend);

                    break;
                }
            }
        }
    }

    private void checkSvComplete(final SvChainState svConn)
    {
        if (svConn.breakendExhausted(true) && (svConn.SV.isSglBreakend() || svConn.breakendExhausted(false)))
        {
            LOGGER.trace("SV({}) both breakends exhausted", svConn.toString());
            mSvConnectionsMap.remove(svConn.SV);
            mSvCompletedConnections.add(svConn);
        }
    }

    protected double getUnlinkedBreakendCount(final SvBreakend breakend)
    {
        SvChainState svConn = mSvConnectionsMap.get(breakend.getSV());
        if (svConn == null)
            return 0;

        return !svConn.breakendExhausted(breakend.usesStart()) ? svConn.unlinked(breakend.usesStart()) : 0;
    }

    protected double getUnlinkedBreakendCount(final SvBreakend breakend, boolean limitByChains)
    {
        if(limitByChains)
            return getBreakendPloidyData(breakend).unlinkedPloidy();
        else
            return getUnlinkedBreakendCount(breakend);
    }

    protected BreakendPloidy getBreakendPloidyData(final SvBreakend breakend)
    {
        // gather up data about how much unallocated ploidy is available for this breakend
        // and whether it is tied to any chains
        SvChainState svConn = mSvConnectionsMap.get(breakend.getSV());
        if (svConn == null)
            return new BreakendPloidy(0, true);

        double unlinkedPloidy = !svConn.breakendExhausted(breakend.usesStart()) ? svConn.unlinked(breakend.usesStart()) : 0;
        boolean otherBreakendExhausted = svConn.breakendExhausted(!breakend.usesStart());

        if(unlinkedPloidy == 0)
            return new BreakendPloidy(0, otherBreakendExhausted);

        if(!svConn.hasConnections())
            return new BreakendPloidy(unlinkedPloidy,otherBreakendExhausted);

        // if the breakend is the open end of a chain, then the chain's ploidy is a limit on the max which can assigned
        List<SvChain> chains = getChainsWithOpenBreakend(breakend);

        if(chains.isEmpty())
            return new BreakendPloidy(unlinkedPloidy, otherBreakendExhausted);

        // if a SV is fully connected to a chain on one side, then only offer up the chain ploidy
        if(chains.size() == 1 && otherBreakendExhausted)
        {
            final SvChain chain = chains.get(0);
            return new BreakendPloidy(0, chain.ploidy(), chain, chains, otherBreakendExhausted);
        }

        double totalChainPloidy = 0;
        SvChain maxChain = null;
        for(final SvChain chain : chains)
        {
            if(chain.getOpenBreakend(true) == breakend)
            {
                totalChainPloidy += chain.ploidy();

                if(maxChain == null || maxChain.ploidy() < chain.ploidy())
                    maxChain = chain;
            }

            if(chain.getOpenBreakend(false) == breakend)
            {
                totalChainPloidy += chain.ploidy();

                if(maxChain == null || maxChain.ploidy() < chain.ploidy())
                    maxChain = chain;
            }
        }

        double unchainedPloidy = max(unlinkedPloidy - totalChainPloidy, 0);

        return new BreakendPloidy(unchainedPloidy, totalChainPloidy, maxChain, chains, false);
    }

    protected List<SvChain> getChainsWithOpenBreakend(final SvBreakend breakend)
    {
        return mChains.stream()
                .filter(x -> x.getOpenBreakend(true) == breakend || x.getOpenBreakend(false) == breakend)
                .collect(Collectors.toList());
    }

    protected double getMaxUnlinkedBreakendCount(final SvBreakend breakend)
    {
        SvChainState svConn = mSvConnectionsMap.get(breakend.getSV());
        if (svConn == null)
            return 0;

        if (!svConn.breakendExhausted(breakend.usesStart()))
            return svConn.unlinked(breakend.usesStart());
        else if (!svConn.breakendExhaustedVsMax(breakend.usesStart()))
            return svConn.maxUnlinked(breakend.usesStart());
        else
            return 0;
    }

    public boolean matchesExistingPair(final SvLinkedPair pair)
    {
        for(SvLinkedPair existingPair : mUniquePairs)
        {
            if(pair.matches(existingPair))
                return true;
        }

        return false;
    }

    public boolean hasSkippedPairs(final SvLinkedPair pair)
    {
        return mSkippedPairs.keySet().stream().anyMatch(x -> x.matches(pair));
    }

    public int getSkippedPairCount(final LinkSkipType type)
    {
        return (int)mSkippedPairs.values().stream().filter(x -> x == type).count();
    }

    public final Map<SvLinkedPair, LinkSkipType> getSkippedPairs()
    {
        return mSkippedPairs;
    }

    private void addSkippedPair(final SvLinkedPair pair, LinkSkipType type)
    {
        if(hasSkippedPairs(pair))
            return;

        mPairSkipped = true;
        mSkippedPairs.put(pair, type);
    }

    public void removeSkippedPairs(final List<ProposedLinks> proposedLinks)
    {
        if(proposedLinks.isEmpty() || mSkippedPairs.isEmpty())
            return;

        int index = 0;
        while(index < proposedLinks.size())
        {
            if(proposedLinks.get(index).Links.stream().anyMatch(x -> hasSkippedPairs(x)))
            {
                proposedLinks.remove(index);
            }
            else
            {
                ++index;
            }
        }
    }

    public boolean allowDmChainSplits(final SvLinkedPair pair)
    {
        if(mDoubleMinuteSVs.isEmpty())
            return false;

        if(mDoubleMinuteSVs.contains(pair.first()) && !mDoubleMinuteSVs.contains(pair.second()))
            return true;

        if(!mDoubleMinuteSVs.contains(pair.first()) && mDoubleMinuteSVs.contains(pair.second()))
            return true;

        return false;
    }

}
