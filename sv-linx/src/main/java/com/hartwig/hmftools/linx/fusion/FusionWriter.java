package com.hartwig.hmftools.linx.fusion;

import static com.hartwig.hmftools.common.io.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.common.variant.structural.annotation.ReportableGeneFusionFile.context;
import static com.hartwig.hmftools.common.variant.structural.annotation.ReportableGeneFusionFile.fusionPloidy;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_END;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_START;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.variant.structural.annotation.FusionAnnotations;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneAnnotation;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneFusion;
import com.hartwig.hmftools.common.variant.structural.annotation.ImmutableReportableGeneFusion;
import com.hartwig.hmftools.common.variant.structural.annotation.ReportableDisruption;
import com.hartwig.hmftools.common.variant.structural.annotation.ReportableGeneFusion;
import com.hartwig.hmftools.common.variant.structural.annotation.ReportableGeneFusionFile;
import com.hartwig.hmftools.common.variant.structural.annotation.Transcript;
import com.hartwig.hmftools.common.variant.structural.linx.ImmutableLinxBreakend;
import com.hartwig.hmftools.common.variant.structural.linx.ImmutableLinxFusion;
import com.hartwig.hmftools.common.variant.structural.linx.LinxBreakend;
import com.hartwig.hmftools.common.variant.structural.linx.LinxBreakendFile;
import com.hartwig.hmftools.common.variant.structural.linx.LinxFusion;
import com.hartwig.hmftools.common.variant.structural.linx.LinxFusionFile;
import com.hartwig.hmftools.linx.visualiser.file.VisFusionFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FusionWriter
{
    private final String mOutputDir;
    private BufferedWriter mFusionWriter;
    private BufferedWriter mVisFusionWriter;

    private static final Logger LOGGER = LogManager.getLogger(FusionWriter.class);

    public FusionWriter(final String outputDir)
    {
        mOutputDir = outputDir;
        mFusionWriter = null;
        mVisFusionWriter = null;
    }

    public static void convertBreakendsAndFusions(
            final List<GeneFusion> geneFusions, final List<Transcript> transcripts,
            final List<LinxFusion> fusions, final List<LinxBreakend> breakends)
    {
        int breakendId = 0;
        Map<Transcript,Integer> transIdMap = Maps.newHashMap();

        for(final Transcript transcript : transcripts)
        {
            transIdMap.put(transcript, breakendId);

            breakends.add(ImmutableLinxBreakend.builder()
                    .id(breakendId++)
                    .svId(transcript.gene().id())
                    .isStart(transcript.gene().isStart())
                    .gene(transcript.geneName())
                    .transcriptId(transcript.StableId)
                    .canonical(transcript.isCanonical())
                    .isUpstream(transcript.isUpstream())
                    .disruptive(transcript.isDisruptive())
                    .reportedDisruption(transcript.reportableDisruption())
                    .undisruptedCopyNumber(transcript.undisruptedCopyNumber())
                    .regionType(transcript.regionType())
                    .codingContext(transcript.codingType())
                    .biotype(transcript.bioType())
                    .exonBasePhase(transcript.exactCodingBase())
                    .nextSpliceExonRank(transcript.nextSpliceExonRank())
                    .nextSpliceExonPhase(transcript.nextSpliceExonPhase())
                    .nextSpliceDistance(transcript.isUpstream() ? transcript.prevSpliceAcceptorDistance() : transcript.nextSpliceAcceptorDistance())
                    .totalExonCount(transcript.ExonMax)
                    .build());
        }

        for(final GeneFusion geneFusion : geneFusions)
        {
            int upBreakendId = transIdMap.get(geneFusion.upstreamTrans());
            int downBreakendId = transIdMap.get(geneFusion.downstreamTrans());

            fusions.add(ImmutableLinxFusion.builder()
                    .fivePrimeBreakendId(upBreakendId)
                    .threePrimeBreakendId(downBreakendId)
                    .name(geneFusion.name())
                    .reported(geneFusion.reportable())
                    .reportedType(geneFusion.getKnownType())
                    .phased(geneFusion.phaseMatched())
                    .chainLength(geneFusion.getChainLength())
                    .chainLinks(geneFusion.getChainLinks())
                    .chainTerminated(geneFusion.isTerminated())
                    .domainsKept(geneFusion.downstreamTrans().getProteinFeaturesKept())
                    .domainsLost(geneFusion.downstreamTrans().getProteinFeaturesLost())
                    .skippedExonsUp(geneFusion.getExonsSkipped(true))
                    .skippedExonsDown(geneFusion.getExonsSkipped(false))
                    .fusedExonUp(geneFusion.getFusedExon(true))
                    .fusedExonDown(geneFusion.getFusedExon(false))
                    .build());
        }
    }

    public void writeSampleData(
            final String sampleId, final List<GeneFusion> geneFusions, final List<LinxFusion> fusions, final List<LinxBreakend> breakends)
    {
        // write sample files for patient reporter
        List<ReportableGeneFusion> reportedFusions = Lists.newArrayList();
        for(final GeneFusion fusion : geneFusions)
        {
            if(fusion.reportable())
            {
                reportedFusions.add(ImmutableReportableGeneFusion.builder()
                        .geneStart(fusion.upstreamTrans().geneName())
                        .geneTranscriptStart(fusion.upstreamTrans().StableId)
                        .geneContextStart(context(fusion.upstreamTrans(), fusion.getFusedExon(true)))
                        .geneEnd(fusion.downstreamTrans().geneName())
                        .geneTranscriptEnd(fusion.downstreamTrans().StableId)
                        .geneContextEnd(context(fusion.downstreamTrans(), fusion.getFusedExon(false)))
                        .ploidy(fusionPloidy(fusion.upstreamTrans().gene().ploidy(), fusion.downstreamTrans().gene().ploidy()))
                        .build());
            }
        }

        try
        {
            // write file of reportable fusions for for patient reporter
            final String reportedFusionsFile = ReportableGeneFusionFile.generateFilename(mOutputDir, sampleId);
            ReportableGeneFusionFile.write(reportedFusionsFile, reportedFusions);

            // write flat files for database loading
            final String breakendsFile = LinxBreakendFile.generateFilename(mOutputDir, sampleId);
            LinxBreakendFile.write(breakendsFile, breakends);

            final String fusionsFile = LinxFusionFile.generateFilename(mOutputDir, sampleId);
            LinxFusionFile.write(fusionsFile, fusions);
        }
        catch(IOException e)
        {
            LOGGER.error("failed to write fusions file: {}", e.toString());
        }
    }

    public void initialiseOutputFiles(boolean addVisWriter)
    {
        try
        {
            if(mFusionWriter == null)
            {
                mFusionWriter = createBufferedWriter(mOutputDir + "LNX_FUSIONS.csv", false);

                mFusionWriter.write("SampleId,Reportable,KnownType,PhaseMatched,ClusterId,ClusterCount,ResolvedType");

                for(int se = SE_START; se <= SE_END; ++se)
                {
                    String upDown = se == SE_START ? "Up" : "Down";

                    String fieldsStr = ",SvId" + upDown;
                    fieldsStr += ",Chr" + upDown;
                    fieldsStr += ",Pos" + upDown;
                    fieldsStr += ",Orient" + upDown;
                    fieldsStr += ",Type" + upDown;
                    fieldsStr += ",Ploidy" + upDown;
                    fieldsStr += ",GeneId" + upDown;
                    fieldsStr += ",GeneName" + upDown;
                    fieldsStr += ",Transcript" + upDown;
                    fieldsStr += ",Strand" + upDown;
                    fieldsStr += ",RegionType" + upDown;
                    fieldsStr += ",CodingType" + upDown;
                    fieldsStr += ",BreakendExon" + upDown;
                    fieldsStr += ",FusedExon" + upDown;
                    fieldsStr += ",ExonsSkipped" + upDown;
                    fieldsStr += ",Phase" + upDown;
                    fieldsStr += ",ExonMax" + upDown;
                    fieldsStr += ",Disruptive" + upDown;
                    fieldsStr += ",ExactBase" + upDown;
                    fieldsStr += ",CodingBases" + upDown;
                    fieldsStr += ",TotalCoding" + upDown;
                    fieldsStr += ",CodingStart" + upDown;
                    fieldsStr += ",CodingEnd" + upDown;
                    fieldsStr += ",TransStart" + upDown;
                    fieldsStr += ",TransEnd" + upDown;
                    fieldsStr += ",DistancePrev" + upDown;
                    fieldsStr += ",Canonical" + upDown;
                    fieldsStr += ",Biotype" + upDown;
                    mFusionWriter.write(fieldsStr);
                }

                mFusionWriter.write(",ProteinsKept,ProteinsLost,OverlapUp,OverlapDown,ChainInfo");
                mFusionWriter.newLine();
            }

            if(addVisWriter)
            {
                mVisFusionWriter = createBufferedWriter(mOutputDir + "LNX_VIS_FUSIONS.tsv", false);

                mVisFusionWriter.write(VisFusionFile.header());
                mVisFusionWriter.newLine();
            }
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing fusions: {}", e.toString());
        }
    }

    public void writeVisualisationData(final String sampleId, final List<VisFusionFile> visFusions)
    {
        try
        {
            if (mVisFusionWriter == null)
            {
                // write out fusions file for visualisations
                VisFusionFile.write(VisFusionFile.generateFilename(mOutputDir, sampleId), visFusions);
            }
            else
            {
                for (final VisFusionFile visFusion : visFusions)
                {
                    mVisFusionWriter.write(VisFusionFile.toString(visFusion));
                    mVisFusionWriter.newLine();
                }
            }
        }
        catch (IOException e)
        {
            LOGGER.error("failed to write fusions vis file: {}", e.toString());
        }
    }

    public void writeMultiSampleData(final GeneFusion fusion, final String sampleId)
    {
        if(mFusionWriter == null)
            return;

        try
        {
            BufferedWriter writer = mFusionWriter;

            final FusionAnnotations annotations = fusion.getAnnotations();

            if(annotations == null)
            {
                LOGGER.error("fusion({}) annotations not set", fusion.name());
                return;
            }

            writer.write(String.format("%s,%s,%s",
                    sampleId, fusion.reportable(), fusion.getKnownType()));

            writer.write(String.format(",%s,%d,%d,%s",
                    fusion.phaseMatched(), annotations.clusterId(), annotations.clusterCount(), annotations.resolvedType()));

            // write upstream SV, transcript and exon info
            for(int se = SE_START; se <= SE_END; ++se)
            {
                boolean isUpstream = (se == SE_START);
                final Transcript trans = isUpstream ? fusion.upstreamTrans() : fusion.downstreamTrans();
                final GeneAnnotation gene = trans.gene();

                writer.write(String.format(",%d,%s,%d,%d,%s,%.6f",
                        gene.id(), gene.chromosome(), gene.position(), gene.orientation(),
                        gene.type(), gene.ploidy()));

                writer.write(String.format(",%s,%s,%s,%d,%s,%s",
                        gene.StableId, gene.GeneName, trans.StableId,
                        gene.Strand, trans.regionType(), trans.codingType()));

                writer.write(String.format(",%d,%d,%d,%d,%d,%s",
                        isUpstream ? trans.ExonUpstream : trans.ExonDownstream,
                        fusion.getFusedExon(isUpstream), fusion.getExonsSkipped(isUpstream),
                        isUpstream ? trans.ExonUpstreamPhase : trans.ExonDownstreamPhase,
                        trans.ExonMax, trans.isDisruptive()));

                writer.write(String.format(",%d,%d,%d,%d,%d,%d,%d,%d,%s,%s",
                        trans.exactCodingBase(), trans.calcCodingBases(true), trans.totalCodingBases(),
                        trans.codingStart(), trans.codingEnd(), trans.TranscriptStart, trans.TranscriptEnd,
                        trans.prevSpliceAcceptorDistance(), trans.isCanonical(), trans.bioType()));
            }

            writer.write(String.format(",%s,%s",
                        fusion.downstreamTrans().getProteinFeaturesKept(), fusion.downstreamTrans().getProteinFeaturesLost()));

            String chainInfo = "";
            String defaultValues = ",0:false;0;0;0;false";
            if(annotations.disruptionUp() != null)
            {
                chainInfo += String.format(",%d;%s;%d;%d;%d;%s",
                        annotations.disruptionUp().facingBreakends(), annotations.disruptionUp().allLinksAssembled(),
                        annotations.disruptionUp().totalBreakends(), annotations.disruptionUp().minDistance(),
                        annotations.disruptionUp().disruptedExons(), annotations.disruptionUp().transcriptTerminated());
            }
            else
            {
                chainInfo += defaultValues;

            }

            if(annotations.disruptionDown() != null)
            {
                chainInfo += String.format(",%d;%s;%d;%d;%d;%s",
                        annotations.disruptionDown().facingBreakends(), annotations.disruptionDown().allLinksAssembled(),
                        annotations.disruptionDown().totalBreakends(), annotations.disruptionDown().minDistance(),
                        annotations.disruptionDown().disruptedExons(), annotations.disruptionDown().transcriptTerminated());
            }
            else
            {
                chainInfo += defaultValues;
            }

            if(annotations.chainInfo() != null)
            {
                chainInfo += String.format(",%d;%d;%d;%s;%s",
                        annotations.chainInfo().chainId(), annotations.chainInfo().chainLinks(), annotations.chainInfo().chainLength(),
                        annotations.chainInfo().validTraversal(), annotations.chainInfo().traversalAssembled());
            }
            else
            {
                chainInfo += ",-1;0;0;true;false";
            }

            writer.write(String.format("%s", chainInfo));

            writer.newLine();
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing fusions: {}", e.toString());
        }
    }

    public void close()
    {
        closeBufferedWriter(mFusionWriter);
        closeBufferedWriter(mVisFusionWriter);
    }
}
