package com.hartwig.hmftools.linx.fusion;

import static java.lang.Math.abs;
import static java.lang.Math.max;

import static com.hartwig.hmftools.common.io.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.formatPloidy;
import static com.hartwig.hmftools.linx.types.ResolvedType.LINE;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_END;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_START;
import static com.hartwig.hmftools.linx.types.SvVarData.isStart;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.dnds.DndsDriverGeneLikelihoodSupplier;
import com.hartwig.hmftools.common.variant.structural.annotation.EnsemblGeneData;
import com.hartwig.hmftools.common.variant.structural.annotation.ExonData;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneAnnotation;
import com.hartwig.hmftools.common.variant.structural.annotation.ImmutableReportableDisruption;
import com.hartwig.hmftools.common.variant.structural.annotation.ReportableDisruption;
import com.hartwig.hmftools.common.variant.structural.annotation.ReportableDisruptionFile;
import com.hartwig.hmftools.common.variant.structural.annotation.Transcript;
import com.hartwig.hmftools.common.variant.structural.annotation.TranscriptData;
import com.hartwig.hmftools.linx.chaining.SvChain;
import com.hartwig.hmftools.linx.gene.SvGeneTranscriptCollection;
import com.hartwig.hmftools.linx.types.ResolvedType;
import com.hartwig.hmftools.linx.types.SvBreakend;
import com.hartwig.hmftools.linx.types.SvCluster;
import com.hartwig.hmftools.linx.types.SvLinkedPair;
import com.hartwig.hmftools.linx.types.SvVarData;

