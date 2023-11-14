package com.hartwig.hmftools.markdups;

import static java.lang.String.format;

import static com.hartwig.hmftools.common.test.GeneTestUtils.CHR_1;
import static com.hartwig.hmftools.markdups.TestUtils.DEFAULT_QUAL;
import static com.hartwig.hmftools.markdups.TestUtils.REF_BASES;
import static com.hartwig.hmftools.markdups.TestUtils.REF_BASES_A;
import static com.hartwig.hmftools.markdups.TestUtils.REF_BASES_C;
import static com.hartwig.hmftools.markdups.TestUtils.setBaseQualities;
import static com.hartwig.hmftools.markdups.TestUtils.setSecondInPair;
import static com.hartwig.hmftools.markdups.common.Constants.CONSENSUS_MAX_DEPTH;
import static com.hartwig.hmftools.markdups.consensus.BaseBuilder.NO_BASE;
import static com.hartwig.hmftools.markdups.consensus.ConsensusOutcome.ALIGNMENT_ONLY;
import static com.hartwig.hmftools.markdups.consensus.ConsensusOutcome.INDEL_MATCH;
import static com.hartwig.hmftools.markdups.consensus.ConsensusOutcome.INDEL_MISMATCH;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static htsjdk.samtools.CigarOperator.D;
import static htsjdk.samtools.CigarOperator.I;
import static htsjdk.samtools.CigarOperator.M;
import static htsjdk.samtools.CigarOperator.S;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.test.MockRefGenome;
import com.hartwig.hmftools.common.test.ReadIdGenerator;
import com.hartwig.hmftools.common.test.SamRecordTestUtils;
import com.hartwig.hmftools.markdups.consensus.ConsensusReadInfo;
import com.hartwig.hmftools.markdups.consensus.ConsensusReads;
import com.hartwig.hmftools.markdups.consensus.ReadParseState;

import org.junit.Test;

import htsjdk.samtools.SAMRecord;

public class ConsensusReadsTest
{
    private final MockRefGenome mRefGenome;
    private final MockRefGenome mRefGenomeOneBased;
    private final ConsensusReads mConsensusReads;
    private final ReadIdGenerator mReadIdGen;
    private final Map<Character, Character> mNextBaseMap;

    public static final String UMI_ID_1 = "TAGTAG";

    public ConsensusReadsTest()
    {
        mRefGenome = new MockRefGenome();
        mRefGenome.RefGenomeMap.put(CHR_1, REF_BASES);
        mRefGenome.ChromosomeLengths.put(CHR_1, REF_BASES.length());
        mConsensusReads = new ConsensusReads(mRefGenome);

        mRefGenomeOneBased = new MockRefGenome(true);
        mRefGenomeOneBased.RefGenomeMap.put(CHR_1, REF_BASES);
        mRefGenomeOneBased.ChromosomeLengths.put(CHR_1, REF_BASES.length());

        mReadIdGen = new ReadIdGenerator();

        mNextBaseMap = Maps.newHashMap();
        mNextBaseMap.put('G', 'C');
        mNextBaseMap.put('C', 'A');
        mNextBaseMap.put('A', 'T');
        mNextBaseMap.put('T', 'G');
    }

