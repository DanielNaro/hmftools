package com.hartwig.hmftools.common.variant.structural;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.variant.filter.ChromosomeFilter;
import com.hartwig.hmftools.common.variant.filter.ExcludeCNVFilter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.filter.CompoundFilter;
import htsjdk.variant.variantcontext.filter.VariantContextFilter;

public class StructuralVariantFactory {

    public final static String RECOVERED = "RECOVERED";
    public final static String RECOVERY_METHOD = "RECOVERY_METHOD";
    public final static String RECOVERY_FILTER = "RECOVERY_FILTER";
    public static final String INFERRED = "INFERRED";
    public final static String CIPOS = "CIPOS";
    public final static String SVTYPE = "SVTYPE";
    public static final String PON_FILTER_PON = "PON";
    public final static String IMPRECISE = "IMPRECISE";

    private final static String MATE_ID = "MATEID";
    private final static String PAR_ID = "PARID";
    private final static String INS_SEQ = "SVINSSEQ";
    private final static String LEFT_INS_SEQ = "LEFT_SVINSSEQ";
    private final static String RIGHT_INS_SEQ = "RIGHT_SVINSSEQ";
    private final static String HOM_SEQ = "HOMSEQ";
    private final static String BPI_START = "BPI_START";
    private final static String BPI_END = "BPI_END";
    private final static String BPI_AF = "BPI_AF";
    private final static String SOMATIC_SCORE = "SOMATICSCORE"; // only applicable for Manta and will be removed when fully on GRIDSS
    private final static String IHOMPOS = "IHOMPOS";
    private final static String VARIANT_FRAGMENT_BREAKPOINT_COVERAGE = "VF";
    private final static String VARIANT_FRAGMENT_BREAKEND_COVERAGE = "BVF";
    private final static String REFERENCE_BREAKEND_READ_COVERAGE = "REF";
    private final static String REFERENCE_BREAKEND_READPAIR_COVERAGE = "REFPAIR";
    private final static String EVENT = "EVENT";
    private final static String LOCAL_LINKED_BY = "LOCAL_LINKED_BY";
    private final static String REMOTE_LINKED_BY = "REMOTE_LINKED_BY";
    private final static String UNTEMPLATED_SEQUENCE_ALIGNMENTS = "BEALN";
    private final static String UNTEMPLATED_SEQUENCE_REPEAT_CLASS = "INSRMRC";
    private final static String UNTEMPLATED_SEQUENCE_REPEAT_TYPE = "INSRMRT";
    private final static String UNTEMPLATED_SEQUENCE_REPEAT_ORIENTATION = "INSRMRO";
    private final static String UNTEMPLATED_SEQUENCE_REPEAT_COVERAGE = "INSRMP";


    /**
     * Must match the small deldup threshold in scripts/gridss/gridss.config.R
     */
    private final static int SMALL_DELDUP_SIZE = 1000;
    private final static int NORMAL_GENOTYPE_ORDINAL = 0;
    private final static int TUMOUR_GENOTYPE_ORDINAL = 1;
    private final static Pattern BREAKEND_REGEX = Pattern.compile("^(.*)([\\[\\]])(.+)[\\[\\]](.*)$");
    private final static Pattern SINGLE_BREAKEND_REGEX = Pattern.compile("^(([.].*)|(.*[.]))$");

    @NotNull
    private final Map<String, VariantContext> unmatched = Maps.newHashMap();
    @NotNull
    private final List<StructuralVariant> results = Lists.newArrayList();
    @NotNull
    private final CompoundFilter filter;

    public StructuralVariantFactory(final @NotNull VariantContextFilter filter) {
        this.filter = new CompoundFilter(true);
        this.filter.add(new ChromosomeFilter());
        this.filter.add(new ExcludeCNVFilter());
        this.filter.add(filter);
    }

