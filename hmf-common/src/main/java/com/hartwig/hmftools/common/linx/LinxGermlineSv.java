package com.hartwig.hmftools.common.linx;

import static com.hartwig.hmftools.common.purple.PurpleCommon.DELIMITER;
import static com.hartwig.hmftools.common.utils.FileReaderUtils.createFieldsIndexMap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.sv.StructuralVariantType;

public final class LinxGermlineSv
{
    public final String ChromosomeStart;
    public final String ChromosomeEnd;
    public final int PositionStart;
    public final int PositionEnd;
    public final byte OrientStart;
    public final byte OrientEnd;
    public final String GeneName;
    public final StructuralVariantType Type;
    public final String Filter;
    public final String EventId;
    public final double QualScore;
    public final String HomologyStart;
    public final String HomologyEnd;
    public final double JunctionCopyNumber;
    public final double AdjustedAFStart;
    public final double AdjustedAFEnd;
    public final double AdjustedCopyNumberStart;
    public final double AdjustedCopyNumberEnd;
    public final double AdjustedCopyNumberChangeStart;
    public final double AdjustedCopyNumberChangeEnd;
    public final int GermlineFragments;
    public final int GermlineReferenceFragmentsStart;
    public final int GermlineReferenceFragmentsEnd;
    public final int TumorFragments;
    public final int TumorReferenceFragmentsStart;
    public final int TumorReferenceFragmentsEnd;
    public final String InsertSequence;
    public final String InsertSequenceAlignments;
    public final String InsertSequenceRepeatClass;
    public final String InsertSequenceRepeatType;

    public final int ClusterId;
    public final int ClusterCount;
    public final String ResolvedType;
    public final String LinkedByStart;
    public final String LinkedByEnd;
    public final int CohortFrequency;
    public final boolean Reported;

    public LinxGermlineSv(
            final String chromosomeStart, final String chromosomeEnd, final int positionStart, final int positionEnd,
            final byte orientStart, final byte orientEnd, final String geneName, final StructuralVariantType type,
            final String filter, final String eventId, final double qualScore,
            final String homologyStart, final String homologyEnd, final double junctionCopyNumber,
            final double adjustedAFStart, final double adjustedAFEnd, final double adjustedCopyNumberStart, final double adjustedCopyNumberEnd,
            final double adjustedCopyNumberChangeStart, final double adjustedCopyNumberChangeEnd,
            final int germlineFragments, final int germlineReferenceFragmentsStart, final int germlineReferenceFragmentsEnd,
            final int tumorFragments, final int tumorReferenceFragmentsStart, final int tumorReferenceFragmentsEnd,
            final String insSeq, final String insSeqAlignments, final String insSeqRepeatClass, final String insSeqRepeatType,
            final int clusterId, final int clusterCount, final String resolvedType,
            final String linkedByStart, final String linkedByEnd, final int cohortFrequency, final boolean reported)
    {
        ChromosomeStart = chromosomeStart;
        ChromosomeEnd = chromosomeEnd;
        PositionStart = positionStart;
        PositionEnd = positionEnd;
        OrientStart = orientStart;
        OrientEnd = orientEnd;
        GeneName = geneName;
        Type = type;
        Filter = filter;
        EventId = eventId;
        QualScore = qualScore;
        HomologyStart = homologyStart;
        HomologyEnd = homologyEnd;
        JunctionCopyNumber = junctionCopyNumber;
        AdjustedAFStart = adjustedAFStart;
        AdjustedAFEnd = adjustedAFEnd;
        AdjustedCopyNumberStart = adjustedCopyNumberStart;
        AdjustedCopyNumberEnd = adjustedCopyNumberEnd;
        AdjustedCopyNumberChangeStart = adjustedCopyNumberChangeStart;
        AdjustedCopyNumberChangeEnd = adjustedCopyNumberChangeEnd;
        GermlineFragments = germlineFragments;
        GermlineReferenceFragmentsStart = germlineReferenceFragmentsStart;
        GermlineReferenceFragmentsEnd = germlineReferenceFragmentsEnd;
        TumorFragments = tumorFragments;
        TumorReferenceFragmentsStart = tumorReferenceFragmentsStart;
        TumorReferenceFragmentsEnd = tumorReferenceFragmentsEnd;
        InsertSequence = insSeq;
        InsertSequenceAlignments = insSeqAlignments;
        InsertSequenceRepeatClass = insSeqRepeatClass;
        InsertSequenceRepeatType = insSeqRepeatType;
        ClusterId = clusterId;
        ClusterCount = clusterCount;
        ResolvedType = resolvedType;
        LinkedByStart = linkedByStart;
        LinkedByEnd = linkedByEnd;
        CohortFrequency = cohortFrequency;
        Reported = reported;
    }