    @Test
    public void testBasicConsensusReads()
    {
        final List<SAMRecord> reads = Lists.newArrayList();

        int posStart = 11;
        String consensusBases = REF_BASES.substring(posStart, 21);
        reads.add(createSamRecord(nextReadId(), posStart, consensusBases, "10M", false));
        reads.add(createSamRecord(nextReadId(), posStart, consensusBases, "10M", false));
        reads.add(createSamRecord(nextReadId(), posStart, consensusBases, "10M", false));

        ConsensusReadInfo readInfo = mConsensusReads.createConsensusRead(reads, UMI_ID_1);
        assertEquals(ALIGNMENT_ONLY, readInfo.Outcome);
        assertEquals(consensusBases, readInfo.ConsensusRead.getReadString());
        assertEquals("10M", readInfo.ConsensusRead.getCigarString());
        assertEquals(posStart, readInfo.ConsensusRead.getAlignmentStart());

        // mismatch at some of the bases and so determined by qual
        reads.clear();

        SAMRecord read1 = createSamRecord(nextReadId(), posStart, consensusBases, "10M", false);
        setBaseQualities(read1, DEFAULT_QUAL - 1);
        reads.add(read1);

        String bases2 = "AATAATAATA";
        SAMRecord read2 = createSamRecord(nextReadId(), posStart + 2, bases2, "2S8M", false);
        reads.add(read2);

        String bases3 = "AATAAGAAAA";
        SAMRecord read3 = createSamRecord(nextReadId(), posStart + 4, bases3, "4S6M", false);
        setBaseQualities(read3, DEFAULT_QUAL - 1);
        reads.add(read3);

        // 3rd base is 'T' due to max qual of 2nd and 3rd reads
        // 6th base is 'T' due to max qual of 2nd read
        // 9th base is 'A' due to max qual of 1st and 3rd reads

        readInfo = mConsensusReads.createConsensusRead(reads, UMI_ID_1);
        assertEquals(ALIGNMENT_ONLY, readInfo.Outcome);
        assertEquals(posStart, readInfo.ConsensusRead.getAlignmentStart());
        assertEquals("10M", readInfo.ConsensusRead.getCigarString());

        assertEquals("AATAATAAAA", readInfo.ConsensusRead.getReadString());
        assertEquals(19, readInfo.ConsensusRead.getBaseQualities()[2]);
        assertEquals(0, readInfo.ConsensusRead.getBaseQualities()[5]);
        assertEquals(18, readInfo.ConsensusRead.getBaseQualities()[8]);

        // test preference for reference base when quals are qual
        reads.clear();
        posStart = 16;
        read1 = createSamRecord(nextReadId(), posStart, REF_BASES_A, "10M", false);
        reads.add(read1);

        read2 = createSamRecord(nextReadId(), posStart, REF_BASES_C, "10M", false);
        reads.add(read2);

        readInfo = mConsensusReads.createConsensusRead(reads, UMI_ID_1);
        assertEquals("AAAAACCCCC", readInfo.ConsensusRead.getReadString());

        // soft-clips at both ends
        posStart = 11;
        read1 = createSamRecord(nextReadId(), posStart + 3, REF_BASES_A, "3S6M1S", false);
        read2 = createSamRecord(nextReadId(), posStart + 3, REF_BASES_A, "3S6M1S", false);

        read3 = createSamRecord(nextReadId(), posStart + 2, REF_BASES_A, "2S5M3S", false);

        readInfo = mConsensusReads.createConsensusRead(List.of(read1, read2, read3), UMI_ID_1);

        assertEquals(posStart + 3, readInfo.ConsensusRead.getAlignmentStart());
        assertEquals("3S6M1S", readInfo.ConsensusRead.getCigarString());
        assertEquals(REF_BASES_A, readInfo.ConsensusRead.getReadString());

        // longer reads at the non-fragment start end
        posStart = 11;
        read1 = createSamRecord(nextReadId(), posStart, REF_BASES_A, "8M2S", false);

        read2 = createSamRecord(nextReadId(), posStart, REF_BASES_A + "CCC", "7M6S", false);
        read3 = createSamRecord(nextReadId(), posStart, REF_BASES_A + "CCC", "7M6S", false);

        readInfo = mConsensusReads.createConsensusRead(List.of(read1, read2, read3), UMI_ID_1);

        assertEquals(posStart, readInfo.ConsensusRead.getAlignmentStart());
        assertEquals("7M6S", readInfo.ConsensusRead.getCigarString());
        assertEquals(REF_BASES_A + "CCC", readInfo.ConsensusRead.getReadString());

        // same again on the reverse strand
        posStart = 11;
        read1 = createSamRecord(nextReadId(), posStart + 2, REF_BASES_A, "2S6M2S", true);

        read2 = createSamRecord(nextReadId(), posStart, "CCCC" + REF_BASES_A, "4S7M3S", true);
        read3 = createSamRecord(nextReadId(), posStart, "CCCC" + REF_BASES_A, "4S7M3S", true);

        readInfo = mConsensusReads.createConsensusRead(List.of(read1, read2, read3), UMI_ID_1);

        assertEquals(posStart, readInfo.ConsensusRead.getAlignmentStart());
        assertEquals("4S7M3S", readInfo.ConsensusRead.getCigarString());
        assertEquals("CCCC" + REF_BASES_A, readInfo.ConsensusRead.getReadString());
    }

