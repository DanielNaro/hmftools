package com.hartwig.hmftools.svanalysis.analysis;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import static com.hartwig.hmftools.svanalysis.analysis.LinkFinder.MIN_TEMPLATED_INSERTION_LENGTH;
import static com.hartwig.hmftools.svanalysis.types.SvLinkedPair.ASSEMBLY_MATCH_MATCHED;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.SVI_END;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.SVI_START;
import static com.hartwig.hmftools.svanalysis.types.SvVarData.isStart;
import static com.hartwig.hmftools.svanalysis.types.SvLinkedPair.LINK_TYPE_TI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.svanalysis.types.SvBreakend;
import com.hartwig.hmftools.svanalysis.types.SvChain;
import com.hartwig.hmftools.svanalysis.types.SvCluster;
import com.hartwig.hmftools.svanalysis.types.SvVarData;
import com.hartwig.hmftools.svanalysis.types.SvLinkedPair;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/* ChainFinder - forms one or more chains from the SVs in a cluster

    Set-up:
    - form all assembled links and connect into chains
    - identify high-ploidy, foldbacks and complex DUP-type SVs, since these will be prioritised during chaining
    - create a cache of all possible linked pairs
    - create a cache of available breakends, including replicating each on accoriding to the SV's ploidy

    Replication count and ploidy
    - for clusters where all SVs have the same ploidy, none of the replication logic applies
    - in reality all SVs are integer multiples of each other according to their ploidy ratio, but in practice the ploidy min/max
    values need to be used to estimate replication

    Routine:
    - apply priority rules to find the next possible link(s)
    - add the link to an existing chain or a new chain if required
    - remove the breakends & link from further consideration
    - repeat until no further links can be made

    Priority rules:
    - Max-Replicated - find the SV(s) with the highest replication count, then select the one with the fewest possible links
    - Single-Option - if a breakend has only one possible link, select this one
    - Foldbacks - look for a breakend which can link to both ends of a foldback
    - Ploidy-Match - starting with the highest ploidy SV, only link SVs of the same ploidy
    - Resolving-SV - only link a high-ploidy SV to a lower one once all ploidy-match links are exhausted
    - Shortest - after all other rules, if there is more than 1 possible link then choose the shortest

    Rule selection
    1. Single-Option
    2. Foldbacks
    3. Ploidy-Match
    4. Max-Replicated (will possibly discard)
    5. Resolving-SV (possbly not relevant after rules 2 & 3 are exhausted)
    6. Shortest

*/

public class ChainFinder
{
    private static final Logger LOGGER = LogManager.getLogger(ChainFinder.class);

    private SvCluster mCluster;
    private boolean mHasReplication;

    // chaining state
    private List<SvLinkedPair> mAssemblyLinkedPairs;
    private List<SvLinkedPair> mChainClosingPairs;
    private List<SvVarData> mFoldbacks;
    private List<SvVarData> mComplexDupCandidates;
    private boolean mSkippedPair;

    private List<SvChain> mPartialChains;
    private int mNextChainId;
    private List<SvVarData> mUnlinkedSVs;
    private Map<SvBreakend,List<SvBreakend>> mUnlinkedBreakendMap;
    private Map<SvVarData,Integer> mSvReplicationMap;

    private Map<SvBreakend,List<SvLinkedPair>> mSvBreakendPossibleLinks;

    private int mLinkIndex;
    private boolean mIsValid;
    private boolean mLogVerbose;
    private boolean mNewMethod;

    public ChainFinder()
    {
        mNextChainId = 0;
        mLinkIndex = 0;
        mPartialChains = Lists.newArrayList();
        mFoldbacks = Lists.newArrayList();
        mComplexDupCandidates = Lists.newArrayList();
        mUnlinkedSVs = Lists.newArrayList();
        mChainClosingPairs = Lists.newArrayList();
        mUnlinkedBreakendMap = new HashMap();
        mSvReplicationMap = new HashMap();
        mSvBreakendPossibleLinks = new HashMap();
        mHasReplication = false;
        mLogVerbose = false;
        mIsValid = true;
        mSkippedPair = false;
        mNewMethod = true;
    }

    public void initialise(SvCluster cluster)
    {
        mNextChainId = 0;
        mLinkIndex = 0;
        mCluster = cluster;
        mHasReplication = mCluster.hasReplicatedSVs();
        mIsValid = true;
        mSkippedPair = false;

        mAssemblyLinkedPairs = Lists.newArrayList(cluster.getAssemblyLinkedPairs());
        mChainClosingPairs.clear();
        mFoldbacks.clear();
        mFoldbacks.addAll(mCluster.getFoldbacks());
        mComplexDupCandidates.clear();

        mPartialChains.clear();
        mUnlinkedSVs.clear();
        mUnlinkedBreakendMap.clear();
        mSvBreakendPossibleLinks.clear();
    }

    public void setLogVerbose(boolean toggle) { mLogVerbose = toggle; }
    public void setNewMethod(boolean toggle) { mNewMethod = toggle; }

