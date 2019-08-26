package com.hartwig.hmftools.bachelor;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import com.hartwig.hmftools.bachelor.types.BachelorGermlineVariant;
import com.hartwig.hmftools.common.variant.EnrichedSomaticVariant;
import com.hartwig.hmftools.patientdb.dao.DatabaseUtil;
import com.hartwig.hmftools.patientdb.database.hmfpatients.Tables;

import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStepN;

public class GermlineVariantDAO {

    @NotNull
    private final DSLContext context;

    private static final String PASS = "PASS";

    GermlineVariantDAO(@NotNull final DSLContext context) {
        this.context = context;
    }

    public void write(final String sampleId, final List<BachelorGermlineVariant> bachRecords)
    {
        // First remove any existing records for this patient
        context.delete(Tables.GERMLINEVARIANT).where(Tables.GERMLINEVARIANT.SAMPLEID.eq(sampleId)).execute();

        final Timestamp timestamp = new Timestamp(new Date().getTime());

        InsertValuesStepN inserter = context.insertInto(Tables.GERMLINEVARIANT,
                Tables.GERMLINEVARIANT.SAMPLEID,
                Tables.GERMLINEVARIANT.CHROMOSOME,
                Tables.GERMLINEVARIANT.POSITION,
                Tables.GERMLINEVARIANT.FILTER,
                Tables.GERMLINEVARIANT.TYPE,
                Tables.GERMLINEVARIANT.REF,
                Tables.GERMLINEVARIANT.ALT,
                Tables.GERMLINEVARIANT.GENE,
                Tables.GERMLINEVARIANT.DBSNPID,
                Tables.GERMLINEVARIANT.COSMICID,
                Tables.GERMLINEVARIANT.EFFECT,
                Tables.GERMLINEVARIANT.CODINGEFFECT,
                Tables.GERMLINEVARIANT.TRANSCRIPT,
                Tables.GERMLINEVARIANT.ALLELEREADCOUNT,
                Tables.GERMLINEVARIANT.TOTALREADCOUNT,
                Tables.GERMLINEVARIANT.ADJUSTEDCOPYNUMBER,
                Tables.GERMLINEVARIANT.ADJUSTEDVAF,
                Tables.GERMLINEVARIANT.TRINUCLEOTIDECONTEXT,
                Tables.GERMLINEVARIANT.MICROHOMOLOGY,
                Tables.GERMLINEVARIANT.REPEATSEQUENCE,
                Tables.GERMLINEVARIANT.REPEATCOUNT,
                Tables.GERMLINEVARIANT.HGVSPROTEIN,
                Tables.GERMLINEVARIANT.HGVSCODING,
                Tables.GERMLINEVARIANT.BIALLELIC,
                Tables.GERMLINEVARIANT.HOTSPOT,
                Tables.GERMLINEVARIANT.MAPPABILITY,
                Tables.GERMLINEVARIANT.MINORALLELEPLOIDY,
                Tables.GERMLINEVARIANT.REFSTATUS,
                Tables.GERMLINEVARIANT.PROGRAM,
                Tables.GERMLINEVARIANT.SOURCE,
                Tables.GERMLINEVARIANT.MODIFIED);

        for (final BachelorGermlineVariant bachRecord : bachRecords)
        {
            if (!bachRecord.isValid())
                continue;

            final EnrichedSomaticVariant variant = bachRecord.getEnrichedVariant();

            inserter.values(sampleId,
                    bachRecord.Chromosome,
                    bachRecord.Position,
                    bachRecord.isLowScore() ? "ARTEFACT" : PASS,
                    variant.type(),
                    variant.ref(),
                    variant.alt(),
                    bachRecord.Gene,
                    variant.dbsnpID() == null ? "" : variant.dbsnpID(),
                    variant.canonicalCosmicID() == null ? "" : variant.canonicalCosmicID(),
                    bachRecord.Effects,
                    bachRecord.CodingEffect,
                    bachRecord.TranscriptId,
                    bachRecord.getTumorAltCount(),
                    bachRecord.getTumorReadDepth(),
                    DatabaseUtil.decimal(variant.adjustedCopyNumber()),
                    DatabaseUtil.decimal(bachRecord.getAdjustedVaf()),
                    variant.trinucleotideContext(),
                    variant.microhomology(),
                    variant.repeatSequence(),
                    variant.repeatCount(),
                    bachRecord.HgvsProtein,
                    bachRecord.HgvsCoding,
                    bachRecord.isBiallelic(),
                    variant.isHotspot(),
                    DatabaseUtil.decimal(variant.mappability()),
                    DatabaseUtil.decimal(variant.minorAllelePloidy()),
                    bachRecord.IsHomozygous ? "HOM" : "HET",
                    bachRecord.Program,
                    "GERMLINE",
                    timestamp);
        }

        inserter.execute();
    }
}