    @Test
    public void testMatchingIndelReads()
    {
        // indels with no CIGAR differences use the standard consensus routines
        final List<SAMRecord> reads = Lists.newArrayList();

        int posStart = 13;

        String consensusBases = REF_BASES_A.substring(0, 5) + "C" + REF_BASES_A.substring(5, 10);
        String indelCigar = "2S3M1I3M2S";
        SAMRecord read1 = createSamRecord(nextReadId(), posStart, consensusBases, indelCigar, false);
        reads.add(read1);

        SAMRecord read2 = createSamRecord(nextReadId(), posStart, consensusBases, indelCigar, false);
        reads.add(read2);

        SAMRecord read3 = createSamRecord(nextReadId(), posStart, consensusBases, indelCigar, false);
        reads.add(read3);

        ConsensusReadInfo readInfo = mConsensusReads.createConsensusRead(reads, UMI_ID_1);
        assertEquals(INDEL_MATCH, readInfo.Outcome);
        assertEquals(consensusBases, readInfo.ConsensusRead.getReadString());
        assertEquals(indelCigar, readInfo.ConsensusRead.getCigarString());
        assertEquals(posStart, readInfo.ConsensusRead.getAlignmentStart());
    }

    @Test
    public void testMultipleSoftClipLengths()
    {
        final List<SAMRecord> reads = Lists.newArrayList();

        int posStart = 1;

        String consensusBases = REF_BASES.substring(1);

        String cigar1 = "100M";
        reads.add(createSamRecord(nextReadId(), posStart, consensusBases, cigar1, true));
        reads.add(createSamRecord(nextReadId(), posStart, consensusBases, cigar1, true));

        String cigar2 = "17S83M";
        reads.add(createSamRecord(nextReadId(), posStart + 17, consensusBases, cigar2, true));

        String cigar3 = "68S32M";
        reads.add(createSamRecord(nextReadId(), posStart + 68, consensusBases, cigar3, true));

        String cigar4 = "94S6M";
        reads.add(createSamRecord(nextReadId(), posStart + 94, consensusBases, cigar4, true));
        reads.add(createSamRecord(nextReadId(), posStart + 94, consensusBases, cigar4, true));
        reads.add(createSamRecord(nextReadId(), posStart + 94, consensusBases, cigar4, true));
        reads.add(createSamRecord(nextReadId(), posStart + 94, consensusBases, cigar4, true));

        String cigar5 = "99S1M";
        reads.add(createSamRecord(nextReadId(), posStart + 99, consensusBases, cigar5, true));

        ConsensusReadInfo readInfo = mConsensusReads.createConsensusRead(reads, UMI_ID_1);
        assertEquals(ALIGNMENT_ONLY, readInfo.Outcome);
        assertEquals(consensusBases, readInfo.ConsensusRead.getReadString());
        assertEquals(cigar4, readInfo.ConsensusRead.getCigarString());
        assertEquals(posStart + 94, readInfo.ConsensusRead.getAlignmentStart());
    }

