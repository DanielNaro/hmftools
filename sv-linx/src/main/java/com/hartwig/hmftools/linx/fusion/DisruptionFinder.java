package com.hartwig.hmftools.linx.fusion;

import static java.lang.Math.abs;

import static com.hartwig.hmftools.common.fusion.TranscriptRegionType.EXONIC;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.isStart;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.DUP;
import static com.hartwig.hmftools.linx.LinxConfig.LNX_LOGGER;
import static com.hartwig.hmftools.linx.LinxOutput.ITEM_DELIM;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.formatJcn;
import static com.hartwig.hmftools.linx.types.ResolvedType.LINE;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.drivercatalog.panel.DriverGene;
import com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache;
import com.hartwig.hmftools.common.ensemblcache.EnsemblGeneData;
import com.hartwig.hmftools.common.ensemblcache.ExonData;
import com.hartwig.hmftools.common.ensemblcache.TranscriptData;
import com.hartwig.hmftools.common.fusion.BreakendGeneData;
import com.hartwig.hmftools.common.fusion.BreakendTransData;
import com.hartwig.hmftools.linx.LinxConfig;
import com.hartwig.hmftools.linx.chaining.SvChain;
import com.hartwig.hmftools.linx.types.DbPair;
import com.hartwig.hmftools.linx.types.LinkedPair;
import com.hartwig.hmftools.linx.types.SvBreakend;
import com.hartwig.hmftools.linx.types.SvCluster;
import com.hartwig.hmftools.linx.types.SvVarData;

public class DisruptionFinder
{
    private final EnsemblDataCache mGeneTransCache;
    private final List<String> mDisruptionGeneIds;

    private final List<BreakendTransData> mDisruptions;
    private final Map<BreakendTransData,String> mRemovedDisruptions; // cached for diagnostic purposes

    private BufferedWriter mWriter;
    private final String mOutputDir;

    public static final int MAX_NON_DISRUPTED_CHAIN_LENGTH = 5000;

    public DisruptionFinder(final LinxConfig config, final EnsemblDataCache geneTransCache)
    {
        mGeneTransCache = geneTransCache;
        mDisruptionGeneIds = disruptionGeneIds(config.DriverGenes, geneTransCache);

        mDisruptions = Lists.newArrayList();
        mRemovedDisruptions = Maps.newHashMap();

        mOutputDir = config.OutputDataPath;
        mWriter = null;
    }

    public static List<String> disruptionGeneIds(final List<DriverGene> driverGenes, final EnsemblDataCache geneTransCache)
    {
        return driverGenes.stream()
                .filter(x -> x.reportDisruption())
                .map(x -> geneTransCache.getGeneDataByName(x.gene()))
                .filter(x -> x != null)
                .map(x -> x.GeneId)
                .collect(Collectors.toList());
    }

    public final List<BreakendTransData> getDisruptions() { return mDisruptions; }

    public boolean matchesDisruptionGene(final BreakendGeneData gene)
    {
        return mDisruptionGeneIds.stream().anyMatch(geneId -> gene.StableId.equals(geneId));
    }

