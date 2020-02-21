package com.hartwig.hmftools.sage.read;

import static org.junit.Assert.assertEquals;

import com.hartwig.hmftools.common.variant.hotspot.ImmutableVariantHotspotImpl;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.sage.config.SageConfig;
import com.hartwig.hmftools.sage.config.SageConfigTest;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import htsjdk.samtools.SAMRecord;

public class ReadContextCounterTest {

    private final SageConfig config = SageConfigTest.testConfig();

    @Test
    public void testInsertInLeftSoftClip() {
        final IndexedBases refBases = new IndexedBases(554, 1, "TGTTC".getBytes());
        final VariantHotspot hotspot = ImmutableVariantHotspotImpl.builder().chromosome("1").ref("G").alt("GT").position(554).build();
        final ReadContext readContext = new ReadContext(Strings.EMPTY, 554, 1, 0, 5, 0, "TGTTTC".getBytes());
        final ReadContextCounter victim = new ReadContextCounter(hotspot, readContext);

        final SAMRecord record = buildSamRecord(555, "3S3M", "TGTTTC", "######");
        victim.accept(false, record, config, refBases);

        assertEquals(1, victim.depth());
        assertEquals(1, victim.altSupport());
    }

    @Test
    public void testDeleteInLeftSoftClip() {
        final IndexedBases refBases = new IndexedBases(554, 1, "TGTTTC".getBytes());
        final VariantHotspot hotspot = ImmutableVariantHotspotImpl.builder().chromosome("1").ref("GT").alt("G").position(554).build();
        final ReadContext readContext = new ReadContext(Strings.EMPTY, 554, 1, 0, 4, 0, "TGTTC".getBytes());
        final ReadContextCounter victim = new ReadContextCounter(hotspot, readContext);

        final SAMRecord record = buildSamRecord(556, "2S3M", "TGTTC", "#####");
        victim.accept(false, record, config, refBases);

        assertEquals(1, victim.depth());
        assertEquals(1, victim.altSupport());
    }

    @Test
    public void testMnvInLeftSoftClip() {
        final IndexedBases refBases = new IndexedBases(550, 0, "GATCGATC".getBytes());
        final VariantHotspot hotspot = ImmutableVariantHotspotImpl.builder().chromosome("1").ref("TCG").alt("ATC").position(552).build();
        final ReadContext readContext = new ReadContext(Strings.EMPTY, 552, 2, 0, 6, 0, "GAAAAAT".getBytes());
        final ReadContextCounter victim = new ReadContextCounter(hotspot, readContext);

        final SAMRecord record = buildSamRecord(555, "5S3M", "GAAAAATC", "########");
        victim.accept(false, record, config, refBases);

        assertEquals(1, victim.depth());
        assertEquals(1, victim.altSupport());
    }

    @Test
    public void testInsertInRightSoftClip() {
        final IndexedBases refBases = new IndexedBases(553, 0, "TGTTC".getBytes());
        final VariantHotspot hotspot = ImmutableVariantHotspotImpl.builder().chromosome("1").ref("G").alt("GT").position(554).build();
        final ReadContext readContext = new ReadContext(Strings.EMPTY, 554, 1, 0, 5, 0, "TGTTTC".getBytes());
        final ReadContextCounter victim = new ReadContextCounter(hotspot, readContext);

        final SAMRecord record = buildSamRecord(553, "2M4S", "TGTTTC", "######");
        victim.accept(false, record, config, refBases);

        assertEquals(1, victim.depth());
        assertEquals(1, victim.altSupport());
    }

    @Test
    public void testDeleteInRightSoftClip() {
        final IndexedBases refBases = new IndexedBases(553, 0, "TGTTTC".getBytes());
        final VariantHotspot hotspot = ImmutableVariantHotspotImpl.builder().chromosome("1").ref("GT").alt("G").position(554).build();
        final ReadContext readContext = new ReadContext(Strings.EMPTY, 554, 1, 0, 4, 0, "TGTTC".getBytes());
        final ReadContextCounter victim = new ReadContextCounter(hotspot, readContext);

        final SAMRecord record = buildSamRecord(553, "2M3S", "TGTTC", "#####");
        victim.accept(false, record, config, refBases);

        assertEquals(1, victim.depth());
        assertEquals(1, victim.altSupport());
    }

    @Test
    public void testMnvInRightSoftClip() {
        final IndexedBases refBases = new IndexedBases(550, 0, "GATCGATC".getBytes());
        final VariantHotspot hotspot = ImmutableVariantHotspotImpl.builder().chromosome("1").ref("TCG").alt("ATC").position(552).build();
        final ReadContext readContext = new ReadContext(Strings.EMPTY, 552, 2, 0, 6, 0, "GAAAAAT".getBytes());
        final ReadContextCounter victim = new ReadContextCounter(hotspot, readContext);

        final SAMRecord record = buildSamRecord(550, "2M6S", "GAAAAATC", "########");
        victim.accept(false, record, config, refBases);

        assertEquals(1, victim.depth());
        assertEquals(1, victim.altSupport());
    }


    @NotNull
    public static SAMRecord buildSamRecord(final int alignmentStart, @NotNull final String cigar, @NotNull final String readString,
            @NotNull final String qualities) {
        final SAMRecord record = new SAMRecord(null);
        record.setAlignmentStart(alignmentStart);
        record.setCigarString(cigar);
        record.setReadString(readString);
        record.setReadNegativeStrandFlag(false);
        record.setBaseQualityString(qualities);
        record.setMappingQuality(20);
        record.setDuplicateReadFlag(false);
        record.setReadUnmappedFlag(false);
        record.setProperPairFlag(true);
        record.setReadPairedFlag(true);
        return record;
    }

}
