package com.hartwig.hmftools.esvee.alignment;

import static java.lang.Math.min;

import static com.hartwig.hmftools.common.utils.TaskExecutor.runThreadTasks;
import static com.hartwig.hmftools.esvee.AssemblyConfig.SV_LOGGER;
import static com.hartwig.hmftools.esvee.assembly.types.ThreadTask.mergePerfCounters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.utils.PerformanceCounter;
import com.hartwig.hmftools.esvee.AssemblyConfig;
import com.hartwig.hmftools.esvee.assembly.output.AlignmentWriter;
import com.hartwig.hmftools.esvee.assembly.output.WriteType;
import com.hartwig.hmftools.esvee.assembly.types.AssemblyOutcome;
import com.hartwig.hmftools.esvee.assembly.types.JunctionAssembly;
import com.hartwig.hmftools.esvee.assembly.types.ThreadTask;

import org.broadinstitute.hellbender.utils.bwa.BwaMemAlignment;

public class Alignment
{
    private final AssemblyConfig mConfig;

    private final Aligner mAligner;
    private final AlignmentWriter mWriter;
    private final AlignmentCache mAlignmentCache;

    public Alignment(final AssemblyConfig config, final Aligner aligner)
    {
        mConfig = config;
        mAligner = aligner;
        mWriter = new AlignmentWriter(mConfig);
        mAlignmentCache = new AlignmentCache(config.AlignmentFile);
    }

    public void close() { mWriter.close(); }

    public static boolean skipUnlinkedJunctionAssembly(final JunctionAssembly assembly)
    {
        // apply filters on what to run alignment on
        if(assembly.outcome() == AssemblyOutcome.DUP_BRANCHED
        || assembly.outcome() == AssemblyOutcome.SECONDARY
        || assembly.outcome() == AssemblyOutcome.REMOTE_REGION)
        {
            // since identical to or associated with other links
            return true;
        }

        return false;
    }

    public void run(final List<AssemblyAlignment> assemblyAlignments, final List<PerformanceCounter> perfCounters)
    {
        if(mAligner == null && !mAlignmentCache.enabled())
            return;

        int singleAssemblies = (int) assemblyAlignments.stream().filter(x -> x.assemblies().size() == 1).count();
        int linkedAssemblies = assemblyAlignments.size() - singleAssemblies;

        SV_LOGGER.info("running alignment for {} assemblies, linked({}) single({})",
                assemblyAlignments.size(), linkedAssemblies, singleAssemblies);

        Queue<AssemblyAlignment> assemblyAlignmentQueue = new ConcurrentLinkedQueue<>();

        assemblyAlignments.forEach(x -> assemblyAlignmentQueue.add(x));

        List<Thread> threadTasks = new ArrayList<>();
        List<AssemblerAlignerTask> alignerTasks = Lists.newArrayList();

        int taskCount = min(mConfig.Threads, assemblyAlignments.size());

        for(int i = 0; i < taskCount; ++i)
        {
            AssemblerAlignerTask assemblerAlignerTask = new AssemblerAlignerTask(assemblyAlignmentQueue);
            alignerTasks.add(assemblerAlignerTask);
            threadTasks.add(assemblerAlignerTask);
        }

        if(!runThreadTasks(threadTasks))
            System.exit(1);

        SV_LOGGER.debug("requeried supp alignments({})", alignerTasks.stream().mapToInt(x -> x.requeriedSuppCount()).sum());

        SV_LOGGER.info("alignment complete");

        mergePerfCounters(perfCounters, alignerTasks.stream().collect(Collectors.toList()));
    }

    private class AssemblerAlignerTask extends ThreadTask
    {
        private final Queue<AssemblyAlignment> mAssemblyAlignments;
        private final int mAssemblyAlignmentCount;
        private int mRequeriedSuppCount;

        public AssemblerAlignerTask(final Queue<AssemblyAlignment> assemblyAlignments)
        {
            super("AssemblerAlignment");
            mAssemblyAlignments = assemblyAlignments;
            mAssemblyAlignmentCount = assemblyAlignments.size();
            mRequeriedSuppCount = 0;
        }

        private static final int LOG_COUNT = 10000;

        public int requeriedSuppCount() { return mRequeriedSuppCount; }