    private static final String EXTENSION = ".linx.germline.disruption.tsv";

    public static String generateFilename(final String basePath, final String sample)
    {
        return basePath + File.separator + sample + EXTENSION;
    }

    public static List<LinxGermlineSv> read(final String fileName) throws IOException
    {
        return fromLines(Files.readAllLines(new File(fileName).toPath()));
    }

    public static void write(final String fileName, List<LinxGermlineSv> deletions) throws IOException
    {
        Files.write(new File(fileName).toPath(), toLines(deletions));
    }

    private static List<String> toLines(final List<LinxGermlineSv> deletions)
    {
        final List<String> lines = Lists.newArrayList();
        lines.add(header());
        deletions.forEach(x -> lines.add(toString(x)));
        return lines;
    }

    private static String header()
    {
        return new StringJoiner(DELIMITER, "", "")
                .add("chromosomeStart")
                .add("chromosomeEnd")
                .add("positionStart")
                .add("positionEnd")
                .add("orientStart")
                .add("orientEnd")
                .add("gene")
                .add("type")
                .add("filter")
                .add("event")
                .add("qualScore")
                .add("homologySequenceStart")
                .add("homologySequenceEnd")
                .add("junctionCopyNumber")
                .add("adjustedAFStart")
                .add("adjustedAFEnd")
                .add("adjustedCopyNumberStart")
                .add("adjustedCopyNumberEnd")
                .add("adjustedCopyNumberChangeStart")
                .add("adjustedCopyNumberChangeEnd")
                .add("germlineFragments")
                .add("germlineReferenceFragmentsStart")
                .add("germlineReferenceFragmentsEnd")
                .add("tumorFragments")
                .add("tumorReferenceFragmentsStart")
                .add("tumorReferenceFragmentsEnd")
                .add("insertSequence")
                .add("insertSequenceAlignments")
                .add("insertSequenceRepeatClass")
                .add("insertSequenceRepeatType")
                .add("clusterId")
                .add("clusterCount")
                .add("resolvedType")
                .add("linkedByStart")
                .add("linkedByEnd")
                .add("cohortFrequency")
                .add("reported")
                .toString();
    }

    private static String toString(final LinxGermlineSv disruption)
    {
        return new StringJoiner(DELIMITER)
                .add(disruption.ChromosomeStart)
                .add(disruption.ChromosomeEnd)
                .add(String.valueOf(disruption.PositionStart))
                .add(String.valueOf(disruption.PositionEnd))
                .add(String.valueOf(disruption.OrientStart))
                .add(String.valueOf(disruption.OrientEnd))
                .add(disruption.GeneName)
                .add(String.valueOf(disruption.Type))
                .add(disruption.Filter)
                .add(disruption.EventId)
                .add(String.valueOf(disruption.QualScore))
                .add(disruption.HomologyStart)
                .add(disruption.HomologyEnd)
                .add(String.valueOf(disruption.JunctionCopyNumber))
                .add(String.valueOf(disruption.AdjustedAFStart))
                .add(String.valueOf(disruption.AdjustedAFEnd))
                .add(String.valueOf(disruption.AdjustedCopyNumberStart))
                .add(String.valueOf(disruption.AdjustedCopyNumberEnd))
                .add(String.valueOf(disruption.AdjustedCopyNumberChangeStart))
                .add(String.valueOf(disruption.AdjustedCopyNumberChangeEnd))
                .add(String.valueOf(disruption.GermlineFragments))
                .add(String.valueOf(disruption.GermlineReferenceFragmentsStart))
                .add(String.valueOf(disruption.GermlineReferenceFragmentsEnd))
                .add(String.valueOf(disruption.TumorFragments))
                .add(String.valueOf(disruption.TumorReferenceFragmentsStart))
                .add(String.valueOf(disruption.TumorReferenceFragmentsEnd))
                .add(disruption.InsertSequence)
                .add(disruption.InsertSequenceAlignments)
                .add(disruption.InsertSequenceRepeatClass)
                .add(disruption.InsertSequenceRepeatType)
                .add(String.valueOf(disruption.ClusterId))
                .add(String.valueOf(disruption.ClusterCount))
                .add(disruption.ResolvedType)
                .add(disruption.LinkedByStart)
                .add(disruption.LinkedByEnd)
                .add(String.valueOf(disruption.CohortFrequency))
                .add(String.valueOf(disruption.Reported))
                .toString();
    }