    @Test
    public void testReadParseState()
    {
        String bases = "AGGCGGA";
        String indelCigar = "1S2M1I2M1S";

        SAMRecord read1 = createSamRecord(nextReadId(), 100, bases, indelCigar, false);

        ReadParseState readState = new ReadParseState(read1, true);
        assertEquals((byte)'A', readState.currentBase());
        assertEquals(DEFAULT_QUAL, readState.currentBaseQual());
        assertEquals(S, readState.elementType());
        assertEquals(1, readState.elementLength());

        readState.moveNext();
        assertEquals((byte)'G', readState.currentBase());
        assertEquals(M, readState.elementType());
        assertEquals(2, readState.elementLength());

        readState.moveNext();
        readState.moveNext();
        assertEquals((byte)'C', readState.currentBase());
        assertEquals(I, readState.elementType());
        assertEquals(1, readState.elementLength());

        readState.moveNext();
        readState.moveNext();
        assertFalse(readState.exhausted());
        assertEquals((byte)'G', readState.currentBase());
        assertEquals(M, readState.elementType());

        readState.moveNext();
        assertFalse(readState.exhausted());
        assertEquals((byte)'A', readState.currentBase());
        assertEquals(S, readState.elementType());

        readState.moveNext();
        assertTrue(readState.exhausted());

        // and in reverse
        readState = new ReadParseState(read1, false);
        assertEquals((byte)'A', readState.currentBase());
        assertEquals(DEFAULT_QUAL, readState.currentBaseQual());
        assertEquals(S, readState.elementType());
        assertEquals(1, readState.elementLength());

        readState.moveNext();
        readState.moveNext();
        readState.moveNext();

        assertEquals((byte)'C', readState.currentBase());
        assertEquals(I, readState.elementType());
        assertEquals(1, readState.elementLength());

        readState.moveNext();
        readState.moveNext();
        readState.moveNext();
        assertFalse(readState.exhausted());
        assertEquals((byte)'A', readState.currentBase());
        assertEquals(S, readState.elementType());

        readState.moveNext();
        assertTrue(readState.exhausted());

        // with a delete
        bases = "ACGT";
        indelCigar = "2M3D2M";

        read1 = createSamRecord(nextReadId(), 100, bases, indelCigar, false);

        readState = new ReadParseState(read1, true);

        assertEquals((byte)'A', readState.currentBase());
        assertEquals(M, readState.elementType());

        readState.moveNext();
        assertEquals((byte)'C', readState.currentBase());
        assertEquals(M, readState.elementType());

        readState.moveNext();
        assertEquals((byte)'C', readState.currentBase());
        assertEquals(D, readState.elementType());

        readState.moveNext();
        assertEquals((byte)'C', readState.currentBase());
        assertEquals(D, readState.elementType());

        readState.moveNext();
        assertEquals((byte)'C', readState.currentBase());
        assertEquals(D, readState.elementType());

        readState.moveNext();
        assertEquals((byte)'G', readState.currentBase());
        assertEquals(M, readState.elementType());

        readState.moveNext();
        assertEquals((byte)'T', readState.currentBase());
        assertEquals(M, readState.elementType());

        readState.moveNext();
        assertTrue(readState.exhausted());
    }

    @Test
    public void testSoftClippedOverChromosomeEnd()
    {
        final List<SAMRecord> reads = Lists.newArrayList();

        int posStart = REF_BASES.length() - 5;
        String readBases1 = REF_BASES.substring(posStart, REF_BASES.length()) + "T".repeat(5);
        reads.add(createSamRecord(nextReadId(), posStart, readBases1, "5M5S", false));

        String readBases2 = REF_BASES.substring(posStart, REF_BASES.length()) + "A".repeat(5);
        reads.add(createSamRecord(nextReadId(), posStart, readBases2, "5M5S", false));

        ConsensusReads consensusReads = new ConsensusReads(mRefGenomeOneBased);
        ConsensusReadInfo readInfo = consensusReads.createConsensusRead(reads, UMI_ID_1);
        assertEquals(ALIGNMENT_ONLY, readInfo.Outcome);

        consensusReads = new ConsensusReads(mRefGenomeOneBased);
        consensusReads.setChromosomeLength(mRefGenomeOneBased.getChromosomeLength(CHR_1));
        readInfo = consensusReads.createConsensusRead(reads, UMI_ID_1);
        assertEquals(ALIGNMENT_ONLY, readInfo.Outcome);
    }