        @Override
        public void run()
        {
            while(true)
            {
                try
                {
                    int remainingCount = mAssemblyAlignments.size();
                    int processedCount = mAssemblyAlignmentCount - remainingCount;

                    mPerfCounter.start();

                    ++processedCount;

                    AssemblyAlignment assemblyAlignment = mAssemblyAlignments.remove();

                    processAssembly(assemblyAlignment);

                    if((processedCount % LOG_COUNT) == 0)
                    {
                        SV_LOGGER.debug("processed {} assembly alignments", processedCount);
                    }

                    stopCheckLog(assemblyAlignment.info(), mConfig.PerfLogTime);
                }
                catch(NoSuchElementException e)
                {
                    SV_LOGGER.trace("all alignment tasks complete");
                    break;
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }

        private void processAssembly(final AssemblyAlignment assemblyAlignment)
        {
            List<AlignData> alignments;
            List<AlignData> requeriedAlignments;

            if(mAlignmentCache.enabled())
            {
                alignments = mAlignmentCache.findAssemblyAlignments(assemblyAlignment.info());
                requeriedAlignments = Collections.emptyList();
            }
            else
            {
                List<BwaMemAlignment> bwaAlignments = mAligner.alignSequence(assemblyAlignment.fullSequence().getBytes());

                alignments = bwaAlignments.stream()
                        .map(x -> AlignData.from(x, mConfig.RefGenVersion))
                        .filter(x -> x != null).collect(Collectors.toList());

                requeriedAlignments = Lists.newArrayList();
                alignments = requerySupplementaryAlignments(assemblyAlignment, alignments, requeriedAlignments);
            }

            processAlignmentResults(assemblyAlignment, alignments);

            AlignmentFragments alignmentFragments = new AlignmentFragments(assemblyAlignment, mConfig.combinedSampleIds());
            alignmentFragments.allocateBreakendSupport();

            if(mConfig.WriteTypes.contains(WriteType.ALIGNMENT))
                AlignmentWriter.writeAssemblyAlignment(mWriter.alignmentWriter(), assemblyAlignment, alignments);

            if(mConfig.WriteTypes.contains(WriteType.ALIGNMENT_DATA))
            {
                List<AlignData> alignmentsToWrite;

                if(!requeriedAlignments.isEmpty())
                {
                    alignmentsToWrite = Lists.newArrayList(alignments);
                    alignmentsToWrite.addAll(requeriedAlignments);
                }
                else
                {
                    alignmentsToWrite = alignments;
                }

                AlignmentWriter.writeAlignmentDetails(mWriter.alignmentDetailsWriter(), assemblyAlignment, alignmentsToWrite);
            }
        }

        private List<AlignData> requerySupplementaryAlignments(
                final AssemblyAlignment assemblyAlignment, final List<AlignData> alignments, final List<AlignData> requeriedAlignments)
        {
            // re-alignment supplementaries to get a more reliable map quality
            if(alignments.stream().noneMatch(x -> x.isSupplementary()))
                return alignments;

            List<AlignData> newAlignments = Lists.newArrayList();

            for(AlignData alignData : alignments)
            {
                if(!alignData.isSupplementary())
                {
                    newAlignments.add(alignData);
                    continue;
                }

                requeriedAlignments.add(alignData);
                newAlignments.addAll(requeryAlignment(assemblyAlignment, alignData));
            }

            return newAlignments;
        }

        private List<AlignData> requeryAlignment(final AssemblyAlignment assemblyAlignment, final AlignData alignData)
        {
            ++mRequeriedSuppCount;

            String fullSequence = assemblyAlignment.fullSequence();

            alignData.setFullSequenceData(fullSequence, assemblyAlignment.fullSequenceLength());

            String alignmentSequence = fullSequence.substring(alignData.sequenceStart(), alignData.sequenceEnd() + 1);

            List<BwaMemAlignment> requeryBwaAlignments = mAligner.alignSequence(alignmentSequence.getBytes());

            List<AlignData> requeryAlignments = requeryBwaAlignments.stream()
                    .map(x -> AlignData.from(x, mConfig.RefGenVersion))
                    .filter(x -> x != null).collect(Collectors.toList());

            List<AlignData> convertedAlignments = Lists.newArrayList();

            for(AlignData rqAlignment : requeryAlignments)
            {
                rqAlignment.setFullSequenceData(alignmentSequence, alignmentSequence.length());

                // eg:
                // alignData = {AlignData@3240} "10:2543491-2543563 72S73M fwd seq(72-145 adj=72-144) score(58) flags(2048) mapQual(55 align=73 adj=73)"
                // rqAlignment = {AlignData@3246} "10:2543809-2543878 3S70M fwd seq(3-73 adj=3-72) score(65) flags(0) mapQual(17 align=70 adj=70)"

                AlignData convertedAlignment = new AlignData(
                        rqAlignment.RefLocation,
                        rqAlignment.rawSequenceStart(),
                        rqAlignment.rawSequenceEnd(),
                        rqAlignment.MapQual, rqAlignment.Score, rqAlignment.Flags, rqAlignment.Cigar, rqAlignment.NMatches,
                        rqAlignment.XaTag, rqAlignment.MdTag);

                // restore values to be in terms of the original sequence
                int rqSeqOffsetStart = rqAlignment.sequenceStart();
                int adjSequenceStart = alignData.sequenceStart() + rqSeqOffsetStart;
                int rqSeqOffsetEnd = alignmentSequence.length() - 1 - rqAlignment.sequenceEnd();
                int adjSequenceEnd = alignData.sequenceEnd() - rqSeqOffsetEnd;
                convertedAlignment.setRequeriedSequenceCoords(adjSequenceStart, adjSequenceEnd);

                convertedAlignments.add(convertedAlignment);
            }

            return convertedAlignments;
        }

        private void processAlignmentResults(final AssemblyAlignment assemblyAlignment, final List<AlignData> alignments)
        {
            BreakendBuilder breakendBuilder = new BreakendBuilder(mConfig.RefGenome, assemblyAlignment);
            breakendBuilder.formBreakends(alignments);

            for(JunctionAssembly assembly : assemblyAlignment.assemblies())
            {
                boolean matched = false;

                for(Breakend breakend : assemblyAlignment.breakends())
                {
                    if(breakend.matches(assembly.junction().Chromosome, assembly.junction().Position, assembly.junction().Orient))
                    {
                        assembly.setAlignmentOutcome(AlignmentOutcome.MATCH);
                        matched = true;
                        break;
                    }
                }

                if(!matched)
                    assembly.setAlignmentOutcome(AlignmentOutcome.NO_MATCH);
            }

            if(assemblyAlignment.breakends().isEmpty())
                assemblyAlignment.assemblies().forEach(x -> x.setAlignmentOutcome(AlignmentOutcome.NO_RESULT));
        }
    }
}
