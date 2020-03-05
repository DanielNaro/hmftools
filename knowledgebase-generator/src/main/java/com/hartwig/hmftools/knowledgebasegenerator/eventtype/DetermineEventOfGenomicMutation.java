package com.hartwig.hmftools.knowledgebasegenerator.eventtype;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.knowledgebasegenerator.GenomicEvents;
import com.hartwig.hmftools.knowledgebasegenerator.cnv.ActionableAmplificationDeletion;
import com.hartwig.hmftools.knowledgebasegenerator.cnv.CnvExtractor;
import com.hartwig.hmftools.knowledgebasegenerator.cnv.KnownAmplificationDeletion;
import com.hartwig.hmftools.knowledgebasegenerator.hotspot.HotspotExtractor;
import com.hartwig.hmftools.knowledgebasegenerator.output.WriteDataToOutputFile;
import com.hartwig.hmftools.knowledgebasegenerator.sourceknowledgebase.Source;
import com.hartwig.hmftools.vicc.datamodel.ViccEntry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class DetermineEventOfGenomicMutation {
    private static final Logger LOGGER = LogManager.getLogger(DetermineEventOfGenomicMutation.class);

    private static final List<String> AMPLIFICATION =
            Lists.newArrayList("Amplification", "Overexpression", "amp", "OVEREXPRESSION", "Transcript Amplification");
    private static final List<String> DELETION = Lists.newArrayList("Copy Number Loss", "Deletion", "del", "DELETION", "UNDEREXPRESSION");
    private static final Set<String> VARIANTS = Sets.newHashSet("missense_variant", "inframe_deletion", "inframe_insertion");
    private static final Set<String> FUSIONS = Sets.newHashSet();
    private static final Set<String> RANGE = Sets.newHashSet();
    private static final Set<String> SIGNATURE = Sets.newHashSet();

    public static void checkGenomicEvent(@NotNull ViccEntry viccEntry, @NotNull EventType type, @NotNull HotspotExtractor hotspotExtractor,
            @NotNull BufferedWriter writerKnownAmplification, @NotNull BufferedWriter writerKnownDeletions,
            @NotNull BufferedWriter writerActionableCNV) throws IOException, InterruptedException {

        Source source = Source.sourceFromKnowledgebase(viccEntry.source());

        if (AMPLIFICATION.contains(type.eventType())) {
            GenomicEvents typeEvent = GenomicEvents.genomicEvents("Amplification");
            KnownAmplificationDeletion knownAmplification =
                    CnvExtractor.determineKnownAmplificationDeletion(source, typeEvent.toString(), type.gene());
            WriteDataToOutputFile.writeKnownAmplifications(knownAmplification, writerKnownAmplification);
            ActionableAmplificationDeletion actionableAmplification =
                    CnvExtractor.determineActionableAmplificationDeletion(source, typeEvent.toString(), type.gene());
            LOGGER.info("amplification: " + knownAmplification);
        } else if (DELETION.contains(type.eventType())) {
            GenomicEvents typeEvent = GenomicEvents.genomicEvents("Deletion");
            KnownAmplificationDeletion knownDeletion =
                    CnvExtractor.determineKnownAmplificationDeletion(source, typeEvent.toString(), type.gene());
            WriteDataToOutputFile.writeKnownDeletion(knownDeletion, writerKnownDeletions);
            ActionableAmplificationDeletion actionableDeletion =
                    CnvExtractor.determineActionableAmplificationDeletion(source, typeEvent.toString(), type.gene());
            LOGGER.info("deletion: " + knownDeletion);
        } else if (VARIANTS.contains(type.eventType())) {
            GenomicEvents typeEvent = GenomicEvents.genomicEvents("Variants");
            // TODO: Determine hotspots
            //hotspotExtractor.extractHotspots(viccEntry);
        } else if (RANGE.contains(type.eventType())) {
            GenomicEvents typeEvent = GenomicEvents.genomicEvents("Range");
            // TODO: Determine range
        } else if (FUSIONS.contains(type.eventType())) {
            GenomicEvents typeEvent = GenomicEvents.genomicEvents("Fusions");
            // TODO: Determine fusions
        } else if (SIGNATURE.contains(type.eventType())) {
            GenomicEvents typeEvent = GenomicEvents.genomicEvents("Signature");
            // TODO: Determine signature
        } else {
            LOGGER.info("skipping");
        }
    }
}
