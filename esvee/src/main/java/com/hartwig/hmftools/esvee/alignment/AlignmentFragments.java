package com.hartwig.hmftools.esvee.alignment;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;

import static com.hartwig.hmftools.esvee.common.SvConstants.DEFAULT_DISCORDANT_FRAGMENT_LENGTH;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.genome.region.Orientation;
import com.hartwig.hmftools.esvee.assembly.types.JunctionAssembly;
import com.hartwig.hmftools.esvee.assembly.types.SupportRead;
import com.hartwig.hmftools.esvee.assembly.types.SupportType;

public class AlignmentFragments
{
    private final AssemblyAlignment mAssemblyAlignment;

    public AlignmentFragments(final AssemblyAlignment assemblyAlignment, final List<String> combinedSampleIds)
    {
        mAssemblyAlignment = assemblyAlignment;

        for(Breakend breakend : assemblyAlignment.breakends())
        {
            // rather than use the genome position of a read vs the aligned breakend position, use its position in the assembly
            List<BreakendSupport> sampleSupport = breakend.sampleSupport();

            combinedSampleIds.forEach(x -> sampleSupport.add(new BreakendSupport()));
        }
    }

    public void allocateBreakendSupport()
    {
        Map<String,SupportRead> mFragmentMap = Maps.newHashMap();

        Set<String> processedFragments = Sets.newHashSet();

        for(JunctionAssembly assembly : mAssemblyAlignment.assemblies())
        {
            for(SupportRead read : assembly.support())
            {
                // only check split status and fragment length from the primary pair
                // if a junction assembly were formed only from supplementaries then this would miss their support, but the expectation
                // is that the primary also forms a junction assembly and that the two of them have formed a link, so the primary's
                // support for the link will be captured
                if(read.isSupplementary())
                    continue;

                if(processedFragments.contains(read.id()))
                    continue;

                SupportRead firstRead = mFragmentMap.remove(read.id());

                if(firstRead == null)
                {
                    mFragmentMap.put(read.id(), read);
                    continue;
                }

                // only process a fragment once even if it belongs to multiple assemblies
                processedFragments.add(read.id());

                // find associated breakends
                List<ReadBreakendMatch> firstBreakendMatches = findReadBreakendMatch(firstRead);
                List<ReadBreakendMatch> secondBreakendMatches = findReadBreakendMatch(read);

                if(!firstBreakendMatches.isEmpty() && !secondBreakendMatches.isEmpty())
                {
                    processCompleteFragment(firstRead, read, firstBreakendMatches, secondBreakendMatches);
                }
                else if(firstBreakendMatches != null)
                {
                    processSoloRead(firstRead, firstBreakendMatches);
                }
                else if(secondBreakendMatches != null)
                {
                    processSoloRead(read, secondBreakendMatches);
                }
            }
        }

        // handle single reads
        for(SupportRead read : mFragmentMap.values())
        {
            List<ReadBreakendMatch> readBreakendMatches = findReadBreakendMatch(read);

            if(!readBreakendMatches.isEmpty())
                processSoloRead(read, readBreakendMatches);
        }
    }

    private void processCompleteFragment(
            final SupportRead firstRead, final SupportRead secondRead,
            final List<ReadBreakendMatch> firstBreakendMatches, final List<ReadBreakendMatch> secondBreakendMatches)
    {
        int lowerIndex = min(firstRead.fullAssemblyIndexStart(), secondRead.fullAssemblyIndexStart());
        int upperIndex = max(firstRead.fullAssemblyIndexEnd(), secondRead.fullAssemblyIndexEnd());

        int fragmentLength = upperIndex - lowerIndex + 1;
        firstRead.setInferredFragmentLength(fragmentLength);
        secondRead.setInferredFragmentLength(fragmentLength);

        int forwardReads = 0;
        int reverseReads = 0;

        if(firstRead.orientation().isForward())
            ++forwardReads;
        else
            ++reverseReads;

        if(secondRead.orientation().isForward())
            ++forwardReads;
        else
            ++reverseReads;

        Set<Breakend> breakends = Sets.newHashSet();

        // add each breakend and its pair only once to ensure they are both updated with the same split/discordant status
        addUniqueBreakends(breakends, firstBreakendMatches);
        addUniqueBreakends(breakends, secondBreakendMatches);

        for(Breakend breakend : breakends)
        {
            boolean isSplitSupport = firstBreakendMatches.stream().anyMatch(x -> x.IsSplit)
                    || secondBreakendMatches.stream().anyMatch(x -> x.IsSplit);

            breakend.updateBreakendSupport(firstRead.sampleIndex(), isSplitSupport, forwardReads, reverseReads);
            breakend.addInferredFragmentLength(fragmentLength);

            if(!breakend.isSingle())
            {
                breakend.otherBreakend().updateBreakendSupport(firstRead.sampleIndex(), isSplitSupport, forwardReads, reverseReads);
                breakend.otherBreakend().addInferredFragmentLength(fragmentLength);
            }
        }
    }

    private static void addUniqueBreakends(final Set<Breakend> breakends, final List<ReadBreakendMatch> readBreakendMatches)
    {
        for(ReadBreakendMatch breakendMatch : readBreakendMatches)
        {
            if(breakends.contains(breakendMatch.Breakend))
                continue;

            if(!breakendMatch.Breakend.isSingle() && breakends.contains(breakendMatch.Breakend.otherBreakend()))
                continue;

            breakends.add(breakendMatch.Breakend);
        }
    }