    public void addDisruptionGene(final String geneId)
    {
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
        final List<BreakendGeneData> genesStart = var.getGenesList(true);
        final List<BreakendGeneData> genesEnd = var.getGenesList(false);

        if(genesStart.isEmpty() && genesEnd.isEmpty())
            return;

        /* test the breakend to see if:
            - it isn't chained - revert to simple disrupted definitions
            - it is chained
                - the chain returns to the same intron
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

            final List<BreakendGeneData> svGenes = se == SE_START ? genesStart : genesEnd;

            for(BreakendGeneData gene : svGenes)
            {
                BreakendTransData canonicalTrans = gene.canonical();

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
            markNonDisruptiveGeneTranscripts(var, genesStart, genesEnd, NON_DISRUPT_REASON_SIMPLE_SV);
        }

        final List<SvChain> chains = cluster.findChains(var);

        // first test each breakend in turn to see if it forms a TI wholly within an intron and where the other breakends
        // of the TI are non-genic

        for(int se = SE_START; se <= SE_END; ++se)
        {
            final SvBreakend breakend = var.getBreakend(se);

            final List<BreakendGeneData> svGenes = se == SE_START ? genesStart : genesEnd;

            List<BreakendTransData> transList = getDisruptedTranscripts(svGenes);

            if(transList.isEmpty())
                continue;

            boolean otherBreakendNonGenic = se == SE_START ? genesEnd.isEmpty() : genesStart.isEmpty();

            final List<LinkedPair> links = var.getLinkedPairs(breakend.usesStart());

            for(final LinkedPair pair : links)
            {
                final SvBreakend otherSvBreakend = pair.getOtherBreakend(breakend);

                List<BreakendGeneData> otherGenes = otherSvBreakend.getSV().getGenesList(otherSvBreakend.usesStart());
                List<BreakendTransData> otherTransList = getDisruptedTranscripts(otherGenes);

                boolean otherSvOtherBreakendNonGenic = otherSvBreakend.getSV().getGenesList(!otherSvBreakend.usesStart()).isEmpty();

                if(otherBreakendNonGenic && otherSvOtherBreakendNonGenic)
                {
                    if(markNonDisruptiveTranscripts(transList, otherTransList, "IntronicSection"))
                    {
                        LNX_LOGGER.debug("pair({}) length({}) fully intronic)", pair, pair.length());
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

    private static List<BreakendTransData> getDisruptedTranscripts(final List<BreakendGeneData> genes)
    {
        List<BreakendTransData> transList = Lists.newArrayList();

        for (final BreakendGeneData gene : genes)
        {
            transList.addAll(gene.transcripts().stream().filter(x -> x.isDisruptive()).collect(Collectors.toList()));
        }

        return transList;
    }

    private static void removeNonDisruptedTranscripts(final List<BreakendTransData> transList)
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

    public boolean isNonDisruptiveChainedPair(final BreakendTransData upTrans, final List<BreakendGeneData> downGenesList)
    {
        if(upTrans.regionType() == EXONIC)
            return false;

        // check whether these breakends may belong to the same non-disrupted segment in any upstream transcript
        final BreakendGeneData downGene = downGenesList.stream()
                .filter(x -> x.StableId.equals(upTrans.gene().StableId)).findFirst().orElse(null);

        if(downGene == null)
            return false;

        for(BreakendTransData downTrans : downGene.transcripts())
        {
            if(downTrans.transId() != upTrans.transId())
                continue;

            if(downTrans.regionType() == upTrans.regionType() && downTrans.ExonUpstream == upTrans.ExonUpstream)
                return true;
        }

        return false;
    }

    private void checkChainedTranscripts(final SvBreakend breakend, final SvChain chain, final List<BreakendTransData> transList)
    {
        // an SV whose breakends are not both within the same intron disrupts those transcripts unless
        // a) one breakend isn't genic and the other forms a TI whole within an intron OR
        // b) both breakends are in chained sections which come back to the same intro with correct orientation and
        // without traversing a splice acceptor OR

        final List<LinkedPair> links = chain.getLinkedPairs();

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

                final LinkedPair nextPair = links.get(index);

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

                List<BreakendGeneData> otherGenes = nextBreakend.getSV().getGenesList(nextBreakend.usesStart());

                if(otherGenes.isEmpty())
                    continue;

                List<BreakendTransData> otherTransList = getDisruptedTranscripts(otherGenes);

                if(!otherTransList.isEmpty())
                {
                    String contextInfo = String.format("SameIntronNoSPA;%d-%d", abs(index - startIndex), chainLength);

                    if (markNonDisruptiveTranscripts(transList, otherTransList, contextInfo))
                    {
                        removeNonDisruptedTranscripts(transList);

                        LNX_LOGGER.debug("breakends({} & {}) return to same intron, chain({}) links({}) length({})",
                                breakend, nextBreakend, chain.id(), abs(index - startIndex), chainLength);

                        if (transList.isEmpty())
                            return;
                    }
                }
            }
        }
    }

    private void markNonDisruptiveGeneTranscripts(
            final SvVarData var, final List<BreakendGeneData> genesStart, final List<BreakendGeneData> genesEnd, final String context)
    {
        for(final BreakendGeneData geneStart : genesStart)
        {
            final BreakendGeneData geneEnd = genesEnd.stream()
                    .filter(x -> x.StableId.equals(geneStart.StableId)).findFirst().orElse(null);

            if(geneEnd == null)
                continue;

            if(var.type() == DUP)
            {
                markNonDisruptiveDups(
                        geneStart.isUpstream() ? geneStart.transcripts() : geneEnd.transcripts(),
                        !geneEnd.isUpstream() ? geneEnd.transcripts() : geneStart.transcripts());
            }

            markNonDisruptiveTranscripts(geneStart.transcripts(), geneEnd.transcripts(), context);
        }
    }

    private boolean markNonDisruptiveTranscripts(final List<BreakendTransData> transList1, final List<BreakendTransData> transList2, final String context)
    {
        boolean foundMatchingTrans = false;

        for (final BreakendTransData trans1 : transList1)
        {
            final BreakendTransData trans2 = transList2.stream()
                    .filter(x -> x.transName().equals(trans1.transName())).findFirst().orElse(null);

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

    private boolean markNonDisruptiveDups(final List<BreakendTransData> upstreamTransList, final List<BreakendTransData> downstreamTransList)
    {
        // special case of a DUP around the 1st exon which since it doesn't have a splice acceptor does not change the transcript
        boolean foundMatchingTrans = false;

        for (final BreakendTransData upTrans : upstreamTransList)
        {
            final BreakendTransData downTrans = downstreamTransList.stream()
                    .filter(x -> x.transName().equals(upTrans.transName())).findFirst().orElse(null);

            if(downTrans == null)
                continue;

            if(upTrans.ExonUpstream == 1 && downTrans.ExonDownstream <= 2 && !upTrans.isExonic())
            {
                foundMatchingTrans = true;

                markNonDisruptiveTranscript(upTrans, NON_DISRUPT_REASON_SIMPLE_SV);
                markNonDisruptiveTranscript(downTrans, NON_DISRUPT_REASON_SIMPLE_SV);
            }
        }

        return foundMatchingTrans;
    }

    private void markNonDisruptiveTranscript(final BreakendTransData transcript, final String context)
    {
        transcript.setIsDisruptive(false);
        registerNonDisruptedTranscript(transcript, context);
    }

    private void registerNonDisruptedTranscript(final BreakendTransData transcript, final String context)
    {
        if(mRemovedDisruptions.containsKey(transcript))
            return;

        if(!transcript.isCanonical() || !matchesDisruptionGene(transcript.gene()))
            return;

        LNX_LOGGER.debug("excluding gene({}) svId({}) reason({})", transcript.geneName(), transcript.gene().id(), context);

        mRemovedDisruptions.put(transcript, context);
    }

    public boolean pairTraversesGene(final LinkedPair pair, int fusionDirection, boolean isPrecodingUpstream)
    {
        // for this pair to not affect the fusion, the section it traverses cannot cross any gene's splice acceptor
        // with the same strand direction unless that is part of a fully traversed non-coding 5' exon

        int lowerPos = pair.getBreakend(true).position();
        int upperPos = pair.getBreakend(false).position();

        List<EnsemblGeneData> geneDataList = mGeneTransCache.getChrGeneDataMap().get(pair.chromosome());

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
                List<TranscriptData> transDataList = mGeneTransCache.getTranscripts(geneData.GeneId);

                if(transDataList == null)
                    continue;

                for(final TranscriptData transData : transDataList)
                {
                    for (final ExonData exonData : transData.exons())
                    {
                        if (exonData.Rank == 1)
                            continue;

                        if ((geneData.Strand == 1 && lowerPos <= exonData.Start && upperPos >= exonData.Start)
                        || (geneData.Strand == -1 && lowerPos <= exonData.End && upperPos >= exonData.End))
                        {
                            // allow an exon to be fully traversed if the upstream transcript is pre-coding
                            if (isPrecodingUpstream && lowerPos <= exonData.Start && upperPos >= exonData.End)
                            {
                                if (geneData.Strand == 1 && (transData.CodingStart == null || upperPos < transData.CodingStart))
                                    continue;
                                else if (geneData.Strand == -1 && (transData.CodingEnd == null || lowerPos > transData.CodingEnd))
                                    continue;
                            }

                            LNX_LOGGER.trace("pair({}) direction({}) traverses splice acceptor({} {}) exon(rank{} pos={})",
                                    pair.toString(), fusionDirection, geneData.GeneName, transData.TransName,
                                    exonData.Rank, exonData.Start, exonData.End);

                            return true;
                        }
                    }
                }
            }
        }

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

                final List<BreakendGeneData> tsgGenesList = var.getGenesList(isStart(be)).stream()
                        .filter(x -> matchesDisruptionGene(x)).collect(Collectors.toList());

                if(tsgGenesList.isEmpty())
                    continue;

                for(BreakendGeneData gene : tsgGenesList)
                {
                    List<BreakendTransData> reportableDisruptions = gene.transcripts().stream()
                            .filter(BreakendTransData::isCanonical)
                            .filter(BreakendTransData::isDisruptive)
                            .collect(Collectors.toList());

                    for(BreakendTransData transcript : reportableDisruptions)
                    {
                        LNX_LOGGER.debug("var({}) breakend({}) gene({}) transcript({}) is disrupted, cnLowside({})",
                                var.id(), var.getBreakend(be), gene.GeneName, transcript.transName(),
                                formatJcn(transcript.undisruptedCopyNumber()));

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

        final DbPair dbLink = breakend.getDBLink();
        if(dbLink != null && dbLink.length() < 0)
        {
            cnLowSide -= dbLink.getOtherBreakend(breakend).jcn();
        }

        return cnLowSide;
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
            LNX_LOGGER.error("error writing disruptions: {}", e.toString());
        }
    }

    public void writeMultiSampleData(final String sampleId, final List<SvVarData> svList)
    {
        if(mWriter == null)
            return;

        try
        {
            for(final BreakendTransData transcript : mDisruptions)
            {
                final BreakendGeneData gene = transcript.gene();
                final SvVarData var = svList.stream().filter(x -> x.id() == gene.id()).findFirst().orElse(null);

                mWriter.write(String.format("%s,%s,%d,%s,%s,%d,%s,%d,%d",
                        sampleId, transcript.reportableDisruption(), gene.id(), gene.isStart(),
                        var != null ? var.type() : "", var != null ? var.getCluster().id() : -1,
                        gene.chromosome(), gene.position(), gene.orientation()));

                mWriter.write(String.format(",%s,%s,%d,%s,%d,%d,%s,%s,%.2f,,",
                        gene.StableId, gene.GeneName, gene.Strand, transcript.transName(),
                        transcript.ExonUpstream, transcript.ExonDownstream, transcript.codingType(), transcript.regionType(),
                        transcript.undisruptedCopyNumber()));

                mWriter.newLine();
            }

            for(Map.Entry<BreakendTransData,String> entry : mRemovedDisruptions.entrySet())
            {
                final String exclusionInfo = entry.getValue();

                if(exclusionInfo.equals(NON_DISRUPT_REASON_SIMPLE_SV))
                    continue;

                final BreakendTransData transcript = entry.getKey();
                final BreakendGeneData gene = transcript.gene();
                final SvVarData var = svList.stream().filter(x -> x.id() == gene.id()).findFirst().orElse(null);

                mWriter.write(String.format("%s,%s,%d,%s,%s,%d,%s,%d,%d",
                    sampleId, transcript.reportableDisruption(), gene.id(), gene.isStart(),
                    var != null ? var.type() : "", var != null ? var.getCluster().id() : -1,
                    gene.chromosome(), gene.position(), gene.orientation()));


                String exclusionReason = exclusionInfo;
                String extraInfo = "";

                String[] contextInfo = exclusionInfo.split(ITEM_DELIM);

                if(contextInfo.length == 2)
                {
                    exclusionReason = contextInfo[0];
                    extraInfo = contextInfo[1];
                }

                mWriter.write(String.format(",%s,%s,%d,%s,%d,%d,%s,%s,%.2f,%s,%s",
                        gene.StableId, gene.GeneName, gene.Strand, transcript.transName(),
                        transcript.ExonUpstream, transcript.ExonDownstream, transcript.codingType(),
                        transcript.regionType(), transcript.undisruptedCopyNumber(), exclusionReason, extraInfo));

                mWriter.newLine();
            }
        }
        catch (final IOException e)
        {
            LNX_LOGGER.error("error writing fusions: {}", e.toString());
        }
    }

    public void close()
    {
        closeBufferedWriter(mWriter);
    }

}