    public void addVariantContext(final @NotNull VariantContext context) {
        if (filter.test(context)) {
            final StructuralVariantType type = type(context);
            if (type.equals(StructuralVariantType.BND)) {
                final boolean isSingleBreakend = SINGLE_BREAKEND_REGEX.matcher(context.getAlternateAllele(0).getDisplayString()).matches();
                if (isSingleBreakend) {
                    results.add(createSingleBreakend(context));
                } else {
                    String mate = mateId(context);
                    if (unmatched.containsKey(mate)) {
                        results.add(create(unmatched.remove(mate), context));
                    } else {
                        unmatched.put(context.getID(), context);
                    }
                }
            } else {
                results.add(create(context));
            }
        }
    }

    @Nullable
    public static String mateId(@NotNull VariantContext context) {
        String mate = (String) context.getAttribute(PAR_ID);
        if (mate == null) {
            return (String) context.getAttribute(MATE_ID);
        }

        return mate;
    }

    @NotNull
    public List<StructuralVariant> results() {
        return results;
    }

    @NotNull
    public List<VariantContext> unmatched() {
        return Lists.newArrayList(unmatched.values());
    }

    @NotNull
    private static StructuralVariant create(@NotNull VariantContext context) {
        final StructuralVariantType type = type(context);
        Preconditions.checkArgument(!StructuralVariantType.BND.equals(type));

        final int start = context.hasAttribute(BPI_START) ? context.getAttributeAsInt(BPI_START, -1) : context.getStart();
        final int end = context.hasAttribute(BPI_END) ? context.getAttributeAsInt(BPI_END, -1) : context.getEnd();
        final List<Double> af = context.hasAttribute(BPI_AF) ? context.getAttributeAsDoubleList(BPI_AF, 0.0) : Collections.emptyList();

        byte startOrientation = 0, endOrientation = 0;
        switch (type) {
            case INV:
                if (context.hasAttribute("INV3")) {
                    startOrientation = endOrientation = 1;
                } else if (context.hasAttribute("INV5")) {
                    startOrientation = endOrientation = -1;
                }
                break;
            case DEL:
                startOrientation = 1;
                endOrientation = -1;
                break;
            case INS:
                startOrientation = 1;
                endOrientation = -1;
                break;
            case DUP:
                startOrientation = -1;
                endOrientation = 1;
                break;
        }

        String insertedSequence = "";

        if (type == StructuralVariantType.INS) {
            final String leftInsertSeq = context.getAttributeAsString(LEFT_INS_SEQ, "");
            final String rightInsertSeq = context.getAttributeAsString(RIGHT_INS_SEQ, "");
            if (!leftInsertSeq.isEmpty() && !rightInsertSeq.isEmpty()) {
                insertedSequence = leftInsertSeq + "|" + rightInsertSeq;
            } else {
                List<Allele> alleles = context.getAlleles();
                if (alleles.size() > 1) {
                    insertedSequence = alleles.get(1).toString();

                    // remove the ref base from the start
                    insertedSequence = insertedSequence.substring(1, insertedSequence.length());
                }
            }
        } else {
            insertedSequence = context.getAttributeAsString(INS_SEQ, "");
        }
        final boolean isSmallDelDup =
                (end - start) <= SMALL_DELDUP_SIZE && (type == StructuralVariantType.DEL || type == StructuralVariantType.DUP);
        final StructuralVariantLeg startLeg =
                setLegCommon(ImmutableStructuralVariantLegImpl.builder(), context, isSmallDelDup).chromosome(context.getContig())
                        .position(start)
                        .orientation(startOrientation)
                        .homology(context.getAttributeAsString(HOM_SEQ, ""))
                        .alleleFrequency(af.size() == 2 ? af.get(0) : null)
                        .build();

        final StructuralVariantLeg endLeg =
                setLegCommon(ImmutableStructuralVariantLegImpl.builder(), context, isSmallDelDup).chromosome(context.getContig())
                        .position(end)
                        .orientation(endOrientation)
                        .homology("")
                        .alleleFrequency(af.size() == 2 ? af.get(1) : null)
                        .build();

        return setCommon(ImmutableStructuralVariantImpl.builder(), context).start(startLeg)
                .end(endLeg)
                .insertSequence(insertedSequence)
                .type(type)
                .filter(filters(context, null))
                .startContext(context)
                .build();
    }