    public void formClusterChains(boolean assembledLinksOnly)
    {
        List<SvVarData> svList = Lists.newArrayList(mCluster.getSVs(true));

        if(svList.size() < 2)
            return;

        mCluster.getChains().clear();

        if (mCluster.getSvCount() >= 4)
        {
            LOGGER.debug("cluster({}) assemblyLinks({}) svCount({} rep={})",
                    mCluster.id(), mAssemblyLinkedPairs.size(), mCluster.getSvCount(), mCluster.getSvCount(true));
        }

        // isSpecificCluster(mCluster);
        // mLogWorking = isSpecificCluster(mCluster);

        buildChains(assembledLinksOnly);

        // report on how effective chaining was
        if(LOGGER.isDebugEnabled())
        {
            int breakendCount = (int)mUnlinkedBreakendMap.values().stream().count();

            List<SvVarData> uniqueUnlinkedSVs = Lists.newArrayList();

            for(final SvVarData var : mUnlinkedSVs)
            {
                if(!uniqueUnlinkedSVs.contains(var.getOrigSV()))
                    uniqueUnlinkedSVs.add(var.getOrigSV());
            }

            LOGGER.debug("cluster({}) chaining finished: chains({}) unlinked SVs({} unique={}) breakends({} reps={})",
                    mCluster.id(), mPartialChains.size(), mUnlinkedSVs.size(), uniqueUnlinkedSVs.size(),
                    mUnlinkedBreakendMap.size(), breakendCount);
        }

        if(!mIsValid)
        {
            LOGGER.warn("cluster({}) chain finding failed", mCluster.id());
            return;
        }

        // add these chains to the cluster, but skip any which are identical to existing ones,
        // which can happen for clusters with replicated SVs
        mPartialChains.stream().forEach(chain -> checkAddNewChain(chain));

        for(int i = 0; i < mCluster.getChains().size(); ++i)
        {
            final SvChain chain = mCluster.getChains().get(i);

            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("cluster({}) added chain({}) with {} linked pairs:", mCluster.id(), chain.id(), chain.getLinkCount());
                chain.logLinks();
            }

            chain.setId(i); // set after logging so can compare with logging during building
        }
    }

    private void checkAddNewChain(final SvChain newChain)
    {
        if(!mHasReplication || mCluster.getChains().isEmpty())
        {
            mCluster.addChain(newChain, false);
            return;
        }

        // any identical chains will have their replicated SVs entirely removed
        for(final SvChain chain : mCluster.getChains())
        {
            if(chain.identicalChain(newChain))
            {
                boolean allReplicatedSVs = newChain.getSvCount(false) == 0;

                LOGGER.debug("cluster({}) skipping duplicate chain({}) vs origChain({}) all replicated({})",
                        mCluster.id(), newChain.id(), chain.id(), allReplicatedSVs);

                // remove these replicated SVs as well as the replicated chain
                if(allReplicatedSVs)
                {
                    for (final SvVarData var : newChain.getSvList())
                    {
                        mCluster.removeReplicatedSv(var);
                    }
                }

                return;
            }
        }

        mCluster.addChain(newChain, false);
    }

    private void buildChains(boolean assembledLinksOnly)
    {
        setUnlinkedBreakends();

        // first make chains out of any assembly links
        addAssemblyLinksToChains();

        if(assembledLinksOnly)
            return;

        setSvReplicationCounts();

        determinePossibleLinks();

        while (true)
        {
            boolean isRestrictedSet = false;
            mSkippedPair = false;

            List<SvLinkedPair> possiblePairs = findSingleOptionPairs();

            if(!possiblePairs.isEmpty())
            {
                isRestrictedSet = true;
            }
            else
            {
                if (mNewMethod && mHasReplication)
                {
                    possiblePairs = findDuplicationPairs();

                    if(possiblePairs.isEmpty())
                    {
                        possiblePairs = findPloidyMatchPairs();
                    }
                }

                if(possiblePairs.isEmpty())
                {
                    possiblePairs = findMaxReplicationPairs();
                }
            }

            if(possiblePairs.isEmpty())
            {
                if(mSkippedPair)
                    continue;

                break;
            }

            processPossiblePairs(possiblePairs, isRestrictedSet);
            checkProgress();
        }
    }

    private List<SvLinkedPair> findPloidyMatchPairs()
    {
        List<SvLinkedPair> possiblePairs = Lists.newArrayList();


        return possiblePairs;
    }

    private List<SvLinkedPair> findDuplicationPairs()
    {
        // both ends of a foldback or complex DUP connect to one end of another SV with ploidy >= 2x
        List<SvLinkedPair> possiblePairs = Lists.newArrayList();

        // are there any foldbacks where both ends should or can only connect to a single other high-ploidy breakend
        List<SvVarData> replicatingSVs = Lists.newArrayList(mFoldbacks);
        replicatingSVs.addAll(mComplexDupCandidates);

        if(replicatingSVs.isEmpty())
            return possiblePairs;

        for(SvVarData var : replicatingSVs)
        {
            double varPloidy = var.getRoundedCNChange();

            // collect up possible pairs for this foldback
            List<SvLinkedPair> varPairs = Lists.newArrayList();

            for(int be = SVI_START; be <= SVI_END; ++be)
            {
                boolean isStart = isStart(be);

                if(var.getFoldbackBreakend(isStart) != null)
                {
                    SvBreakend breakend = var.getBreakend(isStart);
                    List<SvLinkedPair> bePairs = mSvBreakendPossibleLinks.get(breakend);
                    if(bePairs != null)
                    {
                        varPairs.addAll(bePairs);
                    }
                }
            }

            // then check if any of these run into a breakend of higher ploidy, and if so, take the nearest pair set
            // eg for A - B - A, where A has double ploidy of B, and both ends of B connect to same end of A
            // in the logic below, the SV in question is the B variant
            List<SvVarData> otherSVs = Lists.newArrayList();
            for(SvLinkedPair pair : varPairs)
            {
                SvVarData otherSV = pair.getOtherSV(var); // the SV with equal or higher ploidy
                double otherVarPloidy = otherSV.getRoundedCNChange();

                if(varPloidy > otherVarPloidy) // || varPloidy * 2 > otherVarPloidy
                    continue;

                if(otherSVs.contains(otherSV))
                    continue;

                otherSVs.add(otherSV);

                // at this point select the nearer of the breakend pair and link them both to one end of the higher-ploidy SV
                int endsMatchedOnOtherVarStart = 0;
                int endsMatchedOnOtherVarEnd = 0;
                long linkOnOtherVarStartLength = 0;
                long linkOnOtherVarEndLength = 0;
                int maxLinkOnOtherVarStart = 0;
                int maxLinkOnOtherVarEnd = 0;
                List<SvLinkedPair> startLinks = Lists.newArrayList();
                List<SvLinkedPair> endLinks = Lists.newArrayList();

                for(SvLinkedPair otherPair : varPairs)
                {
                    int maxPairCount = getMaxUnlinkedPairCount(otherPair);

                    if(maxPairCount == 0)
                        continue;

                    if(otherPair.hasBreakend(otherSV, true) && getUnlinkedBreakendCount(otherSV.getBreakend(true)) > 1
                            && (otherPair.hasBreakend(var, true) || otherPair.hasBreakend(var, false)))
                    {
                        // link can be made from start of this var to both ends of the other SV, and record the shortest pair length
                        ++endsMatchedOnOtherVarStart;
                        maxLinkOnOtherVarStart = max(maxLinkOnOtherVarStart, maxPairCount);
                        startLinks.add(otherPair);

                        if (linkOnOtherVarStartLength == 0 || otherPair.length() < linkOnOtherVarStartLength)
                            linkOnOtherVarStartLength = otherPair.length();
                    }
                    else if(otherPair.hasBreakend(otherSV, false) && getUnlinkedBreakendCount(otherSV.getBreakend(false)) > 1
                            && (otherPair.hasBreakend(var, true) || otherPair.hasBreakend(var, false)))
                    {
                        ++endsMatchedOnOtherVarEnd;
                        maxLinkOnOtherVarEnd = max(maxLinkOnOtherVarEnd, maxPairCount);
                        endLinks.add(otherPair);

                        if (linkOnOtherVarEndLength == 0 || otherPair.length() < linkOnOtherVarEndLength)
                            linkOnOtherVarEndLength = otherPair.length();
                    }
                }

                if(endsMatchedOnOtherVarStart < 2 && endsMatchedOnOtherVarEnd < 2)
                    continue;

                log(LOG_LEVEL_VERBOSE, String.format("SV(%s) ploidy(%.2f) foldback dual links: start(links=%d maxLinks=%d matched=%d) start(links=%d maxLinks=%d matched=%d)",
                        var.id(), varPloidy, startLinks.size(), maxLinkOnOtherVarStart, endsMatchedOnOtherVarStart,
                        endLinks.size(), maxLinkOnOtherVarEnd, endsMatchedOnOtherVarEnd));

                // check for conflicts between this pairing and any others involving the SVs, and if found
                // go with the shortest (could change this to the highest)
                if(!possiblePairs.isEmpty())
                {
                    boolean replacePossibles = false;
                    boolean hasClashes = false;

                    for(SvLinkedPair otherPair : possiblePairs)
                    {
                        if (otherPair.hasVariant(var) || otherPair.hasVariant(otherSV))
                        {
                            hasClashes = true;
                            int shorterNewPairs = 0;

                            if (endsMatchedOnOtherVarStart == 2)
                            {
                                shorterNewPairs = (int) startLinks.stream().filter(x -> x.length() < otherPair.length()).count();
                            }

                            if (endsMatchedOnOtherVarEnd == 2)
                            {
                                shorterNewPairs += (int) endLinks.stream().filter(x -> x.length() < otherPair.length()).count();
                            }

                            if (shorterNewPairs > 0)
                            {
                                replacePossibles = true;
                                break;
                            }
                        }
                    }

                    if(replacePossibles)
                    {
                        possiblePairs.clear();
                    }
                    else if(hasClashes)
                    {
                        continue;
                    }
                }

                if(endsMatchedOnOtherVarStart == 2 && endsMatchedOnOtherVarEnd == 2)
                {
                    // prioritise potential link count followed by length
                    if(maxLinkOnOtherVarStart > maxLinkOnOtherVarEnd || linkOnOtherVarStartLength < linkOnOtherVarEndLength)
                    {
                        possiblePairs.addAll(startLinks);
                    }
                    else
                    {
                        possiblePairs.addAll(endLinks);
                    }
                }
                else if(endsMatchedOnOtherVarStart == 2)
                {
                    possiblePairs.addAll(startLinks);
                }
                else if(endsMatchedOnOtherVarEnd == 2)
                {
                    possiblePairs.addAll(endLinks);
                }
            }

            // otherwise find matching ploidy links
            if(possiblePairs.isEmpty())
            {

            }
        }

        if(!possiblePairs.isEmpty())
        {
            removeChainClosingPairs(possiblePairs);
        }

        return possiblePairs;
    }

    private List<SvLinkedPair> findSingleOptionPairs()
    {
        List<SvLinkedPair> restrictedPairs = Lists.newArrayList();

        for(Map.Entry<SvBreakend,List<SvLinkedPair>> entry : mSvBreakendPossibleLinks.entrySet())
        {
            if(entry.getValue().size() != 1)
                continue;

            SvLinkedPair newPair = entry.getValue().get(0);

            if(mChainClosingPairs.contains(newPair))
                continue;

            int minLinkCount = getMaxUnlinkedPairCount(newPair);

            // add this if it doesn't clash, and if it does then take the highest ploidy first, following by shortest
            int index = 0;
            boolean canAdd = true;
            while(index < restrictedPairs.size())
            {
                SvLinkedPair otherPair = restrictedPairs.get(index);

                if(otherPair == newPair)
                {
                    canAdd = false;
                    break;
                }

                if(!otherPair.hasLinkClash(newPair) && !otherPair.oppositeMatch(newPair))
                {
                    ++index;
                    continue;
                }

                double ploidySumNew = newPair.first().ploidyMin() + newPair.second().ploidyMin();
                double ploidySumOther = otherPair.first().ploidyMin() + otherPair.second().ploidyMin();
                int otherMinLinkCount = getMaxUnlinkedPairCount(otherPair);

                if(minLinkCount < otherMinLinkCount || ploidySumNew > ploidySumOther || newPair.length() < otherPair.length())
                {
                    restrictedPairs.remove(index);
                }
                else
                {
                    canAdd = false;
                    break;
                }
            }

            if(canAdd)
            {
                log(LOG_LEVEL_VERBOSE, String.format("single-option pair(%s) limited by breakend(%s)",
                        newPair.toString(), entry.getKey().toString()));
                restrictedPairs.add(newPair);
            }
        }

        return restrictedPairs;
    }

    private List<SvLinkedPair> findMaxReplicationPairs()
    {
        // find the best next candidate link giving priority to replication count

        // first check if there are SVs with a higher replication count, and if so favour these first
        List<SvVarData> maxRepSVs = !mSvReplicationMap.isEmpty() ? getMaxReplicationSvIds() : null;

        List<SvBreakend> breakendList = Lists.newArrayList();

        for (final SvBreakend breakend : mUnlinkedBreakendMap.keySet())
        {
            if (maxRepSVs != null && !maxRepSVs.contains(breakend.getSV()))
                continue;

            breakendList.add(breakend);
        }

        boolean isMaxReplicated = maxRepSVs != null && !maxRepSVs.isEmpty();

        if (mLogVerbose && isMaxReplicated)
        {
            for (SvVarData var : maxRepSVs)
            {
                LOGGER.debug("restricted to rep SV: {} repCount({})", var.id(), mSvReplicationMap.get(var));
            }
        }

        // next take the pairings with the least alternatives
        List<SvLinkedPair> possiblePairs = findFewestOptionPairs(breakendList, isMaxReplicated);

        removeChainClosingPairs(possiblePairs);

        if (possiblePairs.isEmpty())
        {
            if (isMaxReplicated)
            {
                // these high-replication SVs yielded no possible links so remove them from consideration
                for (final SvVarData var : maxRepSVs)
                {
                    log(LOG_LEVEL_VERBOSE, String.format("cluster(%s) removing high-replicated SV(%s %s)",
                            mCluster.id(), var.posId(), var.type()));

                    mSvReplicationMap.remove(var);
                }
            }
        }

        return possiblePairs;
    }

    private void removeChainClosingPairs(List<SvLinkedPair> possiblePairs)
    {
        if(mChainClosingPairs.isEmpty())
            return;

        int index = 0;
        while(index < possiblePairs.size())
        {
            SvLinkedPair pair = possiblePairs.get(index);

            if(mChainClosingPairs.contains(pair))
                possiblePairs.remove(index);
            else
                ++index;
        }
    }

    private List<SvLinkedPair> findFewestOptionPairs(List<SvBreakend> breakendList, boolean isRestricted)
    {
        // of these pairs, do some have less alternatives links which could be made than others
        // eg if high-rep SVs are A and B, and possible links have been found A-C, A-D and B-E,
        // then count how many links could be made between C, D and E to other SVs, and then select the least restrictive
        // say A-C is the only link C can make and B-D is the only link that D can make, then would want to make them both
        int minPairCount = 0;
        List<SvLinkedPair> minLinkPairs = Lists.newArrayList();

        for(SvBreakend breakend : breakendList)
        {
            List<SvLinkedPair> possiblePairs = mSvBreakendPossibleLinks.get(breakend);

            if (possiblePairs == null || possiblePairs.isEmpty())
                continue;

            if (minPairCount == 0 || possiblePairs.size() < minPairCount)
            {
                minLinkPairs.clear();
                minPairCount = possiblePairs.size();
            }

            if (possiblePairs.size() == minPairCount)
            {
                for (SvLinkedPair pair : possiblePairs)
                {
                    if (!minLinkPairs.contains(pair))
                        minLinkPairs.add(pair);
                }
            }

            if (isRestricted)
            {
                // also check this max-rep SV's pairings to test how they are restricted
                for (SvLinkedPair pair : possiblePairs)
                {
                    SvBreakend otherBreakend = pair.getOtherBreakend(breakend);

                    List<SvLinkedPair> otherPossiblePairs = mSvBreakendPossibleLinks.get(otherBreakend);

                    if (otherPossiblePairs == null || otherPossiblePairs.isEmpty())
                        continue; // logical assert

                    if (minPairCount == 0 || otherPossiblePairs.size() < minPairCount)
                    {
                        minLinkPairs.clear();
                        minPairCount = otherPossiblePairs.size();
                    }

                    if (otherPossiblePairs.size() == minPairCount)
                    {
                        for (SvLinkedPair otherPair : otherPossiblePairs)
                        {
                            if (!minLinkPairs.contains(otherPair))
                                minLinkPairs.add(otherPair);
                        }
                    }
                }
            }
        }

        return minLinkPairs;
    }

    private void processPossiblePairs(List<SvLinkedPair> possiblePairs, boolean isRestrictedSet)
    {
        // now the top candidates to link have been found, take the shortest of them and add this to a chain
        // where possible, add links multiple times according to the min replication of the breakends involved
        // after each link is added, check whether any breakend now has only one link option
        boolean linkAdded = false;

        while (!possiblePairs.isEmpty())
        {
            SvLinkedPair shortestPair = null;
            for (SvLinkedPair pair : possiblePairs)
            {
                log(LOG_LEVEL_VERBOSE, String.format("possible pair: %s length(%s)", pair.toString(), pair.length()));

                if (shortestPair == null || pair.length() < shortestPair.length())
                {
                    shortestPair = pair;
                }
            }

            possiblePairs.remove(shortestPair);

            // log(LOG_LEVEL_VERBOSE, String.format("shortest possible pair: %s length(%s)", shortestPair.toString(), shortestPair.length()));

            int pairRepeatCount = 1;

            if(mHasReplication)
            {
                int beStartCount = getUnlinkedBreakendCount(shortestPair.getBreakend(true));
                int beEndCount = getUnlinkedBreakendCount(shortestPair.getBreakend(false));

                if(beStartCount > 1 && beEndCount > 1)
                {
                    pairRepeatCount = min(beStartCount, beEndCount);
                }

                if(pairRepeatCount > 1)
                {
                    LOGGER.debug("repeating pair({}) {} times", shortestPair.toString(), pairRepeatCount);
                }
            }

            for(int i = 0; i < pairRepeatCount; ++i)
            {
                linkAdded |= addPairToChain(shortestPair, false);
            }

            if(!mIsValid)
                return;

            // check whether after adding a link, some SV breakends have only a single possible link
            if(!isRestrictedSet)
            {
                List<SvLinkedPair> restrictedPairs = findSingleOptionPairs();
                if(!restrictedPairs.isEmpty())
                {
                    possiblePairs = restrictedPairs;
                    isRestrictedSet = true;
                }
            }

            if(!isRestrictedSet)
            {
                // having added a new pair, remove any other conflicting pairs
                int index = 0;
                while (index < possiblePairs.size())
                {
                    SvLinkedPair pair = possiblePairs.get(index);
                    if (pair.oppositeMatch(shortestPair))
                    {
                        possiblePairs.remove(index);
                        continue;
                    }

                    if (mHasReplication)
                    {
                        if (findUnlinkedMatchingBreakend(pair.getBreakend(true)) == null
                                || findUnlinkedMatchingBreakend(pair.getBreakend(false)) == null)
                        {
                            // replicated instances exhausted
                            possiblePairs.remove(index);
                            continue;
                        }
                    }
                    else if (pair.hasLinkClash(shortestPair))
                    {
                        possiblePairs.remove(index);
                        continue;
                    }

                    ++index;
                }
            }
        }

        if(linkAdded)
        {
            mChainClosingPairs.clear(); // any chain-closing links can now be re-evaluated
        }
    }

    private static int SPEC_LINK_INDEX = -1;
    // private static int SPEC_LINK_INDEX = 26;

    private boolean addPairToChain(final SvLinkedPair pair, boolean isExact)
    {
        if(mLinkIndex == SPEC_LINK_INDEX)
        {
            LOGGER.debug("specific link index({}) pair({})", mLinkIndex, pair.toString());
        }

        // attempt to add to existing chain
        boolean addedToChain = false;
        boolean[] pairToChain = {false, false};

        SvBreakend unlinkedBeFirst = null;
        SvBreakend unlinkedBeSecond = null;
        final SvLinkedPair newPair;

        if(isExact || !mHasReplication)
        {
            newPair = pair;
        }
        else
        {
            // this pair was created from the set of possibles, but needs to make use of unlinked breakends
            unlinkedBeFirst = findUnlinkedMatchingBreakend(pair.getBreakend(true));
            unlinkedBeSecond = findUnlinkedMatchingBreakend(pair.getBreakend(false));

            if(unlinkedBeFirst == null || unlinkedBeSecond == null)
            {
                mIsValid = false;
                LOGGER.error("new pair breakendStart({} valid={}) and breakendEnd({} valid={}) no unlinked match found",
                        pair.getBreakend(true).toString(), unlinkedBeFirst != null,
                        pair.getBreakend(false).toString(), unlinkedBeSecond != null);

                return false;
            }

            newPair = new SvLinkedPair(unlinkedBeFirst.getSV(), unlinkedBeSecond.getSV(), LINK_TYPE_TI,
                    unlinkedBeFirst.usesStart(), unlinkedBeSecond.usesStart());
        }

        boolean linkClosesChain = false;

        for(SvChain chain : mPartialChains)
        {
            // test this link against each ends to the chain
            boolean addToStart = false;
            for(int be = SVI_START; be <= SVI_END; ++be)
            {
                boolean isStart = isStart(be);
                final SvVarData chainSV = chain.getChainEndSV(isStart);

                if (chain.canAddLinkedPair(newPair, isStart, true))
                {
                    addToStart = isStart;

                    if (chainSV.equals(newPair.first(), true))
                    {
                        pairToChain[SVI_START] = true;

                        if(chainSV != newPair.first())
                        {
                            // the correct SV was matched, but a different instance, so switch it for one matching the chain end
                            newPair.replaceFirst(chainSV);
                        }
                    }
                    else
                    {
                        pairToChain[SVI_END] = true;

                        if (chainSV != newPair.second())
                        {
                            newPair.replaceSecond(chainSV);
                        }
                    }
                }
            }

            if(!pairToChain[SVI_START] && !pairToChain[SVI_END])
            {
                continue;
            }

            if(pairToChain[SVI_START] && pairToChain[SVI_END])
            {
                // the link can be added to both ends, which would close the chain - so search for an alternative SV on either end
                // to keep it open while still adding the link
                boolean replacementFound = false;

                if(mHasReplication)
                {
                    for (int be = SVI_START; be <= SVI_END; ++be)
                    {
                        boolean isStart = isStart(be);

                        SvBreakend openBreakend = chain.getOpenBreakend(isStart); // this will be one of the pair breakends

                        if(openBreakend == null)
                            continue; // eg ending on a SGL

                        List<SvBreakend> possibleBreakends = mUnlinkedBreakendMap.get(openBreakend.getOrigBreakend());
                        SvVarData chainSV = chain.getChainEndSV(isStart);

                        if (possibleBreakends == null || possibleBreakends.isEmpty())
                            continue;

                        for (SvBreakend otherBreakend : possibleBreakends)
                        {
                            if (otherBreakend.getSV() != chainSV)
                            {
                                replacementFound = true;

                                if (newPair.first() == chainSV)
                                    newPair.replaceFirst(otherBreakend.getSV());
                                else
                                    newPair.replaceSecond(otherBreakend.getSV());

                                pairToChain[be] = false;
                                addToStart = !isStart;
                                break;
                            }
                        }

                        if (replacementFound)
                            break;
                    }
                }

                if(!replacementFound)
                {
                    log(LOG_LEVEL_VERBOSE, String.format("skipping linked pair(%s) would close existing chain(%d)",
                            newPair.toString(), chain.id()));

                    if(!mChainClosingPairs.contains(newPair))
                    {
                        mSkippedPair = true;
                        mChainClosingPairs.add(pair);
                    }

                    linkClosesChain = true;
                    continue;
                }
            }

            chain.addLink(newPair, addToStart);
            addedToChain = true;

            LOGGER.debug("index({}) adding linked pair({} {} len={}) to existing chain({}) {}",
                    mLinkIndex, newPair.toString(), newPair.assemblyInferredStr(), newPair.length(), chain.id(), addToStart ? "start" : "end");
            break;
        }

        if(!addedToChain)
        {
            if(linkClosesChain)
                return false; // skip this link for now

            SvChain chain = new SvChain(mNextChainId++);
            mPartialChains.add(chain);
            chain.addLink(newPair, true);
            pairToChain[SVI_START] = true;
            pairToChain[SVI_END] = true;

            LOGGER.debug("index({}) adding linked pair({} {}) to new chain({})",
                    mLinkIndex, newPair.toString(), newPair.assemblyInferredStr(), chain.id());
        }

        registerNewLink(newPair, pairToChain);
        ++mLinkIndex;

        if(addedToChain)
        {
            // now see if any partial chains can be linked
            reconcileChains();
        }

        return true;
    }

    private SvBreakend findUnlinkedMatchingBreakend(final SvBreakend breakend)
    {
        // get the next available breakend (thereby reducing the replicated instances)
        final List<SvBreakend> breakendList = mUnlinkedBreakendMap.get(breakend.getOrigBreakend());

        if(breakendList == null || breakendList.isEmpty())
            return null;

        return breakendList.get(0);
    }

    private int getUnlinkedBreakendCount(final SvBreakend breakend)
    {
        List<SvBreakend> beList = mUnlinkedBreakendMap.get(breakend);
        return beList != null ? beList.size() : 0;
    }

    private int getMaxUnlinkedPairCount(final SvLinkedPair pair)
    {
        int first = getUnlinkedBreakendCount(pair.getBreakend(true));
        int second = getUnlinkedBreakendCount(pair.getBreakend(false));
        return min(first, second);
    }

    private void addAssemblyLinksToChains()
    {
        if(mAssemblyLinkedPairs.isEmpty())
            return;

        for(SvLinkedPair pair : mAssemblyLinkedPairs)
        {
            addPairToChain(pair, true);
        }

        if(!mPartialChains.isEmpty())
        {
            LOGGER.debug("created {} partial chains from {} assembly links", mPartialChains.size(), mAssemblyLinkedPairs.size());
        }
    }

    private void registerNewLink(final SvLinkedPair newPair, boolean[] pairToChain)
    {
        for (int be = SVI_START; be <= SVI_END; ++be)
        {
            boolean isStart = isStart(be);

            final SvBreakend breakend = newPair.getBreakend(isStart);

            mUnlinkedSVs.remove(breakend.getSV());

            final SvBreakend origBreakend = breakend.getOrigBreakend();

            final List<SvBreakend> breakendList = mUnlinkedBreakendMap.get(origBreakend);

            if(breakendList == null || breakendList.isEmpty())
            {
                LOGGER.error("breakend({}) list already empty", origBreakend.toString());
                mIsValid = false;
                return;
            }

            breakendList.remove(breakend);

            boolean hasUnlinkedBreakend = true;
            if(breakendList.isEmpty())
            {
                mUnlinkedBreakendMap.remove(origBreakend);
                hasUnlinkedBreakend = false;

                // LOGGER.debug("breakend({}) has no more possible links", breakend);

                SvVarData origSV = origBreakend.getSV();

                if(origSV.isFoldback() && mUnlinkedBreakendMap.get(origSV.getBreakend(!breakend.usesStart())) == null)
                {
                    // remove if not instances of this SV remain
                    mFoldbacks.remove(origSV);
                }
            }

            List<SvLinkedPair> possibleLinks = !mSvBreakendPossibleLinks.isEmpty() ? mSvBreakendPossibleLinks.get(origBreakend) : null;

            if(possibleLinks != null)
            {
                if (!hasUnlinkedBreakend)
                {
                    removePossibleLinks(possibleLinks, breakend);
                }
            }

            // check for an opposite pairing between these 2 SVs - need to look into other breakends' lists
            final SvBreakend otherOrigBreakend = breakend.getOrigSV().getBreakend(!breakend.usesStart());

            possibleLinks = otherOrigBreakend != null && !mSvBreakendPossibleLinks.isEmpty() ? mSvBreakendPossibleLinks.get(otherOrigBreakend) : null;

            if(possibleLinks != null)
            {
                final SvBreakend otherOrigBreakendAlt = newPair.first() ==  breakend.getSV() ?
                        newPair.second().getOrigSV().getBreakend(!newPair.secondLinkOnStart()) :
                        newPair.first().getOrigSV().getBreakend(!newPair.firstLinkOnStart());

                if(otherOrigBreakendAlt != null)
                {
                    for (SvLinkedPair pair : possibleLinks)
                    {
                        if (pair.hasBreakend(otherOrigBreakend) && pair.hasBreakend(otherOrigBreakendAlt))
                        {
                            possibleLinks.remove(pair);
                            break;
                        }
                    }
                }
            }

            if(mHasReplication)
            {
                // reduce replication counts for breakends which are added to a chain
                if (pairToChain[be])
                {
                    Integer replicationCount = mSvReplicationMap.get(breakend.getOrigSV());

                    if (replicationCount != null)
                    {
                        if (replicationCount <= 2)
                            mSvReplicationMap.remove(breakend.getOrigSV());
                        else
                            mSvReplicationMap.put(breakend.getOrigSV(), replicationCount - 1);
                    }
                }
            }
        }
    }

    private void removePossibleLinks(List<SvLinkedPair> possibleLinks, SvBreakend fullyLinkedBreakend)
    {
        if(possibleLinks == null || possibleLinks.isEmpty())
            return;

        final SvVarData linkedSV = fullyLinkedBreakend.getOrigSV();
        final SvBreakend origBreakend = fullyLinkedBreakend.getOrigBreakend();

        int index = 0;
        while (index < possibleLinks.size())
        {
            SvLinkedPair possibleLink = possibleLinks.get(index);
            if (possibleLink.hasBreakend(linkedSV, fullyLinkedBreakend.usesStart()))
            {
                // remove this from consideration
                possibleLinks.remove(index);

                SvBreakend otherBreakend = possibleLink.getBreakend(true) == origBreakend ?
                        possibleLink.getBreakend(false) : possibleLink.getBreakend(true);

                // and remove the pair which was cached in the other breakend's possibles list
                List<SvLinkedPair> otherPossibles = mSvBreakendPossibleLinks.get(otherBreakend);

                if(otherPossibles != null)
                {
                    for (SvLinkedPair otherPair : otherPossibles)
                    {
                        if (otherPair == possibleLink)
                        {
                            otherPossibles.remove(otherPair);

                            if (otherPossibles.isEmpty())
                            {
                                // LOGGER.debug("breakend({}) has no more possible links", otherBreakend);

                                mSvBreakendPossibleLinks.remove(otherBreakend);
                            }

                            break;
                        }
                    }
                }
            }
            else
            {
                ++index;
            }
        }

        if(possibleLinks.isEmpty())
        {
            //LOGGER.debug("breakend({}) has no more possible links", origBreakend);

            mSvBreakendPossibleLinks.remove(origBreakend);
        }
    }

    private void determinePossibleLinks()
    {
        // form a map of each breakend to its set of all other breakends which can form a valid TI
        // need to exclude breakends which are already assigned to an assembled TI
        // unless replication permits additional instances of it
        // add them in such a way that the nearest ones are first

        final Map<String,List<SvBreakend>> chrBreakendMap = mCluster.getChrBreakendMap();

        // a map of potential complex DUPs to the number of breakends with counts:
        // # breakends where maxPloidy(NearestFacingBE) > 2 x minPloidy(BE)
        // # breakends where maxPloidy(BE) < minPloidy(NearestFacingBE)
        Map<SvVarData,int[]> candidateComplexDups = new HashMap();

        for (final Map.Entry<String, List<SvBreakend>> entry : chrBreakendMap.entrySet())
        {
            final List<SvBreakend> breakendList = entry.getValue();

            for (int i = 0; i < breakendList.size() -1; ++i)
            {
                final SvBreakend lowerBreakend = breakendList.get(i);

                if(lowerBreakend.orientation() != -1)
                    continue;

                if(alreadyLinkedBreakend(lowerBreakend))
                    continue;

                int skippedNonAssembledIndex = -1;

                for (int j = i+1; j < breakendList.size(); ++j)
                {
                    final SvBreakend upperBreakend = breakendList.get(j);

                    if(skippedNonAssembledIndex == -1 && upperBreakend.getSV().getAssemblyMatchType(upperBreakend.usesStart()) != ASSEMBLY_MATCH_MATCHED)
                        skippedNonAssembledIndex = j;

                    if(upperBreakend.orientation() != 1)
                        continue;

                    if(upperBreakend.getSV() == lowerBreakend.getSV())
                        continue;

                    if(alreadyLinkedBreakend(upperBreakend))
                        continue;

                    if(abs(upperBreakend.position() - lowerBreakend.position()) < MIN_TEMPLATED_INSERTION_LENGTH)
                        continue;

                    // record the possible link
                    final SvVarData lowerSV = lowerBreakend.getOrigSV();
                    final SvVarData upperSV = upperBreakend.getOrigSV();

                    SvLinkedPair newPair = new SvLinkedPair(lowerSV, upperSV, LINK_TYPE_TI,
                            lowerBreakend.usesStart(), upperBreakend.usesStart());

                    List<SvLinkedPair> lowerPairs = mSvBreakendPossibleLinks.get(lowerBreakend);

                    if(lowerPairs == null)
                    {
                        lowerPairs = Lists.newArrayList();
                        mSvBreakendPossibleLinks.put(lowerBreakend, lowerPairs);
                    }

                    lowerPairs.add(newPair);

                    List<SvLinkedPair> upperPairs = mSvBreakendPossibleLinks.get(upperBreakend);

                    if(upperPairs == null)
                    {
                        upperPairs = Lists.newArrayList();
                        mSvBreakendPossibleLinks.put(upperBreakend, upperPairs);
                    }

                    upperPairs.add(0, newPair); // add to front since always nearer than the one prior

                    if(skippedNonAssembledIndex == -1 || skippedNonAssembledIndex == j)
                    {
                        // make note of any breakends which run into a high-ploidy SV at their first opposing breakend
                        if (!lowerBreakend.getSV().isFoldback())
                        {
                            testComplexDupConditions(candidateComplexDups, lowerBreakend, upperBreakend);
                        }

                        if (!upperBreakend.getSV().isFoldback())
                        {
                            testComplexDupConditions(candidateComplexDups, upperBreakend, lowerBreakend);
                        }
                    }
                }
            }
        }

        for(Map.Entry<SvVarData,int[]> entry : candidateComplexDups.entrySet())
        {
            SvVarData var = entry.getKey();
            int[] ploidyCounts = entry.getValue();

            if(ploidyCounts[0] == 2 && ploidyCounts[1] >= 1)
            {
                LOGGER.debug("identified potential complex dup({} {})", var.posId(), var.type());
                mComplexDupCandidates.add(var);
            }
        }
    }

    private boolean testComplexDupConditions(Map<SvVarData,int[]> complexDupsMap, SvBreakend breakend, SvBreakend higherPloidyBreakend)
    {
        if(breakend.getSV().ploidyMin() * 2 > higherPloidyBreakend.getSV().ploidyMax())
            return false;

        int[] ploidyCounts = complexDupsMap.get(breakend.getSV());
        if(ploidyCounts == null)
        {
            ploidyCounts = new int[2];
            complexDupsMap.put(breakend.getSV(), ploidyCounts);
        }

        ++ploidyCounts[0];

        if(breakend.getSV().ploidyMax() < higherPloidyBreakend.getSV().ploidyMin())
            ++ploidyCounts[1];

        return true;
    }

    private boolean alreadyLinkedBreakend(final SvBreakend breakend)
    {
        Integer beRepCount = null;
        int beRepCountRemainder = 0;

        for(SvLinkedPair pair : mAssemblyLinkedPairs)
        {
            if(pair.hasBreakend(breakend, true))
            {
                // check whether replication would still allow this breakend to be used again
                if(beRepCount == null)
                {
                    beRepCount = mSvReplicationMap.get(breakend.getOrigSV());

                    if (beRepCount == null)
                        return true;

                    beRepCountRemainder = beRepCount;
                }

                // each time this breakend is connected to another breakend via assembly,
                // it subtracts from it potential usage for inferred links
                final SvBreakend otherBreakend = pair.getOtherBreakend(breakend);
                Integer otherBeRepCount = mSvReplicationMap.get(otherBreakend.getOrigSV());

                if(otherBeRepCount == null)
                    --beRepCountRemainder;
                else
                    beRepCountRemainder -= otherBeRepCount;

                if(beRepCountRemainder <= 0)
                    return true;
            }
        }

        return false;
    }

    private void setSvReplicationCounts()
    {
        mSvReplicationMap.clear();

        if(!mHasReplication)
            return;

        for(final SvVarData var : mCluster.getSVs())
        {
            if(var.getReplicatedCount() > 0)
            {
                mSvReplicationMap.put(var, var.getReplicatedCount());
            }
        }
    }

    private void setUnlinkedBreakends()
    {
        // make a cache of all unchained breakends in those of replicated SVs
        for(final SvVarData var : mCluster.getSVs(true))
        {
            for (int be = SVI_START; be <= SVI_END; ++be)
            {
                boolean isStart = isStart(be);

                if (var.isNullBreakend() && !isStart)
                    continue;

                final SvBreakend breakend = var.getBreakend(isStart);
                final SvBreakend origBreakend = breakend.getOrigBreakend();

                List<SvBreakend> breakends = mUnlinkedBreakendMap.get(origBreakend);

                if(breakends == null)
                {
                    breakends = Lists.newArrayList();
                    mUnlinkedBreakendMap.put(origBreakend, breakends);
                }

                breakends.add(breakend);
            }
        }

        mUnlinkedSVs.addAll(mCluster.getSVs(true));
    }

    private List<SvVarData> getMaxReplicationSvIds()
    {
        List<SvVarData> maxRepIds = Lists.newArrayList();
        int maxRepCount = 1;

        for(Map.Entry<SvVarData,Integer> entry : mSvReplicationMap.entrySet())
        {
            int repCount = entry.getValue();

            if(repCount <= 1)
                continue;

            if(repCount > maxRepCount)
            {
                maxRepCount = repCount;
                maxRepIds.clear();
                maxRepIds.add(entry.getKey());
            }
            else if(repCount == maxRepCount)
            {
                if(!maxRepIds.contains(entry.getKey()))
                    maxRepIds.add(entry.getKey());
            }
        }

        return maxRepIds;
    }

    private void reconcileChains()
    {
        int index1 = 0;
        while(index1 < mPartialChains.size())
        {
            SvChain chain1 = mPartialChains.get(index1);

            boolean chainsMerged = false;

            for (int index2 = index1 + 1; index2 < mPartialChains.size(); ++index2)
            {
                SvChain chain2 = mPartialChains.get(index2);

                for (int be1 = SVI_START; be1 <= SVI_END; ++be1)
                {
                    boolean c1Start = isStart(be1);

                    for (int be2 = SVI_START; be2 <= SVI_END; ++be2)
                    {
                        boolean c2Start = isStart(be2);

                        if (chain1.canAddLinkedPair(chain2.getLinkedPair(c2Start), c1Start, false))
                        {
                            LOGGER.debug("merging chain({} links={}) with chain({} links={})",
                                    chain1.id(), chain1.getLinkCount(), chain2.id(), chain2.getLinkCount());

                            if(c2Start)
                            {
                                // merge chains and remove the latter
                                for (SvLinkedPair linkedPair : chain2.getLinkedPairs())
                                {
                                    chain1.addLink(linkedPair, c1Start);
                                }
                            }
                            else
                            {
                                // add in reverse
                                for (int index = chain2.getLinkedPairs().size() - 1; index >= 0; --index)
                                {
                                    SvLinkedPair linkedPair = chain2.getLinkedPairs().get(index);
                                    chain1.addLink(linkedPair, c1Start);
                                }
                            }

                            mPartialChains.remove(index2);

                            chainsMerged = true;
                            break;
                        }

                    }

                    if (chainsMerged)
                        break;
                }

                if (chainsMerged)
                    break;
            }

            if (!chainsMerged)
            {
                ++index1;
            }
        }
    }

    private static int LOG_LEVEL_ERROR = 0;
    private static int LOG_LEVEL_INFO = 1;
    private static int LOG_LEVEL_DEBUG = 2;
    private static int LOG_LEVEL_VERBOSE = 3;

    private void log(int level, final String message)
    {
        if(level >= LOG_LEVEL_VERBOSE && !mLogVerbose)
            return;

        if(level >= LOG_LEVEL_DEBUG && !LOGGER.isDebugEnabled())
            return;

        LOGGER.debug(message);
    }

    private void checkProgress()
    {
        if(!LOGGER.isDebugEnabled())
            return;

        if(!mHasReplication || mCluster.getSvCount() < 100)
            return;

        if((mPartialChains.size() % 100) == 0 || (mUnlinkedBreakendMap.size() % 100) == 0)
        {
            LOGGER.debug("cluster({}) progress: SVs({}) partialChains({}) unlinked(SVs={} breakends={}) replicatedSVs({})",
                    mCluster.id(), mCluster.getSvCount(), mPartialChains.size(), mUnlinkedSVs.size(),
                    mUnlinkedBreakendMap.size(), mSvReplicationMap.size());
        }
    }

}
