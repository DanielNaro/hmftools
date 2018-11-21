package com.hartwig.hmftools.svannotation.analysis;

import static com.hartwig.hmftools.common.variant.structural.annotation.Transcript.TRANS_CODING_TYPE_NON_CODING;
import static com.hartwig.hmftools.svannotation.analysis.StructuralVariantAnalyzer.intronicDisruptionOnSameTranscript;
import static com.hartwig.hmftools.svannotation.analysis.StructuralVariantAnalyzer.isUpstream;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.fusions.KnownFusionsModel;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneAnnotation;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneFusion;
import com.hartwig.hmftools.common.variant.structural.annotation.ImmutableGeneFusion;
import com.hartwig.hmftools.common.variant.structural.annotation.StructuralVariantAnnotation;
import com.hartwig.hmftools.common.variant.structural.annotation.Transcript;

import org.apache.commons.cli.Options;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class SvFusionAnalyser
{

    public static final String FUSION_PAIRS_CSV = "fusion_pairs_csv";
    public static final String PROMISCUOUS_FIVE_CSV = "promiscuous_five_csv";
    public static final String PROMISCUOUS_THREE_CSV = "promiscuous_three_csv";

    private static final int EXON_THRESHOLD = 1;

    private final KnownFusionsModel mKnownFusionsModel;

    private static final Logger LOGGER = LogManager.getLogger(SvFusionAnalyser.class);

    public SvFusionAnalyser(final KnownFusionsModel knownFusionsModel)
    {
        mKnownFusionsModel = knownFusionsModel;
    }

    public static void addCmdLineArgs(Options options)
    {
        options.addOption(FUSION_PAIRS_CSV, true, "Path towards a CSV containing white-listed gene fusion pairs.");
        options.addOption(PROMISCUOUS_FIVE_CSV, true, "Path towards a CSV containing white-listed promiscuous 5' genes.");
        options.addOption(PROMISCUOUS_THREE_CSV, true, "Path towards a CSV containing white-listed promiscuous 3' genes.");
    }

    public final List<GeneFusion> findFusions(final List<StructuralVariantAnnotation> annotations)
    {
        LOGGER.debug("finding fusions in {} annotations", annotations.size());

        List<GeneFusion> fusions = Lists.newArrayList();

        for (final StructuralVariantAnnotation annotation : annotations)
        {
            List<GeneFusion> svFusions = findFusions(annotation.start(), annotation.end());

            fusions.addAll(svFusions);
        }

        return fusions;
    }

    public final List<GeneFusion> findFusions(final List<GeneAnnotation> breakendGenes1, final List<GeneAnnotation> breakendGenes2)
    {
        final List<List<Pair<Transcript, Transcript>>> fusionTranscriptPairs = Lists.newArrayList();

        final List<Pair<Transcript, Transcript>> potentialFusions = Lists.newArrayList();

        for (final GeneAnnotation startGene : breakendGenes1)
        {
            // left is upstream, right is downstream
            boolean startUpstream = isUpstream(startGene);

            for (final GeneAnnotation endGene : breakendGenes2)
            {
                boolean endUpstream = isUpstream(endGene);

                if (startUpstream == endUpstream)
                    continue;

                // see FV Fusions document for permitted combinations
                for (final Transcript startTrans : startGene.transcripts())
                {
                    for (final Transcript endTrans : endGene.transcripts())
                    {
                        final Transcript upstreamTrans = startUpstream ? startTrans : endTrans;
                        final Transcript downstreamTrans = !startUpstream ? startTrans : endTrans;

                        boolean checkExactMatch = false;

                        if(upstreamTrans.postCoding() || downstreamTrans.postCoding() || downstreamTrans.nonCoding())
                            continue;

                        if(upstreamTrans.isPromoter())
                            continue;

                        if(downstreamTrans.isPrePromotor())
                            continue;

                        if(upstreamTrans.preCoding())
                        {
                            if(upstreamTrans.isExonic() && !downstreamTrans.isExonic())
                                continue;
                            else if(downstreamTrans.isCoding())
                                continue;

                            // phasing match
                        }
                        else if(upstreamTrans.isCoding())
                        {
                            if(!downstreamTrans.isCoding())
                                continue;

                            if(upstreamTrans.isExonic())
                            {
                                if(!downstreamTrans.isExonic())
                                    continue;

                                checkExactMatch = true;
                            }

                            // phasing match
                        }
                        else if(upstreamTrans.nonCoding())
                        {
                            if(upstreamTrans.isExonic() && !downstreamTrans.isExonic())
                                continue;
                            else if(downstreamTrans.isCoding())
                                continue;

                            // phasing match
                        }

                        /*
                        // DEBUG
                        if(upstreamTrans.transcriptId().equals("ENST00000312970") && downstreamTrans.transcriptId().equals("ENST00000438429"))
                        {
                            LOGGER.debug("trans match");
                        }
                        */

                        if (!isPotentiallyRelevantFusion(upstreamTrans, downstreamTrans))
                            continue;

                        if(checkExactMatch)
                        {
                            if(exonToExonInPhase(upstreamTrans, true, downstreamTrans, false))
                            {
                                addFusion(potentialFusions, upstreamTrans, downstreamTrans);
                            }
                        }
                        else
                        {
                            // just check for a phasing match
                            if (upstreamTrans.exonUpstreamPhase() == downstreamTrans.exonDownstreamPhase())
                            {
                                // all fusions to downstream exons may be excluded, but for now definitely exclude those which end in the last exon
                                if(downstreamTrans.isExonic() && downstreamTrans.exonDownstream() == downstreamTrans.exonMax() && ! downstreamTrans.preCoding())
                                    continue;

                                addFusion(potentialFusions, upstreamTrans, downstreamTrans);
                            }
                        }
                    }
                }
            }
        }

        fusionTranscriptPairs.add(potentialFusions);

        List<GeneFusion> fusions = toReportableGeneFusions(fusionTranscriptPairs);

        return fusions;
    }

    private static boolean exonToExonInPhase(final Transcript startTrans, boolean startUpstream, final Transcript endTrans, boolean endUpstream)
    {
        // check phasing and offset since exon start or coding start
        int calcStartPhase = calcPositionPhasing(startTrans, startUpstream);
        int calcEndPhase = calcPositionPhasing(endTrans, endUpstream);

        startTrans.setExactCodingBase(calcStartPhase);
        endTrans.setExactCodingBase(calcEndPhase);

        return calcStartPhase == calcEndPhase;
    }

    private static int calcPositionPhasing(final Transcript transcript, boolean isUpstream)
    {
        // if upstream then can just use the coding bases
        // if downstream then coding bases are what's remaing
        long codingBases = isUpstream ? transcript.codingBases() : transcript.totalCodingBases() - transcript.codingBases();

        int adjustedPhase = (int)(codingBases % 3);

        return adjustedPhase;
    }

    private void addFusion(List<Pair<Transcript, Transcript>> fusions, final Transcript startTrans, final Transcript endTrans)
    {
        //LOGGER.debug("adding fusion between start SV({}) trans({}) and end SV({}) trans({})",
        //        startTrans.parent().id(), startTrans.toString(), endTrans.parent().id(), endTrans.toString());

        fusions.add(Pair.of(startTrans, endTrans));
    }

    private static boolean isPotentiallyRelevantFusion(final Transcript t1, final Transcript t2)
    {
        if(!t1.geneName().equals(t2.geneName()))
            return true;

        // skip fusions between different transcripts in the same gene,
        if (!t1.transcriptId().equals(t2.transcriptId()))
            return false;

        if(t1.nonCoding())
            return false;

        // skip fusions within the same intron
        if(t1.isIntronic() && t2.isIntronic() && t1.exonUpstream() == t2.exonUpstream())
            return false;

        return true;
    }

    @NotNull
    private List<GeneFusion> toReportableGeneFusions(@NotNull List<List<Pair<Transcript, Transcript>>> fusionTranscriptPairs)
    {
        final List<GeneFusion> result = Lists.newArrayList();

        for (final List<Pair<Transcript, Transcript>> fusions : fusionTranscriptPairs)
        {
            Optional<Pair<Transcript, Transcript>> reportableFusion = determineReportableFusion(fusions);

            for (final Pair<Transcript, Transcript> fusion : fusions)
            {
                final Transcript upstream = fusion.getLeft();
                final Transcript downstream = fusion.getRight();

                boolean reportable = false;

                if(reportableFusion.isPresent() && reportableFusion.get() == fusion)
                {
                    boolean intragenicOk = !(intragenic(upstream, downstream) && upstream.exonUpstreamPhase() == -1);

                    if(intragenicOk)
                        reportable = true;
                    else
                        reportable = false;

                    // old rule was checking (downstream: not non-coding and not downstream from coding)
                    // AND (upstream: non-coding or not downstream of coding)
                    // AND NOT (intragenic and upstream phase -1)
                    /*
                    boolean oldReportable = isPostCodingDownstream != null && !isPostCodingDownstream
                            && (isPostCodingUpstream == null || !isPostCodingUpstream)
                            && !(intragenic(upstream, downstream) && upstream.exonUpstreamPhase() == -1);
                    */
                }

                final GeneFusion geneFusion = ImmutableGeneFusion.builder()
                        .reportable(reportable)
                        .upstreamLinkedAnnotation(upstream)
                        .downstreamLinkedAnnotation(downstream)
                        .primarySource(mKnownFusionsModel.primarySource(upstream.parent().synonyms(), downstream.parent().synonyms()))
                        .build();

                result.add(geneFusion);
            }
        }
        return result;
    }

    @NotNull
    private Optional<Pair<Transcript, Transcript>> determineReportableFusion(@NotNull List<Pair<Transcript, Transcript>> fusions)
    {
        // Select either the canonical -> canonical transcript fusion
        //  then the one with the most exons where one end is canonical
        //  then the one with the most exons combined transcript

        List<Pair<Transcript, Transcript>> knownFusions = fusions.stream()
                .filter(pair -> transcriptsMatchKnownFusion(mKnownFusionsModel, pair.getLeft(), pair.getRight()))
                .collect(Collectors.toList());


        Optional<Pair<Transcript, Transcript>> reportableFusion =
                knownFusions.stream()
                        .filter(pair -> pair.getLeft().isCanonical() && pair.getRight().isCanonical()).findFirst();

        if (!reportableFusion.isPresent())
        {
            reportableFusion = knownFusions.stream()
                    .filter(pair -> pair.getLeft().isCanonical() || pair.getRight().isCanonical())
                    .sorted(Comparator.comparingInt(a -> a.getLeft().exonMax() + a.getRight().exonMax()))
                    .reduce((a, b) -> b);
        }

        if (!reportableFusion.isPresent())
        {
            reportableFusion = knownFusions.stream()
                    .sorted(Comparator.comparingInt(a -> a.getLeft().exonMax() + a.getRight().exonMax()))
                    .reduce((a, b) -> b);
        }

        return reportableFusion;
    }

    private static boolean transcriptsMatchKnownFusion(final KnownFusionsModel fusionsModel, final Transcript five, final Transcript three)
    {
        if(fusionsModel.exactMatch(five.parent().synonyms(), three.parent().synonyms()))
            return true;

        if(fusionsModel.intergenicPromiscuousMatch(five.parent().synonyms(), three.parent().synonyms()))
            return true;

        if(fusionsModel.intragenicPromiscuousMatch(five.parent().synonyms(), three.parent().synonyms())
        && three.exonDownstream() - five.exonUpstream() > EXON_THRESHOLD)
        {
            return true;
        }

        return false;
    }

    private static boolean intragenic(final Transcript upstream, final Transcript downstream)
    {
        return upstream.parent().synonyms().stream().anyMatch(downstream.parent().synonyms()::contains);
    }

}