import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DisruptionFinder
{
    private final SvGeneTranscriptCollection mGeneTransCollection;
    private Set<String> mDisruptionGeneIds;

    private final List<Transcript> mDisruptions;
    private final Map<Transcript,String> mRemovedDisruptions; // cached for diagnostic purposes

    private BufferedWriter mWriter;
    private final String mOutputDir;

    public static final int MAX_NON_DISRUPTED_CHAIN_LENGTH = 5000;

    private static final Logger LOGGER = LogManager.getLogger(DisruptionFinder.class);

    public DisruptionFinder(final CommandLine cmd, final SvGeneTranscriptCollection geneTransCache, final String outputDir)
    {
        mGeneTransCollection = geneTransCache;
        mDisruptionGeneIds = null;

        initialiseTsgDriverGenes();

        mDisruptions = Lists.newArrayList();
        mRemovedDisruptions = Maps.newHashMap();

        mOutputDir = outputDir;
        mWriter = null;

        initialise(cmd);
    }

    private void initialiseTsgDriverGenes()
    {
        mDisruptionGeneIds = Sets.newHashSet();

        for (String gene : DndsDriverGeneLikelihoodSupplier.tsgLikelihood().keySet())
        {
            final EnsemblGeneData geneData = mGeneTransCollection.getGeneDataByName(gene);

            if(geneData != null)
            {
                mDisruptionGeneIds.add(geneData.GeneId);
            }
        }
    }

    public final List<Transcript> getDisruptions() { return mDisruptions; }

    private void initialise(final CommandLine cmd)
    {
    }

    public boolean matchesDisruptionGene(final GeneAnnotation gene)
    {
        return mDisruptionGeneIds.stream().anyMatch(geneId -> gene.StableId.equals(geneId));
    }

    public void addDisruptionGene(final String geneId)
    {
        if(!mDisruptionGeneIds.contains(geneId))
            mDisruptionGeneIds.add(geneId);
    }

    public void markTranscriptsDisruptive(final List<SvVarData> svList)
    {
        mRemovedDisruptions.clear();

        for(final SvVarData var : svList)
        {
            markTranscriptsDisruptive(var);

            // inferred SGLs are always non-disruptive
            if (var.isInferredSgl())
            {
                var.getGenesList(true).stream().forEach(x -> x.transcripts().stream().forEach(y -> y.setIsDisruptive(false)));
            }
        }
    }

    private static final String NON_DISRUPT_REASON_SIMPLE_SV = "SimpleSV";
    private static final String NON_DISRUPT_REASON_LINE = "LINE";

    private void markTranscriptsDisruptive(final SvVarData var)
    {
        final List<GeneAnnotation> genesStart = var.getGenesList(true);
        final List<GeneAnnotation> genesEnd = var.getGenesList(false);

        if(genesStart.isEmpty() && genesEnd.isEmpty())
            return;

        /* test the breakend to see if:
            - it isn't chained - revert to simple disrupted definitions
            - it is chained
                - the chain returns to the same intro
                - the chain traverses a splice acceptor in any direction
        */

        final SvCluster cluster = var.getCluster();

        // set the undisrupted copy number against all canonical transcripts
        for(int se = SE_START; se <= SE_END; ++se)
        {
            if(se == SE_END && var.isSglBreakend())
                continue;

            final SvBreakend breakend = var.getBreakend(se);
            double undisruptedCopyNumber = getUndisruptedCopyNumber(breakend);

            final List<GeneAnnotation> svGenes = se == SE_START ? genesStart : genesEnd;

            for(GeneAnnotation gene : svGenes)
            {
                Transcript canonicalTrans = gene.canonical();

                if(canonicalTrans != null)
                    canonicalTrans.setUndisruptedCopyNumber(undisruptedCopyNumber);

                // line clusters can insert into an intron and look disruptive if a single breakend is involved,
                // but are only inserting a (non-disruptive) shard
                if(cluster.getResolvedType() == LINE)
                {
                    gene.transcripts().stream()
                            .filter(x -> x.isIntronic())
                            .filter(x -> x.isDisruptive())
                            .forEach(x -> markNonDisruptiveTranscript(x, NON_DISRUPT_REASON_LINE));
                }
            }
        }

        if(var.isSglBreakend())
            return;

        boolean isSimpleSV = var.isSimpleType();

        if(isSimpleSV)
        {
            markNonDisruptiveGeneTranscripts(genesStart, genesEnd, NON_DISRUPT_REASON_SIMPLE_SV);
        }

        final List<SvChain> chains = cluster.findChains(var);

        // first test each breakend in turn to see if it forms a TI wholy within an intron and where the other breakends
        // of the TI are non-genic

        for(int se = SE_START; se <= SE_END; ++se)
        {
            final SvBreakend breakend = var.getBreakend(se);

            final List<GeneAnnotation> svGenes = se == SE_START ? genesStart : genesEnd;

            List<Transcript> transList = getDisruptedTranscripts(svGenes);

            if(transList.isEmpty())
                continue;

            boolean otherBreakendNonGenic = se == SE_START ? genesEnd.isEmpty() : genesStart.isEmpty();

            final List<SvLinkedPair> links = var.getLinkedPairs(breakend.usesStart());

            for(final SvLinkedPair pair : links)
            {
                final SvBreakend otherSvBreakend = pair.getOtherBreakend(breakend);

                List<GeneAnnotation> otherGenes = otherSvBreakend.getSV().getGenesList(otherSvBreakend.usesStart());
                List<Transcript> otherTransList = getDisruptedTranscripts(otherGenes);

                boolean otherSvOtherBreakendNonGenic = otherSvBreakend.getSV().getGenesList(!otherSvBreakend.usesStart()).isEmpty();

                if(otherBreakendNonGenic && otherSvOtherBreakendNonGenic)
                {
                    if(markNonDisruptiveTranscripts(transList, otherTransList, "IntronicSection"))
                    {
                        LOGGER.debug("pair({}) length({}) fully intronic)", pair, pair.length());
                        removeNonDisruptedTranscripts(transList);
                    }
                }
            }

            // next test for a breakend which returns to the same intro via a chain with the same orientation
            // without passing through any splice acceptors
            if(!transList.isEmpty())
            {
                for(SvChain chain : chains)
                {
                    checkChainedTranscripts(breakend, chain, transList);
                }
            }
        }
    }

    private static final List<Transcript> getDisruptedTranscripts(final List<GeneAnnotation> genes)
    {
        List<Transcript> transList = Lists.newArrayList();

        for (final GeneAnnotation gene : genes)
        {
            transList.addAll(gene.transcripts().stream().filter(x -> x.isDisruptive()).collect(Collectors.toList()));
        }

        return transList;
    }

    private static void removeNonDisruptedTranscripts(final List<Transcript> transList)
    {
        int index = 0;

        while(index < transList.size())
        {
            if(transList.get(index).isDisruptive())
                ++index;
            else
                transList.remove(index);
        }
    }

    private void checkChainedTranscripts(final SvBreakend breakend, final SvChain chain, final List<Transcript> transList)
    {
        // an SV whose breakends are not both within the same intron disrupts those transcripts unless
        // a) one breakend isn't genic and the other forms a TI whole within an intron OR
        // b) both breakends are in chained sections which come back to the same intro with correct orientation and
        // without traversing a splice acceptor OR

        final List<SvLinkedPair> links = chain.getLinkedPairs();

        for(int i = 0; i < links.size(); ++i)
        {
            int startIndex = 0;
            boolean traverseUp = false;

            if(i == 0 && chain.getOpenBreakend(true) == breakend)
            {
                startIndex = -1;
                traverseUp = true;
            }
            else if(i == links.size() - 1 && chain.getOpenBreakend(false) == breakend)
            {
                startIndex = links.size();
                traverseUp = false;
            }
            else if(links.get(i).hasBreakend(breakend))
            {
                startIndex = i;
                traverseUp = links.get(i).secondBreakend() == breakend;
            }
            else
            {
                continue;
            }

            int index = startIndex;
            long chainLength = 0;

            while(true)
            {
                index += traverseUp ? 1 : -1;

                if(index < 0 || index >= links.size())
                    break;

                final SvLinkedPair nextPair = links.get(index);

                // does this next link traverse another splice acceptor?
                boolean traversesGene = pairTraversesGene(nextPair, 0, false);

                if(traversesGene)
                    return;

                chainLength += nextPair.length();

                if(chainLength > MAX_NON_DISRUPTED_CHAIN_LENGTH)
                    return;

                // no need to chain this breakend any further as soon as any of its chains have crossed another splice acceptor

                // does it return to the same intron and correct orientation for any transcripts
                final SvBreakend nextBreakend = traverseUp ?
                        nextPair.secondBreakend().getOtherBreakend() : nextPair.firstBreakend().getOtherBreakend();

                if(nextBreakend == null)
                    continue;

                if(nextBreakend.orientation() == breakend.orientation() || !nextBreakend.chromosome().equals(breakend.chromosome()))
                    continue;

                List<GeneAnnotation> otherGenes = nextBreakend.getSV().getGenesList(nextBreakend.usesStart());

                if(otherGenes.isEmpty())
                    continue;

                List<Transcript> otherTransList = getDisruptedTranscripts(otherGenes);

                if(!otherTransList.isEmpty())
                {
                    String contextInfo = String.format("SameIntronNoSPA;%d-%d", abs(index - startIndex), chainLength);

                    if (markNonDisruptiveTranscripts(transList, otherTransList, contextInfo))
                    {
                        removeNonDisruptedTranscripts(transList);

                        LOGGER.debug("breakends({} & {}) return to same intron, chain({}) links({}) length({})",
                                breakend, nextBreakend, chain.id(), abs(index - startIndex), chainLength);

                        if (transList.isEmpty())
                            return;
                    }
                }
            }
        }
    }

    private void markNonDisruptiveGeneTranscripts(
            final List<GeneAnnotation> genesStart, final List<GeneAnnotation> genesEnd, final String context)
    {
        for(final GeneAnnotation geneStart : genesStart)
        {
            final GeneAnnotation geneEnd = genesEnd.stream()
                    .filter(x -> x.StableId.equals(geneStart.StableId)).findFirst().orElse(null);

            if(geneEnd == null)
                continue;

            markNonDisruptiveTranscripts(geneStart.transcripts(), geneEnd.transcripts(), context);
        }
    }

    private boolean markNonDisruptiveTranscripts(final List<Transcript> transList1, final List<Transcript> transList2, final String context)
    {
        boolean foundMatchingTrans = false;

        for (final Transcript trans1 : transList1)
        {
            final Transcript trans2 = transList2.stream()
                    .filter(x -> x.StableId.equals(trans1.StableId)).findFirst().orElse(null);

            if(trans2 == null)
                continue;

            if(trans1.ExonUpstream == trans2.ExonUpstream && !trans1.isExonic() && !trans2.isExonic())
            {
                foundMatchingTrans = true;

                markNonDisruptiveTranscript(trans1, context);
                markNonDisruptiveTranscript(trans2, context);
            }
        }

        return foundMatchingTrans;
    }

    private void markNonDisruptiveTranscript(final Transcript transcript, final String context)
    {
        transcript.setIsDisruptive(false);
        registerNonDisruptedTranscript(transcript, context);
    }

    private void registerNonDisruptedTranscript(final Transcript transcript, final String context)
    {
        if(mRemovedDisruptions.containsKey(transcript))
            return;

        if(!transcript.isCanonical() || !matchesDisruptionGene(transcript.gene()))
            return;

        LOGGER.debug("excluding gene({}) svId({}) reason({})", transcript.geneName(), transcript.gene().id(), context);

        mRemovedDisruptions.put(transcript, context);
    }

    public boolean pairTraversesGene(SvLinkedPair pair, int fusionDirection, boolean isPrecodingUpstream)
    {
        // for this pair to not affect the fusion, the section it traverses cannot cross any gene's splice acceptor
        // with the same strand direction unless that is part of a fully traversed non-coding 5' exon

        long lowerPos = pair.getBreakend(true).position();
        long upperPos = pair.getBreakend(false).position();

        List<EnsemblGeneData> geneDataList = mGeneTransCollection.getChrGeneDataMap().get(pair.chromosome());

        if(geneDataList == null)
            return false;

        for(EnsemblGeneData geneData : geneDataList)
        {
            if(lowerPos > geneData.GeneEnd)
                continue;

            if(upperPos < geneData.GeneStart)
                break;

            if(fusionDirection == 0 || geneData.Strand == fusionDirection)
            {
                // check whether a splice acceptor is encountered within this window
                List<TranscriptData> transDataList = mGeneTransCollection.getTranscripts(geneData.GeneId);

                if(transDataList == null)
                    continue;

                for(final TranscriptData transData : transDataList)
                {
                    for (final ExonData exonData : transData.exons())
                    {
                        if (exonData.ExonRank == 1)
                            continue;

                        if ((geneData.Strand == 1 && lowerPos <= exonData.ExonStart && upperPos >= exonData.ExonStart)
                                || (geneData.Strand == -1 && lowerPos <= exonData.ExonEnd && upperPos >= exonData.ExonEnd))
                        {
                            // allow an exon to be fully traversed if the upstream transcript is pre-coding
                            if (isPrecodingUpstream && lowerPos <= exonData.ExonStart && upperPos >= exonData.ExonEnd)
                            {
                                if (geneData.Strand == 1 && (transData.CodingStart == null || upperPos < transData.CodingStart))
                                    continue;
                                else if (geneData.Strand == -1 && (transData.CodingEnd == null || lowerPos > transData.CodingEnd))
                                    continue;
                            }

                            LOGGER.trace("pair({}) direction({}) traverses splice acceptor({} {}) exon(rank{} pos={})",
                                    pair.toString(), fusionDirection, geneData.GeneName, transData.TransName,
                                    exonData.ExonRank, exonData.ExonStart, exonData.ExonEnd);

                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    // to be deprecated
    private static boolean areDisruptivePair(final Transcript trans1, final Transcript trans2)
    {
        if(trans1.gene().id() != trans2.gene().id())
            return true;

        if(!trans1.StableId.equals(trans2.StableId))
            return true;

        // only DELs, DUPs and INS
        if(trans1.gene().orientation() == trans2.gene().orientation())
            return true;

        if(trans1.ExonUpstream != trans2.ExonUpstream)
            return true;

        return false;
    }

    public void findReportableDisruptions(final List<SvVarData> svList)
    {
        mDisruptions.clear();

        for (final SvVarData var : svList)
        {
            for (int be = SE_START; be <= SE_END; ++be)
            {
                if (be == SE_END && var.isSglBreakend())
                    continue;

                final List<GeneAnnotation> tsgGenesList = var.getGenesList(isStart(be)).stream()
                        .filter(x -> matchesDisruptionGene(x)).collect(Collectors.toList());

                if(tsgGenesList.isEmpty())
                    continue;

                final SvBreakend breakend = var.getBreakend(be);

                for(GeneAnnotation gene : tsgGenesList)
                {
                    List<Transcript> reportableDisruptions = gene.transcripts().stream()
                            .filter(Transcript::isCanonical)
                            .filter(Transcript::isDisruptive)
                            .collect(Collectors.toList());

                    for(Transcript transcript : reportableDisruptions)
                    {
                        LOGGER.debug("var({}) breakend({}) gene({}) transcript({}) is disrupted, cnLowside({})",
                                var.id(), var.getBreakend(be), gene.GeneName, transcript.StableId,
                                formatPloidy(transcript.undisruptedCopyNumber()));

                        transcript.setReportableDisruption(true);
                        mDisruptions.add(transcript);
                    }
                }
            }
        }
    }

    private static double getUndisruptedCopyNumber(final SvBreakend breakend)
    {
        double cnLowSide = breakend.copyNumberLowSide();

        final SvLinkedPair dbLink = breakend.getDBLink();
        if(dbLink != null && dbLink.length() < 0)
        {
            double otherSvPloidy = dbLink.getOtherBreakend(breakend).ploidy();
            cnLowSide -= otherSvPloidy;
        }

        return cnLowSide;
    }

    public void writeSampleData(final String sampleId)
    {
        // write sample file for patient reporter
        List<ReportableDisruption> reportedDisruptions = Lists.newArrayList();

        for(final Transcript transcript : mDisruptions)
        {
            final GeneAnnotation gene = transcript.gene();

            reportedDisruptions.add(ImmutableReportableDisruption.builder()
                    .svId(gene.id())
                    .chromosome(gene.chromosome())
                    .orientation(gene.orientation())
                    .strand(gene.Strand)
                    .chrBand(gene.karyotypeBand())
                    .gene(transcript.geneName())
                    .type(gene.type().toString())
                    .ploidy(gene.ploidy())
                    .exonUp(transcript.ExonUpstream)
                    .exonDown(transcript.ExonDownstream)
                    .undisruptedCopyNumber(max(transcript.undisruptedCopyNumber(),0))
                    .build());
        }

        try
        {
            final String disruptionsFile = ReportableDisruptionFile.generateFilename(mOutputDir, sampleId);
            ReportableDisruptionFile.write(disruptionsFile, reportedDisruptions);
        }
        catch(IOException e)
        {
            LOGGER.error("failed to write sample disruptions file: {}", e.toString());
        }
    }

    public void initialiseOutputFile(final String fileName)
    {
        try
        {
            if(mWriter == null)
            {
                String outputFilename = mOutputDir + fileName;

                mWriter = createBufferedWriter(outputFilename, false);

                mWriter.write("SampleId,Reportable,SvId,IsStart,Type,ClusterId,Chromosome,Position,Orientation");
                mWriter.write(",GeneId,GeneName,Strand,TransId,ExonUp,ExonDown,CodingType,RegionType");
                mWriter.write(",UndisruptedCN,ExcludedReason,ExtraInfo");
                mWriter.newLine();
            }
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing disruptions: {}", e.toString());
        }
    }

    public void writeMultiSampleData(final String sampleId, final List<SvVarData> svList)
    {
        if(mWriter == null)
            return;

        try
        {
            for(final Transcript transcript : mDisruptions)
            {
                final GeneAnnotation gene = transcript.gene();
                final SvVarData var = svList.stream().filter(x -> x.id() == gene.id()).findFirst().orElse(null);

                mWriter.write(String.format("%s,%s,%d,%s,%s,%d,%s,%d,%d",
                        sampleId, transcript.reportableDisruption(), gene.id(), gene.isStart(),
                        var != null ? var.type() : "", var != null ? var.getCluster().id() : -1,
                        gene.chromosome(), gene.position(), gene.orientation()));

                mWriter.write(String.format(",%s,%s,%d,%s,%d,%d,%s,%s,%.2f,,",
                        gene.StableId, gene.GeneName, gene.Strand, transcript.StableId,
                        transcript.ExonUpstream, transcript.ExonDownstream, transcript.codingType(), transcript.regionType(),
                        transcript.undisruptedCopyNumber()));

                mWriter.newLine();
            }

            for(Map.Entry<Transcript,String> entry : mRemovedDisruptions.entrySet())
            {
                final String exclusionInfo = entry.getValue();

                if(exclusionInfo.equals(NON_DISRUPT_REASON_SIMPLE_SV))
                    continue;

                final Transcript transcript = entry.getKey();
                final GeneAnnotation gene = transcript.gene();
                final SvVarData var = svList.stream().filter(x -> x.id() == gene.id()).findFirst().orElse(null);

                mWriter.write(String.format("%s,%s,%d,%s,%s,%d,%s,%d,%d",
                    sampleId, transcript.reportableDisruption(), gene.id(), gene.isStart(),
                    var != null ? var.type() : "", var != null ? var.getCluster().id() : -1,
                    gene.chromosome(), gene.position(), gene.orientation()));


                String exclusionReason = exclusionInfo;
                String extraInfo = "";

                String[] contextInfo = exclusionInfo.split(";");

                if(contextInfo.length == 2)
                {
                    exclusionReason = contextInfo[0];
                    extraInfo = contextInfo[1];
                }

                mWriter.write(String.format(",%s,%s,%d,%s,%d,%d,%s,%s,%.2f,%s,%s",
                        gene.StableId, gene.GeneName, gene.Strand, transcript.StableId,
                        transcript.ExonUpstream, transcript.ExonDownstream, transcript.codingType(),
                        transcript.regionType(), transcript.undisruptedCopyNumber(), exclusionReason, extraInfo));

                mWriter.newLine();
            }
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing fusions: {}", e.toString());
        }
    }

    public void close()
    {
        closeBufferedWriter(mWriter);
    }

    // simple disruption routine - now deprecated
    /*
    public void markNonDisruptiveTranscripts(final SvVarData var)
    {
        if(!var.isSimpleType())
            return;

        final List<GeneAnnotation> genesStart = var.getGenesList(true);
        final List<GeneAnnotation> genesEnd = var.getGenesList(false);

        if(genesStart.isEmpty() || genesEnd.isEmpty())
            return;

        for(final GeneAnnotation geneStart : genesStart)
        {
            final GeneAnnotation geneEnd = genesEnd.stream()
                    .filter(x -> x.StableId.equals(geneStart.StableId)).findFirst().orElse(null);

            if(geneEnd == null)
                continue;

            for (final Transcript transStart : geneStart.transcripts())
            {
                final Transcript transEnd = geneEnd.transcripts().stream()
                        .filter(x -> x.StableId.equals(transStart.StableId)).findFirst().orElse(null);

                if(transEnd == null)
                    continue;

                if(!transEnd.isExonic() && !transStart.isExonic() && transStart.ExonUpstream == transEnd.ExonUpstream)
                {
                    if(transEnd.isCanonical() && matchesDisruptionGene(transEnd.gene()))
                    {
                        LOGGER.debug("SV({}) gene({}:{}) intronic section({}-{}) marked non-disruptive",
                                var.id(), transStart.geneName(), transStart.StableId, transStart.ExonUpstream, transStart.ExonDownstream);
                    }

                    transStart.setIsDisruptive(false);
                    transEnd.setIsDisruptive(false);
                }
            }
        }
    }
    */

}