    static List<LinxGermlineSv> fromLines(final List<String> lines)
    {
        final Map<String, Integer> fieldsIndexMap = createFieldsIndexMap(lines.get(0), DELIMITER);
        lines.remove(0);

        List<LinxGermlineSv> deletions = Lists.newArrayList();

        for(final String line : lines)
        {
            String[] values = line.split(DELIMITER, -1);

            deletions.add(new LinxGermlineSv(
                    values[fieldsIndexMap.get("chromosomeStart")], values[fieldsIndexMap.get("chromosomeEnd")],
                    Integer.parseInt(values[fieldsIndexMap.get("positionStart")]), Integer.parseInt(values[fieldsIndexMap.get("positionEnd")]),
                    Byte.parseByte(values[fieldsIndexMap.get("orientStart")]), Byte.parseByte(values[fieldsIndexMap.get("orientEnd")]),
                    values[fieldsIndexMap.get("gene")], StructuralVariantType.valueOf(values[fieldsIndexMap.get("type")]),
                    values[fieldsIndexMap.get("filter")], values[fieldsIndexMap.get("event")],
                    Double.parseDouble(values[fieldsIndexMap.get("qualScore")]),
                    values[fieldsIndexMap.get("homologySequenceStart")], values[fieldsIndexMap.get("homologySequenceEnd")],
                    Double.parseDouble(values[fieldsIndexMap.get("junctionCopyNumber")]),
                    Double.parseDouble(values[fieldsIndexMap.get("adjustedAFStart")]),
                    Double.parseDouble(values[fieldsIndexMap.get("adjustedAFEnd")]),
                    Double.parseDouble(values[fieldsIndexMap.get("adjustedCopyNumberStart")]),
                    Double.parseDouble(values[fieldsIndexMap.get("adjustedCopyNumberEnd")]),
                    Double.parseDouble(values[fieldsIndexMap.get("adjustedCopyNumberChangeStart")]),
                    Double.parseDouble(values[fieldsIndexMap.get("adjustedCopyNumberChangeEnd")]),
                    Integer.parseInt(values[fieldsIndexMap.get("germlineFragments")]),
                    Integer.parseInt(values[fieldsIndexMap.get("germlineReferenceFragmentsStart")]),
                    Integer.parseInt(values[fieldsIndexMap.get("germlineReferenceFragmentsEnd")]),
                    Integer.parseInt(values[fieldsIndexMap.get("tumorFragments")]),
                    Integer.parseInt(values[fieldsIndexMap.get("tumorReferenceFragmentsStart")]),
                    Integer.parseInt(values[fieldsIndexMap.get("tumorReferenceFragmentsEnd")]),
                    values[fieldsIndexMap.get("insertSequence")], values[fieldsIndexMap.get("insertSequenceAlignments")],
                    values[fieldsIndexMap.get("insertSequenceRepeatClass")], values[fieldsIndexMap.get("insertSequenceRepeatType")],
                    Integer.parseInt(values[fieldsIndexMap.get("clusterId")]), Integer.parseInt(values[fieldsIndexMap.get("clusterCount")]),
                    values[fieldsIndexMap.get("resolvedType")],
                    values[fieldsIndexMap.get("linkedByStart")], values[fieldsIndexMap.get("linkedByEnd")],
                    Integer.parseInt(values[fieldsIndexMap.get("cohortFrequency")]),
                    Boolean.parseBoolean(values[fieldsIndexMap.get("reported")])));
        }

        return deletions;
    }
}