    @NotNull
    public static StructuralVariant create(@NotNull VariantContext first, @NotNull VariantContext second) {
        Preconditions.checkArgument(StructuralVariantType.BND.equals(type(first)));
        Preconditions.checkArgument(StructuralVariantType.BND.equals(type(second)));

        final int start = first.hasAttribute(BPI_START) ? first.getAttributeAsInt(BPI_START, -1) : first.getStart();
        final int end = second.hasAttribute(BPI_START) ? second.getAttributeAsInt(BPI_START, -1) : second.getStart();
        final List<Double> af = first.hasAttribute(BPI_AF) ? first.getAttributeAsDoubleList(BPI_AF, 0.0) : Collections.emptyList();

        final String alt = first.getAlternateAllele(0).getDisplayString();
        final Matcher match = BREAKEND_REGEX.matcher(alt);
        if (!match.matches()) {
            throw new IllegalArgumentException(String.format("ALT %s is not in breakend notation", alt));
        }

        // Local orientation determined by the positioning of the anchoring bases
        final byte startOrientation = (byte) (match.group(1).length() > 0 ? 1 : -1);
        // Other orientation determined by the direction of the brackets
        final byte endOrientation = (byte) (match.group(2).equals("]") ? 1 : -1);
        // Grab the inserted sequence by removing 1 base from the reference anchoring bases
        String insertedSequence =
                match.group(1).length() > 0 ? match.group(1).substring(1) : match.group(4).substring(0, match.group(4).length() - 1);
        if (Strings.isNullOrEmpty(insertedSequence)) {
            insertedSequence = first.getAttributeAsString(INS_SEQ, "");
        }

        final boolean isSmallDelDup =
                first.getContig().equals(second.getContig()) && Math.abs(first.getStart() - second.getStart()) <= SMALL_DELDUP_SIZE
                        && startOrientation != endOrientation;

        final StructuralVariantLeg startLeg =
                setLegCommon(ImmutableStructuralVariantLegImpl.builder(), first, isSmallDelDup).position(start)
                        .orientation(startOrientation)
                        .homology(first.getAttributeAsString(HOM_SEQ, ""))
                        .alleleFrequency(af.size() == 2 ? af.get(0) : null)
                        .build();

        final StructuralVariantLeg endLeg = setLegCommon(ImmutableStructuralVariantLegImpl.builder(), second, isSmallDelDup).position(end)
                .orientation(endOrientation)
                .homology(second.getAttributeAsString(HOM_SEQ, ""))
                .alleleFrequency(af.size() == 2 ? af.get(1) : null)
                .build();

        StructuralVariantType inferredType = StructuralVariantType.BND;
        if (startLeg.chromosome().equals(endLeg.chromosome())) {
            if (startLeg.orientation() == endLeg.orientation()) {
                inferredType = StructuralVariantType.INV;
            } else if (startLeg.orientation() == -1) {
                inferredType = StructuralVariantType.DUP;
            } else if (insertedSequence != null && insertedSequence.length() > endLeg.position() - startLeg.position()) {
                inferredType = StructuralVariantType.INS;
            } else {
                inferredType = StructuralVariantType.DEL;
            }
        }
        return setCommon(ImmutableStructuralVariantImpl.builder(), first).start(startLeg)
                .end(endLeg)
                .mateId(second.getID())
                .insertSequence(insertedSequence)
                .type(inferredType)
                .filter(filters(first, second))
                .startContext(first)
                .endContext(second)
                .build();
    }