    @Test
    public void testSoftClippedOverChromosomeEndWithDel()
    {
        final List<SAMRecord> reads = Lists.newArrayList();

        int posStart = REF_BASES.length() - 5;
        String readBases1 = REF_BASES.substring(posStart, REF_BASES.length()) + "T".repeat(5);
        reads.add(createSamRecord(nextReadId(), posStart, readBases1, "2M1D2M5S", false));

        String readBases2 = REF_BASES.substring(posStart, REF_BASES.length()) + "A".repeat(5);
        reads.add(createSamRecord(nextReadId(), posStart, readBases2, "1M1D3M5S", false));

        ConsensusReads consensusReads = new ConsensusReads(mRefGenomeOneBased);
        ConsensusReadInfo readInfo = consensusReads.createConsensusRead(reads, UMI_ID_1);
        assertEquals(INDEL_MISMATCH, readInfo.Outcome);

        consensusReads = new ConsensusReads(mRefGenomeOneBased);
        consensusReads.setChromosomeLength(mRefGenomeOneBased.getChromosomeLength(CHR_1));
        readInfo = consensusReads.createConsensusRead(reads, UMI_ID_1);
        assertEquals(INDEL_MISMATCH, readInfo.Outcome);
    }

    @Test
    public void testSoftClippedOverChromosomeStart()
    {
        final List<SAMRecord> reads = Lists.newArrayList();

        String readBases1 = "T".repeat(5) + REF_BASES.substring(0, 5);
        reads.add(createSamRecord(nextReadId(), 1, readBases1, "5S5M", false));

        String readBases2 = "A".repeat(5) + REF_BASES.substring(0, 5);
        reads.add(createSamRecord(nextReadId(), 1, readBases2, "5S5M", false));

        ConsensusReads consensusReads = new ConsensusReads(mRefGenomeOneBased);
        ConsensusReadInfo readInfo = consensusReads.createConsensusRead(reads, UMI_ID_1);
        assertEquals(ALIGNMENT_ONLY, readInfo.Outcome);
    }

    @Test
    public void testSoftClippedOverChromosomeStartWithDel()
    {
        final List<SAMRecord> reads = Lists.newArrayList();

        String readBases1 = "T".repeat(5) + REF_BASES.substring(0, 5);
        reads.add(createSamRecord(nextReadId(), 1, readBases1, "5S2M1D2M", false));

        String readBases2 = "A".repeat(5) + REF_BASES.substring(0, 5);
        reads.add(createSamRecord(nextReadId(), 1, readBases2, "5S1M1D3M", false));

        ConsensusReads consensusReads = new ConsensusReads(mRefGenomeOneBased);
        ConsensusReadInfo readInfo = consensusReads.createConsensusRead(reads, UMI_ID_1);
        assertEquals(INDEL_MISMATCH, readInfo.Outcome);
    }

    @Test
    public void testDualStrandPrefersRefBase()
    {
        int posStart = 11;
        int readLength = 10;
        String cigar = "10M";
        String consensusBases = REF_BASES.substring(posStart, posStart + readLength);
        SAMRecord read1 = createSamRecord(nextReadId(), posStart, consensusBases, cigar, false);

        int mutatedBaseIndex = 5;
        char mutatedBase = mNextBaseMap.get(consensusBases.charAt(mutatedBaseIndex));
        StringBuilder mutatedBasesBuilder = new StringBuilder(consensusBases);
        mutatedBasesBuilder.setCharAt(mutatedBaseIndex, mutatedBase);
        String mutatedBases = mutatedBasesBuilder.toString();
        SAMRecord read2 = createSamRecord(nextReadId(), posStart, mutatedBases, cigar, false);
        setSecondInPair(read2);

        SAMRecord read3 = createSamRecord(nextReadId(), posStart, mutatedBases, cigar, false);
        setSecondInPair(read3);

        List<SAMRecord> reads = Lists.newArrayList(read1, read2, read3);
        ConsensusReadInfo readInfo = mConsensusReads.createConsensusRead(reads, UMI_ID_1);

        assertEquals(ALIGNMENT_ONLY, readInfo.Outcome);
        assertEquals(consensusBases, readInfo.ConsensusRead.getReadString());
        assertEquals("10M", readInfo.ConsensusRead.getCigarString());
        assertEquals(37, (int) readInfo.ConsensusRead.getBaseQualities()[0]); // non mutated base
        assertEquals(0, (int) readInfo.ConsensusRead.getBaseQualities()[mutatedBaseIndex]); // mutated base
        assertEquals(posStart, readInfo.ConsensusRead.getAlignmentStart());
    }