    private void processSoloRead(final SupportRead read, final List<ReadBreakendMatch> readBreakendMatches)
    {
        int forwardReads = 0;
        int reverseReads = 0;

        if(read.orientation().isForward())
            ++forwardReads;
        else
            ++reverseReads;

        Set<Breakend> breakends = Sets.newHashSet();
        addUniqueBreakends(breakends, readBreakendMatches);

        for(Breakend breakend : breakends)
        {
            boolean isSplitSupport = readBreakendMatches.stream().anyMatch(x -> x.IsSplit);

            breakend.updateBreakendSupport(read.sampleIndex(), isSplitSupport, forwardReads, reverseReads);

            if(!breakend.isSingle())
            {
                breakend.otherBreakend().updateBreakendSupport(read.sampleIndex(), isSplitSupport, forwardReads, reverseReads);
            }
        }
    }

    private class ReadBreakendMatch
    {
        public final boolean IsSplit;
        public final Breakend Breakend;

        public ReadBreakendMatch(final Breakend breakend, final boolean isSplit)
        {
            IsSplit = isSplit;
            Breakend = breakend;
        }

        public String toString() { return format("%s breakend(%s)", IsSplit ? "split" : "disc", Breakend); }
    }

    private List<ReadBreakendMatch> findReadBreakendMatch(final SupportRead read)
    {
        Breakend closestDiscordantBreakend = null;
        int closestDiscJunctionDistance = INVALID_DISCORANT_DISTANCE;

        List<ReadBreakendMatch> breakendMatches = Lists.newArrayListWithCapacity(2);

        for(Breakend breakend : mAssemblyAlignment.breakends())
        {
            if(readSpansJunction(breakend, read))
            {
                breakendMatches.add(new ReadBreakendMatch(breakend, true));
                continue;
            }

            int discordantDistance = readDiscordantBreakendDistance(breakend, read);

            if(discordantDistance != INVALID_DISCORANT_DISTANCE)
            {
                if(closestDiscJunctionDistance == INVALID_DISCORANT_DISTANCE || discordantDistance < closestDiscJunctionDistance)
                {
                    closestDiscJunctionDistance = discordantDistance;
                    closestDiscordantBreakend = breakend;
                }
            }
        }

        if(closestDiscordantBreakend != null)
            breakendMatches.add(new ReadBreakendMatch(closestDiscordantBreakend, false));

        return breakendMatches;
    }

    private static boolean readSpansJunction(final Breakend breakend, final SupportRead read)
    {
        // first an aligned junction read
        if(read.unclippedStart() < breakend.Position && read.unclippedEnd() > breakend.Position)
            return true;

        // next a misaligned junction read - crossing the segment boundary
        int readSeqIndexStart = read.fullAssemblyIndexStart();
        int readSeqIndexEnd = read.fullAssemblyIndexEnd();

        // look for a read crossing a segment boundary
        for(BreakendSegment segment : breakend.segments())
        {
            int segmentBreakendIndex = segment.Orient.isForward() ? segment.Alignment.sequenceEnd() : segment.Alignment.sequenceStart();

            if(readSeqIndexStart < segmentBreakendIndex && readSeqIndexEnd > segmentBreakendIndex)
                return true;
        }

        return false;
    }

    private static final int INVALID_DISCORANT_DISTANCE = -1;

    private static int readDiscordantBreakendDistance(final Breakend breakend, final SupportRead read)
    {
        // first check for an aligned discordant read
        if(breakend.Chromosome.equals(read.chromosome()) && read.orientation() == breakend.Orient)
        {
            if(breakend.Orient.isForward())
            {
                int maxPosition = max(breakend.maxPosition(), breakend.Position);

                if(read.alignmentEnd() <= maxPosition && read.alignmentStart() >= breakend.Position - DEFAULT_DISCORDANT_FRAGMENT_LENGTH)
                    return maxPosition - read.alignmentEnd();
            }
            else
            {
                int minPosition = min(breakend.minPosition(), breakend.Position);

                if(read.alignmentStart() >= minPosition && read.alignmentEnd() <= breakend.Position + DEFAULT_DISCORDANT_FRAGMENT_LENGTH)
                    return read.alignmentStart() - minPosition;
            }
        }

        int readSeqIndexStart = read.fullAssemblyIndexStart();
        int readSeqIndexEnd = read.fullAssemblyIndexEnd();

        for(BreakendSegment segment : breakend.segments())
        {
            if(read.fullAssemblyOrientation() != segment.Orient)
                continue;

            if(segment.Orient.isForward())
            {
                int segmentBreakendIndex = segment.Alignment.sequenceEnd();

                if(readSeqIndexEnd <= segmentBreakendIndex && readSeqIndexEnd >= segmentBreakendIndex - DEFAULT_DISCORDANT_FRAGMENT_LENGTH)
                    return segmentBreakendIndex - readSeqIndexEnd;
            }
            else
            {
                int segmentBreakendIndex = segment.Alignment.sequenceStart();

                if(readSeqIndexStart >= segmentBreakendIndex && readSeqIndexStart <= segmentBreakendIndex + DEFAULT_DISCORDANT_FRAGMENT_LENGTH)
                    return readSeqIndexStart - segmentBreakendIndex;
            }
        }

        return INVALID_DISCORANT_DISTANCE;
    }
}