    @NotNull
    public static StructuralVariant createSingleBreakend(@NotNull VariantContext context) {
        Preconditions.checkArgument(StructuralVariantType.BND.equals(type(context)));
        Preconditions.checkArgument(SINGLE_BREAKEND_REGEX.matcher(context.getAlternateAllele(0).getDisplayString()).matches());

        final List<Double> af = context.hasAttribute(BPI_AF) ? context.getAttributeAsDoubleList(BPI_AF, 0.0) : Collections.emptyList();

        final String alt = context.getAlternateAllele(0).getDisplayString();
        // local orientation determined by the positioning of the anchoring bases
        final byte orientation = (byte) (alt.startsWith(".") ? -1 : 1);
        final int refLength = context.getReference().length();
        final String insertedSequence =
                orientation == -1 ? alt.substring(1, alt.length() - refLength) : alt.substring(refLength, alt.length() - 1);

        final StructuralVariantLeg startLeg =
                setLegCommon(ImmutableStructuralVariantLegImpl.builder(), context, false).orientation(orientation)
                        .homology("")
                        .alleleFrequency(af.size() >= 1 ? af.get(0) : null)
                        .build();

        return setCommon(ImmutableStructuralVariantImpl.builder(), context).start(startLeg)
                .insertSequence(insertedSequence)
                .type(StructuralVariantType.SGL)
                .filter(filters(context, null))
                .startContext(context)
                .build();

    }

    @NotNull
    private static ImmutableStructuralVariantImpl.Builder setCommon(@NotNull ImmutableStructuralVariantImpl.Builder builder,
            @NotNull VariantContext context)
    {
        // backwards compatibility for Manta until fully over to GRIDSS
        int somaticScore = context.getAttributeAsInt(SOMATIC_SCORE, 0);
        double qualityScore = context.getPhredScaledQual();

        if(somaticScore > 0) {
            qualityScore = somaticScore;
        }

        builder = builder.id(context.getID())
            .recovered(context.hasAttribute(RECOVERED))
            .recoveryMethod(context.getAttributeAsString(RECOVERY_METHOD, null))
            .recoveryFilter(context.getAttributeAsStringList(RECOVERY_FILTER, "").stream().collect(Collectors.joining(",")))
            .event(context.getAttributeAsString(EVENT, null))
            .startLinkedBy(context.getAttributeAsStringList(LOCAL_LINKED_BY, "")
                    .stream()
                    .filter(s -> !Strings.isNullOrEmpty(s))
                    .collect(Collectors.joining(",")))
            .endLinkedBy(context.getAttributeAsStringList(REMOTE_LINKED_BY, "")
                    .stream()
                    .filter(s -> !Strings.isNullOrEmpty(s))
                    .collect(Collectors.joining(",")))
            .imprecise(imprecise(context))
            .qualityScore(qualityScore)
            .insertSequenceAlignments(context.getAttributeAsStringList(UNTEMPLATED_SEQUENCE_ALIGNMENTS, "")
                    .stream()
                    .filter(s -> !Strings.isNullOrEmpty(s))
                    .collect(Collectors.joining(",")));
        if (context.hasAttribute(UNTEMPLATED_SEQUENCE_REPEAT_CLASS)) {
            builder = builder
                    .insertSequenceRepeatClass(context.getAttributeAsString(UNTEMPLATED_SEQUENCE_REPEAT_CLASS, ""))
                    .insertSequenceRepeatType(context.getAttributeAsString(UNTEMPLATED_SEQUENCE_REPEAT_TYPE, ""))
                    .insertSequenceRepeatOrientation(context.getAttributeAsString(UNTEMPLATED_SEQUENCE_REPEAT_ORIENTATION, "+").equals("+") ? (byte)1 : (byte)-1 )
                    .insertSequenceRepeatCoverage(context.getAttributeAsDouble(UNTEMPLATED_SEQUENCE_REPEAT_COVERAGE, 0));
        }
        return builder;
    }