    @Test
    public void testDualStrandPrefersRefBaseWithDel()
    {
        int posStart = 11;
        int readLength = 10;
        String readBasesWithRef = REF_BASES.substring(posStart, posStart + readLength);
        SAMRecord read1 = createSamRecord(nextReadId(), posStart, readBasesWithRef, "1M1D8M", false);

        int mutatedBaseIndex = 5;
        char mutatedBase = mNextBaseMap.get(readBasesWithRef.charAt(mutatedBaseIndex));
        StringBuilder mutatedBasesBuilder = new StringBuilder(readBasesWithRef);
        mutatedBasesBuilder.setCharAt(mutatedBaseIndex, mutatedBase);
        String mutatedBases = mutatedBasesBuilder.toString();
        SAMRecord read2 = createSamRecord(nextReadId(), posStart, mutatedBases, "2M1D7M", false);
        setSecondInPair(read2);

        SAMRecord read3 = createSamRecord(nextReadId(), posStart, mutatedBases, "2M1D7M", false);
        setSecondInPair(read3);

        List<SAMRecord> reads = Lists.newArrayList(read1, read2, read3);
        ConsensusReadInfo readInfo = mConsensusReads.createConsensusRead(reads, UMI_ID_1);

        int delIdx = 2;
        StringBuilder consensusBasesBuilder = new StringBuilder(readBasesWithRef);
        consensusBasesBuilder.deleteCharAt(delIdx);
        consensusBasesBuilder.append((char) NO_BASE);
        String consensusBases = consensusBasesBuilder.toString();

        assertEquals(INDEL_MISMATCH, readInfo.Outcome);
        assertEquals(consensusBases, readInfo.ConsensusRead.getReadString());
        assertEquals("2M1D7M", readInfo.ConsensusRead.getCigarString());
        assertEquals(posStart, readInfo.ConsensusRead.getAlignmentStart());
    }

    @Test
    public void testMaxDepthFiltering()
    {
        List<SAMRecord> reads = Lists.newArrayList();

        int posStart = 11;
        int readLength = 10;
        String consensusCigar = "10M";
        String consensusBases = REF_BASES.substring(posStart, posStart + readLength);
        for(int i = 0; i < CONSENSUS_MAX_DEPTH; ++i)
        {
            reads.add(createSamRecord(nextReadId(), posStart, consensusBases, consensusCigar, false));
        }

        int mutatedBaseIndex = 5;
        StringBuilder mutatedBasesBuilder = new StringBuilder(consensusBases);
        mutatedBasesBuilder.setCharAt(mutatedBaseIndex, mNextBaseMap.get(consensusBases.charAt(mutatedBaseIndex)));
        String mutatedBases = mutatedBasesBuilder.toString();
        for(int i = 0; i < CONSENSUS_MAX_DEPTH + 1; ++i)
        {
            reads.add(createSamRecord(nextReadId(), posStart, mutatedBases, consensusCigar, false));
        }

        ConsensusReadInfo readInfo = mConsensusReads.createConsensusRead(reads, UMI_ID_1);
        assertEquals(ALIGNMENT_ONLY, readInfo.Outcome);
        assertEquals(consensusBases, readInfo.ConsensusRead.getReadString());
        assertEquals(consensusCigar, readInfo.ConsensusRead.getCigarString());
        assertEquals(posStart, readInfo.ConsensusRead.getAlignmentStart());
    }

    private static SAMRecord createSamRecord(
            final String readId, int readStart, final String readBases, final String cigar, boolean isReversed)
    {
        return SamRecordTestUtils.createSamRecord(
                readId, CHR_1, readStart, readBases, cigar, CHR_1, 5000, isReversed, false, null);
    }

    private String nextReadId() { return nextUmiReadId(UMI_ID_1, mReadIdGen); }

    public static String nextUmiReadId(final String umiId, final ReadIdGenerator readIdGen)
    {
        String readId = readIdGen.nextId();
        return format("ABCD:%s:%s", readId, umiId);
    }
}
