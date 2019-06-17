package com.hartwig.hmftools.linx.visualiser.file;

import static java.util.stream.Collectors.toList;

import static com.hartwig.hmftools.linx.visualiser.file.VisCopyNumberFile.DELIMITER;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantType;

import org.jetbrains.annotations.NotNull;

public class VisSvDataFile
{
    public final String SampleId;
    public final int ClusterId;
    public final int ChainId;
    public final int SvId;
    public final StructuralVariantType Type;
    public final String ResolvedType;
    public final String ChrStart;
    public final String InfoStart;
    public final String ChrEnd;
    public final String InfoEnd;
    public final long PosStart;
    public final long PosEnd;
    public final byte OrientStart;
    public final byte OrientEnd;
    public final int TraverseCount;

    public static final String INFO_TYPE_NORMAL = "NORMAL";
    public static final String INFO_TYPE_FOLDBACK = "FOLDBACK";

    public VisSvDataFile(final String sampleId, int clusterId, int chainId, int svId,
            StructuralVariantType type, final String resolvedType, final String chrStart, final String chrEnd, long posStart, long posEnd,
            byte orientStart, byte orientEnd, final String infoStart, final String infoEnd, int traverseCount)
    {
        SampleId = sampleId;
        ClusterId = clusterId;
        ChainId = chainId;
        ResolvedType = resolvedType;
        SvId = svId;
        Type = type;
        ChrStart = chrStart;
        ChrEnd = chrEnd;
        PosStart = posStart;
        PosEnd = posEnd;
        OrientStart = orientStart;
        OrientEnd = orientEnd;
        InfoStart = infoStart;
        InfoEnd = infoEnd;
        TraverseCount = traverseCount;
    }

    private static final String FILE_EXTENSION = ".linx.vis_sv_data.tsv";

    @NotNull
    public static String generateFilename(@NotNull final String basePath, @NotNull final String sample)
    {
        return basePath + File.separator + sample + FILE_EXTENSION;
    }

    @NotNull
    public static List<VisSvDataFile> read(final String filePath) throws IOException
    {
        return fromLines(Files.readAllLines(new File(filePath).toPath()));
    }

    public static void write(@NotNull final String filename, @NotNull List<VisSvDataFile> svDataList) throws IOException
    {
        Files.write(new File(filename).toPath(), toLines(svDataList));
    }

    @NotNull
    static List<String> toLines(@NotNull final List<VisSvDataFile> cnDataList)
    {
        final List<String> lines = Lists.newArrayList();
        lines.add(header());
        cnDataList.stream().map(x -> toString(x)).forEach(lines::add);
        return lines;
    }

    @NotNull
    static List<VisSvDataFile> fromLines(@NotNull List<String> lines)
    {
        return lines.stream().filter(x -> !x.startsWith("SampleId")).map(VisSvDataFile::fromString).collect(toList());
    }

    @NotNull
    private static String header()
    {
        return new StringJoiner(DELIMITER)
                .add("SampleId")
                .add("ClusterId")
                .add("ChainId")
                .add("SvId")
                .add("Type")
                .add("ResolvedType")
                .add("ChrStart")
                .add("ChrEnd")
                .add("PosStart")
                .add("PosEnd")
                .add("OrientStart")
                .add("OrientEnd")
                .add("InfoStart")
                .add("InfoEnd")
                .add("TraverseCount")
                .toString();
    }

    @NotNull
    private static String toString(@NotNull final VisSvDataFile svData)
    {
        return new StringJoiner(DELIMITER)
                .add(String.valueOf(svData.SampleId))
                .add(String.valueOf(svData.ClusterId))
                .add(String.valueOf(svData.ChainId))
                .add(String.valueOf(svData.SvId))
                .add(String.valueOf(svData.Type))
                .add(String.valueOf(svData.ResolvedType))
                .add(String.valueOf(svData.ChrStart))
                .add(String.valueOf(svData.ChrEnd))
                .add(String.valueOf(svData.PosStart))
                .add(String.valueOf(svData.PosEnd))
                .add(String.valueOf(svData.OrientStart))
                .add(String.valueOf(svData.OrientEnd))
                .add(String.valueOf(svData.InfoStart))
                .add(String.valueOf(svData.InfoEnd))
                .add(String.valueOf(svData.TraverseCount))
                .toString();
    }

    @NotNull
    private static VisSvDataFile fromString(@NotNull final String tiData)
    {
        String[] values = tiData.split(DELIMITER);

        int index = 0;

        return new VisSvDataFile(
                values[index++],
                Integer.valueOf(values[index++]),
                Integer.valueOf(values[index++]),
                Integer.valueOf(values[index++]),
                StructuralVariantType.valueOf(values[index++]),
                values[index++],
                values[index++],
                values[index++],
                Long.valueOf(values[index++]),
                Long.valueOf(values[index++]),
                Byte.valueOf(values[index++]),
                Byte.valueOf(values[index++]),
                values[index++],
                values[index++],
                Integer.valueOf(values[index++]));
    }

}