    @NotNull
    private static ImmutableStructuralVariantLegImpl.Builder setLegCommon(@NotNull ImmutableStructuralVariantLegImpl.Builder builder,
            @NotNull VariantContext context, boolean ignoreRefpair) {
        builder.chromosome(context.getContig());
        builder.position(context.getStart());

        builder.startOffset(0);
        builder.endOffset(0);
        if (context.hasAttribute(CIPOS)) {
            final List<Integer> cipos = context.getAttributeAsIntList(CIPOS, 0);
            if (cipos.size() == 2) {
                builder.startOffset(cipos.get(0));
                builder.endOffset(cipos.get(1));
            }
        }
        builder.inexactHomologyOffsetStart(0);
        builder.inexactHomologyOffsetEnd(0);
        if (context.hasAttribute(IHOMPOS)) {
            final List<Integer> ihompos = context.getAttributeAsIntList(IHOMPOS, 0);
            if (ihompos.size() == 2) {
                builder.inexactHomologyOffsetStart(ihompos.get(0));
                builder.inexactHomologyOffsetEnd(ihompos.get(1));
            }
        }
        if (context.getGenotype(NORMAL_GENOTYPE_ORDINAL) != null) {
            Genotype geno = context.getGenotype(NORMAL_GENOTYPE_ORDINAL);
            if (geno.hasExtendedAttribute(VARIANT_FRAGMENT_BREAKPOINT_COVERAGE) || geno.hasExtendedAttribute(VARIANT_FRAGMENT_BREAKEND_COVERAGE)) {
                Integer var = asInteger(geno.getExtendedAttribute(context.hasAttribute(PAR_ID) ? VARIANT_FRAGMENT_BREAKPOINT_COVERAGE :VARIANT_FRAGMENT_BREAKEND_COVERAGE));
                Integer ref = asInteger(geno.getExtendedAttribute(REFERENCE_BREAKEND_READ_COVERAGE));
                Integer refpair = asInteger(geno.getExtendedAttribute(REFERENCE_BREAKEND_READPAIR_COVERAGE));
                builder = builder.normalVariantFragmentCount(var);
                builder = builder.normalReferenceFragmentCount(ref + (ignoreRefpair ? 0 : refpair));
            }
        }
        if (context.getGenotype(TUMOUR_GENOTYPE_ORDINAL) != null) {
            Genotype geno = context.getGenotype(TUMOUR_GENOTYPE_ORDINAL);
            if (geno.hasExtendedAttribute(VARIANT_FRAGMENT_BREAKPOINT_COVERAGE) || geno.hasExtendedAttribute(VARIANT_FRAGMENT_BREAKEND_COVERAGE)) {
                Integer var = asInteger(geno.getExtendedAttribute(context.hasAttribute(PAR_ID) ? VARIANT_FRAGMENT_BREAKPOINT_COVERAGE :VARIANT_FRAGMENT_BREAKEND_COVERAGE));
                Integer ref = asInteger(geno.getExtendedAttribute(REFERENCE_BREAKEND_READ_COVERAGE));
                Integer refpair = asInteger(geno.getExtendedAttribute(REFERENCE_BREAKEND_READPAIR_COVERAGE));
                builder = builder.tumourVariantFragmentCount(var);
                builder = builder.tumourReferenceFragmentCount(ref + (ignoreRefpair ? 0 : refpair));
            }
        }
        return builder;
    }

    private static Integer asInteger(Object obj) {
        if (obj == null) {
            return null;
        } else if (obj instanceof Integer) {
            return (Integer) obj;
        } else {
            final String strObj = obj.toString();
            if (strObj == null || strObj.isEmpty()) {
                return null;
            } else {
                return Integer.parseInt(strObj);
            }
        }
    }

    @NotNull
    private static String filters(@NotNull VariantContext context, @Nullable VariantContext pairedContext) {
        final Set<String> filters = new HashSet<>(context.getFilters());
        if (pairedContext != null) {
            filters.addAll(pairedContext.getFilters());
        }
        if (filters.size() > 1) {
            // Doesn't pass if a filter is applied to either of the two records
            filters.remove("PASS");
        }
        if (filters.size() == 0) {
            filters.add("PASS");
        }
        return filters.stream().sorted().collect(Collectors.joining(";"));
    }

    private static boolean imprecise(@NotNull VariantContext context) {
        final String impreciseStr = context.getAttributeAsString(IMPRECISE, "");
        boolean isPrecise = impreciseStr.isEmpty() || !impreciseStr.equals("true");
        return !isPrecise;
    }

    @NotNull
    private static StructuralVariantType type(@NotNull VariantContext context) {
        return StructuralVariantType.fromAttribute((String) context.getAttribute(SVTYPE));
    }
}
