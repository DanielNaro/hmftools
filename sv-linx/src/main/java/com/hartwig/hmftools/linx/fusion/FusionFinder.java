package com.hartwig.hmftools.linx.fusion;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import static com.hartwig.hmftools.common.variant.structural.annotation.GeneFusion.REPORTABLE_TYPE_3P_PROM;
import static com.hartwig.hmftools.common.variant.structural.annotation.GeneFusion.REPORTABLE_TYPE_5P_PROM;
import static com.hartwig.hmftools.common.variant.structural.annotation.GeneFusion.REPORTABLE_TYPE_BOTH_PROM;
import static com.hartwig.hmftools.common.variant.structural.annotation.GeneFusion.REPORTABLE_TYPE_KNOWN;
import static com.hartwig.hmftools.common.variant.structural.annotation.GeneFusion.REPORTABLE_TYPE_NONE;

import static com.hartwig.hmftools.linx.fusion.KnownFusionData.FUSION_PAIRS_CSV;
import static com.hartwig.hmftools.linx.fusion.KnownFusionData.PROMISCUOUS_FIVE_CSV;
import static com.hartwig.hmftools.linx.fusion.KnownFusionData.PROMISCUOUS_THREE_CSV;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneAnnotation;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneFusion;
import com.hartwig.hmftools.common.variant.structural.annotation.Transcript;
import com.hartwig.hmftools.common.variant.structural.annotation.TranscriptProteinData;
import com.hartwig.hmftools.linx.gene.SvGeneTranscriptCollection;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class FusionFinder
{
    private KnownFusionData mKnownFusionData;
    boolean mHasValidConfigData;

    private SvGeneTranscriptCollection mGeneTranscriptCollection;
    private List<String> mProteinsRequiredKept;
    private List<String> mProteinsRequiredLost;

    private static boolean mLogInvalidReasons;

    private static final int EXON_THRESHOLD = 1;

    private static final Logger LOGGER = LogManager.getLogger(FusionFinder.class);

    public FusionFinder(final CommandLine cmd, final SvGeneTranscriptCollection geneTransCache)
    {
        mGeneTranscriptCollection = geneTransCache;

        mKnownFusionData = null;
        mHasValidConfigData = false;

        mProteinsRequiredKept = Lists.newArrayList();
        mProteinsRequiredLost = Lists.newArrayList();
        setRequiredProteins();

        if(cmd != null)
        {
            initialise(cmd);
        }

        mLogInvalidReasons = false;
    }

    private void initialise(@NotNull final CommandLine cmd)
    {
        if(cmd.hasOption(FUSION_PAIRS_CSV) || cmd.hasOption(PROMISCUOUS_FIVE_CSV) || cmd.hasOption(PROMISCUOUS_THREE_CSV))
        {
            mKnownFusionData = new KnownFusionData();

            if(mKnownFusionData.loadFromFile(cmd))
            {
                LOGGER.debug("loaded known fusion data");
                mHasValidConfigData = true;
            }
        }
    }

    public void setHasValidConfigData(boolean toggle) { mHasValidConfigData = toggle; }
    public void setLogInvalidReasons(boolean toggle) { mLogInvalidReasons = toggle; }

    public static void addCmdLineArgs(Options options)
    {
        options.addOption(FUSION_PAIRS_CSV, true, "Path towards a CSV containing white-listed gene fusion pairs.");
        options.addOption(PROMISCUOUS_FIVE_CSV, true, "Path towards a CSV containing white-listed promiscuous 5' genes.");
        options.addOption(PROMISCUOUS_THREE_CSV, true, "Path towards a CSV containing white-listed promiscuous 3' genes.");
    }

    public final KnownFusionData getKnownFusionDatal() { return mKnownFusionData; }

    public static final String INVALID_REASON_ORIENTATION = "Orientation";
    public static final String INVALID_REASON_PHASING = "Unphased";
    public static final String INVALID_REASON_CODING_TYPE = "Coding";

    public final List<GeneFusion> findFusions(
            final List<GeneAnnotation> breakendGenes1, final List<GeneAnnotation> breakendGenes2,
            final FusionParameters params, boolean setReportable)
    {
        final List<GeneFusion> potentialFusions = Lists.newArrayList();

        if(!mHasValidConfigData)
            return potentialFusions;

        for (final GeneAnnotation startGene : breakendGenes1)
        {
            // left is upstream, right is downstream
            boolean startUpstream = startGene.isUpstream();

            for (final GeneAnnotation endGene : breakendGenes2)
            {
                boolean endUpstream = endGene.isUpstream();

                if (startUpstream == endUpstream)
                {
                    if(params.InvalidReasons != null && !params.InvalidReasons.contains(INVALID_REASON_ORIENTATION))
                        params.InvalidReasons.add(INVALID_REASON_ORIENTATION);

                    continue;
                }

                final GeneAnnotation upGene = startUpstream ? startGene : endGene;
                final GeneAnnotation downGene = !startUpstream ? startGene : endGene;

                boolean knownPair = mKnownFusionData != null && mKnownFusionData.hasKnownFusion(upGene.GeneName, downGene.GeneName);

                for (final Transcript upstreamTrans : upGene.transcripts())
                {
                    for (final Transcript downstreamTrans : downGene.transcripts())
                    {
                        GeneFusion geneFusion = checkFusionLogic(upstreamTrans, downstreamTrans, params, !knownPair);

                        if(geneFusion == null)
                            continue;

                        geneFusion.setKnownType(getKnownFusionType(upstreamTrans, downstreamTrans));

                        potentialFusions.add(geneFusion);
                    }
                }
            }
        }

        if(setReportable)
            setReportableGeneFusions(potentialFusions);

        return potentialFusions;
    }

    private static void logInvalidReasonInfo(final Transcript trans1, final Transcript trans2, final String reasonType, final String reason)
    {
        if(!mLogInvalidReasons)
            return;

        if(trans2 == null)
            LOGGER.trace("transcript({}:{}) invalid({}: {})", trans1.geneName(), trans1.StableId, reasonType, reason);
        else
            LOGGER.trace("transcripts({}:{} and {}:{}) invalid({}: {})",
                    trans1.geneName(), trans1.StableId, trans2.geneName(), trans2.StableId, reasonType, reason);
    }

    public static boolean validFusionTranscript(final Transcript transcript)
    {
        return validFusionTranscript(transcript, true);
    }

    private static boolean validFusionTranscript(final Transcript transcript, boolean requireUpstreamDisruptive)
    {
        // check any conditions which would preclude this transcript being a part of a fusion no matter the other end
        if(transcript.isUpstream())
        {
            if(transcript.isPromoter())
                return false;

            if(requireUpstreamDisruptive && !transcript.isDisruptive())
                return false;
        }
        else
        {
            if(transcript.postCoding())
                return false;

            if(transcript.nonCoding())
                return false;

            if(transcript.ExonMax == 1)
                return false;
        }

        return true;
    }

    public static GeneFusion checkFusionLogic(final Transcript upstreamTrans, final Transcript downstreamTrans, final FusionParameters params)
    {
        return checkFusionLogic(upstreamTrans, downstreamTrans, params, true);
    }

    private static GeneFusion checkFusionLogic(
            final Transcript upstreamTrans, final Transcript downstreamTrans, final FusionParameters params, boolean requireUpstreamDisruptive)
    {
        // see SV Fusions document for permitted combinations
        boolean checkExactMatch = false;

        if(!validFusionTranscript(upstreamTrans, requireUpstreamDisruptive) || !validFusionTranscript(downstreamTrans))
        {
            logInvalidReasonInfo(upstreamTrans, downstreamTrans, INVALID_REASON_CODING_TYPE, "invalid trans");
            return null;
        }

        if(upstreamTrans.preCoding())
        {
            if(upstreamTrans.isExonic() && !downstreamTrans.isExonic())
            {
                logInvalidReasonInfo(upstreamTrans, downstreamTrans, INVALID_REASON_CODING_TYPE, "precoding exonic to non-exonic");
                return null;
            }
            else if(downstreamTrans.isCoding())
            {
                logInvalidReasonInfo(upstreamTrans, downstreamTrans, INVALID_REASON_CODING_TYPE, "pre-coding to coding");
                return null;
            }
            else if(downstreamTrans.preCoding() && upstreamTrans.gene().StableId.equals(downstreamTrans.gene().StableId))
            {
                // skip pre-coding to pre-coding within the same gene
                logInvalidReasonInfo(upstreamTrans, downstreamTrans, INVALID_REASON_CODING_TYPE, "pre-coding to pre-coding");
                return null;
            }
        }
        else if(upstreamTrans.isCoding())
        {
            if(!downstreamTrans.isCoding())
            {
                logInvalidReasonInfo(upstreamTrans, downstreamTrans, INVALID_REASON_CODING_TYPE, "coding to non-coding");
                return null;
            }

            if(upstreamTrans.isExonic())
            {
                if(!downstreamTrans.isExonic() && (!params.AllowExonSkipping || !downstreamTrans.isIntronic()))
                {
                    logInvalidReasonInfo(upstreamTrans, downstreamTrans, INVALID_REASON_CODING_TYPE, "coding exonic to non-exonic");
                    return null;
                }

                if(upstreamTrans.gene().id() != downstreamTrans.gene().id())
                {
                    logInvalidReasonInfo(upstreamTrans, downstreamTrans, INVALID_REASON_CODING_TYPE, "up coding exonic diff SVs");
                    return null;
                }

                // coding exon to coding exon will require phase adjustments to be exact
                checkExactMatch = downstreamTrans.isExonic();
            }
        }
        else if(upstreamTrans.nonCoding())
        {
            if(upstreamTrans.isExonic() && !downstreamTrans.isExonic())
            {
                logInvalidReasonInfo(upstreamTrans, downstreamTrans, INVALID_REASON_CODING_TYPE, "up non-coding exonic to down non-exonic");
                return null;
            }
            else if(downstreamTrans.isCoding())
            {
                logInvalidReasonInfo(upstreamTrans, downstreamTrans, INVALID_REASON_CODING_TYPE, "up non-coding to down-coding");
                return null;
            }
        }

        if (!isPotentiallyRelevantFusion(upstreamTrans, downstreamTrans))
        {
            logInvalidReasonInfo(upstreamTrans, downstreamTrans, INVALID_REASON_CODING_TYPE, "irrelevant fusion");
            return null;
        }

        boolean phaseMatched = false;
        int phaseExonsSkippedUp = 0;
        int phaseExonsSkippedDown = 0;

        if(!checkExactMatch)
        {
            // all fusions to downstream exons may be excluded, but for now definitely exclude those which end in the last exon
            if(downstreamTrans.isExonic() && downstreamTrans.ExonDownstream == downstreamTrans.ExonMax && !downstreamTrans.preCoding())
            {
                logInvalidReasonInfo(upstreamTrans, downstreamTrans, INVALID_REASON_CODING_TYPE, "downstream last exon");
                return null;
            }
        }

        if(checkExactMatch)
        {
            phaseMatched = exonToExonInPhase(upstreamTrans, downstreamTrans);

            if(!phaseMatched)
            {
                logInvalidReasonInfo(upstreamTrans, downstreamTrans, INVALID_REASON_PHASING, "exon-exon inexact phasing");
            }

            if(phaseMatched || !params.RequirePhaseMatch)
            {
                return new GeneFusion(upstreamTrans, downstreamTrans, phaseMatched);
            }
        }
        else
        {
            // just check for a phasing match
            if(upstreamTrans.isExonic() && downstreamTrans.isExonic())
            {
                phaseMatched = (upstreamTrans.preCoding() && downstreamTrans.preCoding())
                        || (upstreamTrans.postCoding() && downstreamTrans.postCoding());
            }
            else
            {
                phaseMatched = upstreamTrans.ExonUpstreamPhase == downstreamTrans.ExonDownstreamPhase;
            }

            if(!phaseMatched && params.AllowExonSkipping && !upstreamTrans.gene().StableId.equals(downstreamTrans.gene().StableId))
            {
                // check for a match within the alternative phasings from upstream and downstream of the breakend
                for (Map.Entry<Integer, Integer> altPhasing : upstreamTrans.getAlternativePhasing().entrySet())
                {
                    if (altPhasing.getKey() == downstreamTrans.ExonDownstreamPhase)
                    {
                        phaseMatched = true;
                        phaseExonsSkippedUp = altPhasing.getValue();
                        break;
                    }
                }

                if(!phaseMatched)
                {
                    for (Map.Entry<Integer, Integer> altPhasing : downstreamTrans.getAlternativePhasing().entrySet())
                    {
                        if (altPhasing.getKey() == upstreamTrans.ExonUpstreamPhase)
                        {
                            phaseMatched = true;
                            phaseExonsSkippedDown = altPhasing.getValue();
                            break;
                        }
                    }
                }
            }

            if(upstreamTrans.isExonic() && !downstreamTrans.isExonic())
            {
                if(phaseExonsSkippedUp == 0)
                    phaseExonsSkippedUp = 1;
            }
            else if(!upstreamTrans.isExonic() && downstreamTrans.isExonic())
            {
                if(phaseExonsSkippedDown == 0)
                    phaseExonsSkippedDown = 1;
            }

            if(!phaseMatched)
            {
                logInvalidReasonInfo(upstreamTrans, downstreamTrans, INVALID_REASON_PHASING, "inexact unphased");
            }

            if(phaseMatched || !params.RequirePhaseMatch)
            {
                GeneFusion fusion = new GeneFusion(upstreamTrans, downstreamTrans, phaseMatched);
                fusion.setExonsSkipped(phaseExonsSkippedUp, phaseExonsSkippedDown);
                return fusion;
            }
        }

        return null;
    }

    private static boolean exonToExonInPhase(final Transcript transUp, final Transcript transDown)
    {
        // check phasing and offset since exon start or coding start
        transUp.setExonicCodingBase();
        transDown.setExonicCodingBase();

        int upPhase = transUp.exonicBasePhase();
        int downPhase = transDown.exonicBasePhase();

        if(upPhase == -1 && downPhase == -1)
            return true;

        return ((upPhase + 1) % 3) == (downPhase % 3);
    }

    public static boolean isPotentiallyRelevantFusion(final Transcript t1, final Transcript t2)
    {
        if(!t1.geneName().equals(t2.geneName()))
            return true;

        // skip fusions between different transcripts in the same gene,
        if (!t1.StableId.equals(t2.StableId))
            return false;

        if(t1.nonCoding())
            return false;

        // skip fusions within the same intron
        if(t1.isIntronic() && t2.isIntronic() && t1.ExonUpstream == t2.ExonUpstream)
            return false;

        return true;
    }

    private void setRequiredProteins()
    {
        mProteinsRequiredLost.add("Raf-like Ras-binding");

        mProteinsRequiredKept.add("Ets domain");
        mProteinsRequiredKept.add("Protein kinase domain");
        mProteinsRequiredKept.add("Epidermal growth factor-like domain");
        mProteinsRequiredKept.add("Ankyrin repeat-containing domain");
        mProteinsRequiredKept.add("Basic-leucine zipper domain");
        mProteinsRequiredKept.add("High mobility group box domain");
        mProteinsRequiredKept.add("Bromodomain");
    }

    public void setReportableGeneFusions(final List<GeneFusion> fusions)
    {
        if(fusions.isEmpty())
            return;

        GeneFusion reportableFusion = determineReportableFusion(fusions, true);

        if(reportableFusion == null)
            return;

        if(intragenic(reportableFusion.upstreamTrans(), reportableFusion.downstreamTrans())
        && reportableFusion.upstreamTrans().ExonUpstreamPhase == -1)
        {
            return;
        }

        reportableFusion.setReportable(true);

        // check impact on protein regions
        setFusionProteinFeatures(reportableFusion);

        if (reportableFusion.getKnownType() != REPORTABLE_TYPE_KNOWN)
        {
            final Transcript downTrans = reportableFusion.downstreamTrans();
            long requiredKeptButLost = mProteinsRequiredKept.stream().filter(f -> downTrans.getProteinFeaturesLost().contains(f)).count();
            long requiredLostButKept = mProteinsRequiredLost.stream().filter(f -> downTrans.getProteinFeaturesKept().contains(f)).count();

            if (requiredKeptButLost > 0 || requiredLostButKept > 0)
                reportableFusion.setReportable(false);
        }
    }

    public void setFusionProteinFeatures(GeneFusion fusion)
    {
        final Transcript downTrans = fusion.downstreamTrans();

        if(!downTrans.getProteinFeaturesKept().isEmpty() || !downTrans.getProteinFeaturesLost().isEmpty())
            return;

        if(downTrans.nonCoding())
            return;

        final List<TranscriptProteinData> transProteinData = mGeneTranscriptCollection.getTranscriptProteinDataMap().get(downTrans.TransId);

        if(transProteinData == null || transProteinData.isEmpty())
            return;

        List<String> processedFeatures = Lists.newArrayList();

        for(int i = 0; i < transProteinData.size(); ++i)
        {
            final TranscriptProteinData pfData = transProteinData.get(i);
            final String feature = pfData.HitDescription;

            if(processedFeatures.contains(feature))
                continue;

            // find start and end across all entries matching this feature
            int featureStart = pfData.SeqStart;
            int featureEnd = pfData.SeqEnd;

            for(int j = i+1; j < transProteinData.size(); ++j)
            {
                if(transProteinData.get(j).HitDescription.equals(feature))
                    featureEnd = max(transProteinData.get(j).SeqEnd, featureEnd);
            }

            boolean pfPreserved = proteinFeaturePreserved(downTrans, true, feature, featureStart, featureEnd);

            if(!pfPreserved && downTrans.gene().StableId.equals(fusion.upstreamTrans().gene().StableId))
            {
                // for same gene fusions, check whether the upstream transcript section preserves this feature
                pfPreserved = proteinFeaturePreserved(fusion.upstreamTrans(), false, feature, featureStart, featureEnd);
            }

            downTrans.addProteinFeature(feature, pfPreserved);

            processedFeatures.add(feature);
        }

    }

    private static boolean proteinFeaturePreserved(
            final Transcript transcript, boolean isDownstream, final String feature, int featureStart, int featureEnd)
    {
        boolean featurePreserved;

        if(transcript.preCoding())
        {
            featurePreserved = isDownstream;
        }
        else if(transcript.postCoding())
        {
            featurePreserved = !isDownstream;
        }
        else
        {
            if (isDownstream)
            {
                // coding must start before the start of the feature for it to be preserved
                int featureCodingBaseStart = featureStart * 3;
                int svCodingBaseStart = transcript.codingBases();
                featurePreserved = (featureCodingBaseStart >= svCodingBaseStart);
            }
            else
            {
                int svCodingBaseEnd = transcript.codingBases();
                int featureCodingBaseEnd = featureEnd * 3;
                featurePreserved = (featureCodingBaseEnd <= svCodingBaseEnd);
            }
        }

        return featurePreserved;
    }

    public static String TRANSCRIPT_PROTEIN_CODING = "protein_coding";
    public static String TRANSCRIPT_NONSENSE_MED_DECAY = "nonsense_mediated_decay";

    private static int MAX_UPSTREAM_DISTANCE_KNOWN = 100000;
    private static int MAX_UPSTREAM_DISTANCE_OTHER = 10000;

    public static boolean couldBeReportable(GeneFusion fusion)
    {
        if(!fusion.phaseMatched() || fusion.neoEpitopeOnly())
            return false;

        // first check whether a fusion is known or not - a key requirement of it being potentially reportable
        if (fusion.getKnownType() == REPORTABLE_TYPE_NONE)
            return false;

        // set limits on how far upstream the breakend can be - adjusted for whether the fusions is known or not
        int maxUpstreamDistance = fusion.getKnownType() == REPORTABLE_TYPE_KNOWN ?
                MAX_UPSTREAM_DISTANCE_KNOWN : MAX_UPSTREAM_DISTANCE_OTHER;

        final Transcript upTrans = fusion.upstreamTrans();
        final Transcript downTrans = fusion.downstreamTrans();

        if(upTrans.getDistanceUpstream() > maxUpstreamDistance || downTrans.getDistanceUpstream() > maxUpstreamDistance)
            return false;

        if(downTrans.bioType().equals(TRANSCRIPT_NONSENSE_MED_DECAY))
            return false;

        if(downTrans.hasNegativePrevSpliceAcceptorDistance())
            return false;

        if(fusion.getKnownType() != REPORTABLE_TYPE_KNOWN
        && (fusion.getExonsSkipped(true) > 0 || fusion.getExonsSkipped(false) > 0))
            return false;

        return true;
    }

    public static GeneFusion determineReportableFusion(final List<GeneFusion> fusions, boolean requireReportable)
    {
        GeneFusion reportableFusion = null;

        // form a score by allocating 0/1 or length value to each power 10 descending
        long highestScore = 0;

        for(final GeneFusion fusion : fusions)
        {
            if(requireReportable && !couldBeReportable(fusion))
                continue;

            // first check whether a fusion is known or not - a key requirement of it being potentially reportable
            final Transcript upTrans = fusion.upstreamTrans();
            final Transcript downTrans = fusion.downstreamTrans();

            /* prioritisation rules:
            - prioritise fusions which don't skip exons
            - take both canonical if possible
            - favour 3' over 5' by canoncial, protein-coding then coding bases (or exon count if not coding)
            */

            long transScore = 0;
            long factor = 1000000000;

            if(fusion.getExonsSkipped(true) == 0 && fusion.getExonsSkipped(false) == 0)
                transScore += factor;

            factor /= 10;

            if(downTrans.isCanonical() && upTrans.isCanonical())
                transScore += factor;

            factor /= 10;

            if(downTrans.isCanonical())
                transScore += factor;

            factor /= 10;

            if(downTrans.bioType().equals(TRANSCRIPT_PROTEIN_CODING))
                transScore += factor;

            factor /= 100;

            long length = downTrans.isCoding() ? downTrans.calcCodingBases() : downTrans.ExonMax;

            // will be a range between 1-99 * current factor
            length = min(round(length/10), 99);
            transScore += length * factor;

            factor /= 10;

            if(upTrans.isCanonical())
                transScore += factor;

            factor /= 10;

            if(upTrans.bioType().equals(TRANSCRIPT_PROTEIN_CODING))
                transScore += factor;

            factor /= 100;

            length = upTrans.isCoding() ? upTrans.calcCodingBases() : upTrans.ExonMax;
            length = min(round(length/10), 99);
            transScore += length * factor;

            if(transScore > highestScore)
            {
                reportableFusion = fusion;
                highestScore = transScore;
            }
        }

        return reportableFusion;
    }

    public String getKnownFusionType(final Transcript upTrans, final Transcript downTrans)
    {
        if(mHasValidConfigData && mKnownFusionData == null)
            return REPORTABLE_TYPE_KNOWN;

        final String upGene = upTrans.gene().GeneName;
        final String downGene = downTrans.gene().GeneName;

        if(mKnownFusionData.hasKnownFusion(upGene, downGene))
            return REPORTABLE_TYPE_KNOWN;

        boolean fivePrimeMatch = mKnownFusionData.hasPromiscuousFiveGene(upGene);
        boolean threePrimeMatch = mKnownFusionData.hasPromiscuousThreeGene(downGene);

        boolean intergenicPromiscuousMatch = mKnownFusionData.intergenicPromiscuousMatch(upGene, downGene);

        boolean intragenicPromiscuousMatch = mKnownFusionData.intragenicPromiscuousMatch(upGene, downGene)
                && downTrans.ExonDownstream - upTrans.ExonUpstream > EXON_THRESHOLD;

        if(intergenicPromiscuousMatch || intragenicPromiscuousMatch)
        {
            if (fivePrimeMatch && threePrimeMatch)
                return REPORTABLE_TYPE_BOTH_PROM;
            else if (fivePrimeMatch)
                return REPORTABLE_TYPE_5P_PROM;
            else if (threePrimeMatch)
                return REPORTABLE_TYPE_3P_PROM;
            else
                return REPORTABLE_TYPE_NONE;
        }

        return REPORTABLE_TYPE_NONE;
    }

    private static boolean intragenic(final Transcript upstream, final Transcript downstream)
    {
        return upstream.gene().synonyms().stream().anyMatch(downstream.gene().synonyms()::contains);
    }

}
