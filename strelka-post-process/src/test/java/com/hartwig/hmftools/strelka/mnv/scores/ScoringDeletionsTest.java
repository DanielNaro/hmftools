package com.hartwig.hmftools.strelka.mnv.scores;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.google.common.io.Resources;
import com.hartwig.hmftools.strelka.mnv.TestUtils;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import htsjdk.samtools.SAMRecord;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;

public class ScoringDeletionsTest {
    private static final File VCF_FILE = new File(Resources.getResource("mnvs.vcf").getPath());
    private static final VCFFileReader VCF_FILE_READER = new VCFFileReader(VCF_FILE, false);
    private static final List<VariantContext> VARIANTS = Streams.stream(VCF_FILE_READER).collect(Collectors.toList());
    private static final VariantContext DELETION = VARIANTS.get(1);

    @Test
    public void doesNotDetectDELinRef() {
        final SAMRecord reference = TestUtils.buildSamRecord(1, "11M", "GATCCCCGATC");
        final VariantScore score = SamRecordScoring.getVariantScore(reference, DELETION);
        assertEquals(ReadType.REF, score.type());
        assertTrue(score.score() > 0);
    }

    @Test
    public void detectsDELinTumor() {
        final SAMRecord tumor = TestUtils.buildSamRecord(1, "3M2D6M", "GATCCGATC");
        final VariantScore score = SamRecordScoring.getVariantScore(tumor, DELETION);
        assertEquals(ReadType.ALT, score.type());
        assertTrue(score.score() > 0);
    }

    @Test
    public void detectsDELinTumorWithINSandDEL() {
        final SAMRecord tumor = TestUtils.buildSamRecord(1, "2M2I1M2D4M", "GATCTCCGA");
        final VariantScore score = SamRecordScoring.getVariantScore(tumor, DELETION);
        assertEquals(ReadType.ALT, score.type());
        assertTrue(score.score() > 0);
    }

    @Test
    public void detectsDELinTumorWithDELAfter() {
        final SAMRecord tumor = TestUtils.buildSamRecord(1, "3M2D2M3D1M", "GATCCC");
        final VariantScore score = SamRecordScoring.getVariantScore(tumor, DELETION);
        assertEquals(ReadType.ALT, score.type());
        assertTrue(score.score() > 0);
    }

    @Test
    public void detectsDELinTumorWithDELPre() {
        final SAMRecord tumor = TestUtils.buildSamRecord(1, "1M1D1M2D6M", "GTCCGATC");
        final VariantScore score = SamRecordScoring.getVariantScore(tumor, DELETION);
        assertEquals(ReadType.ALT, score.type());
        assertTrue(score.score() > 0);
    }

    @Test
    public void detectsDELinTumorWithINSAfter() {
        final SAMRecord tumor = TestUtils.buildSamRecord(1, "3M2D2M2I4M", "GATCCAAGATC");
        final VariantScore score = SamRecordScoring.getVariantScore(tumor, DELETION);
        assertEquals(ReadType.ALT, score.type());
        assertTrue(score.score() > 0);
    }

    @Test
    public void detectsDELinTumorWithINSPre() {
        final SAMRecord tumor = TestUtils.buildSamRecord(1, "2M2I1M2D6M", "GAAATCCGATC");
        final VariantScore score = SamRecordScoring.getVariantScore(tumor, DELETION);
        assertEquals(ReadType.ALT, score.type());
        assertTrue(score.score() > 0);
    }

    @Test
    public void detectsDELatEndOfRead() {
        final SAMRecord tumor = TestUtils.buildSamRecord(1, "3M2D1M", "GATC");
        final VariantScore score = SamRecordScoring.getVariantScore(tumor, DELETION);
        assertEquals(ReadType.ALT, score.type());
        assertTrue(score.score() > 0);
    }

    @Test
    public void detectsDELatEndOfReadWithoutMatchAfter() {
        final SAMRecord tumor = TestUtils.buildSamRecord(1, "3M2D", "GAT");
        final VariantScore score = SamRecordScoring.getVariantScore(tumor, DELETION);
        assertEquals(ReadType.ALT, score.type());
        assertTrue(score.score() > 0);
    }

    @Test
    public void computesScoreForDELinRef() {
        // Ref with qualities 25, 15, 35 --> average = 25
        final SAMRecord ref = TestUtils.buildSamRecord(3, "3M", "TCC", ":0D", false);
        assertEquals(ImmutableVariantScore.of(ReadType.REF, 25), SamRecordScoring.getVariantScore(ref, DELETION));
    }

    @Test
    public void computesScoreForDELinTumor() {
        // Take quality of first base after deletion if available
        final SAMRecord alt = TestUtils.buildSamRecord(3, "1M2D1M", "TT", "PA", false);
        assertEquals(ImmutableVariantScore.of(ReadType.ALT, 32), SamRecordScoring.getVariantScore(alt, DELETION));
    }

    @Test
    public void computesScoreForDELinTumorWithDelAtEnd() {
        // Take quality of base before deletion if base after deletion not present
        final SAMRecord shortAlt = TestUtils.buildSamRecord(3, "1M2D", "T", "P", false);
        assertEquals(ImmutableVariantScore.of(ReadType.ALT, 47), SamRecordScoring.getVariantScore(shortAlt, DELETION));
    }

    @Test
    public void computesScoreForDELinOther() {
        final SAMRecord otherSNV = TestUtils.buildSamRecord(3, "2M", "TG", "FF", false);
        assertEquals(ImmutableVariantScore.of(ReadType.REF, 37), SamRecordScoring.getVariantScore(otherSNV, DELETION));
    }

    @Test
    public void computesScoreForDELinReadWithDeletionOnVariantPos() {
        final SAMRecord deleted = TestUtils.buildSamRecord(2, "1M1D2M", "ACC", "FD", false);
        assertEquals(ImmutableVariantScore.of(ReadType.MISSING, 0), SamRecordScoring.getVariantScore(deleted, DELETION));
    }

    @Test
    public void computesScoreForDELinReadWithPartialDeletion() {
        // Read with partial deletion TC -> T instead of TCC -> T
        // Ref with qualities 32, 40 --> average = 36
        final SAMRecord otherDeletion = TestUtils.buildSamRecord(3, "1M1D1M", "TC", "AI", false);
        assertEquals(ImmutableVariantScore.of(ReadType.REF, 36), SamRecordScoring.getVariantScore(otherDeletion, DELETION));
    }

    @Test
    public void doesNotComputeScoreForShorterDELinTumor() {
        final SAMRecord alt = TestUtils.buildSamRecord(3, "1M1D1M", "TT", "PA", false);
        assertEquals(ImmutableVariantScore.of(ReadType.REF, 39), SamRecordScoring.getVariantScore(alt, DELETION));
    }

    @Test
    public void doesNotComputesScoreForDELinLongerDELinTumor() {
        final SAMRecord alt = TestUtils.buildSamRecord(3, "1M3D", "T", "P", false);
        final VariantScore score = SamRecordScoring.getVariantScore(alt, DELETION);
        assertEquals(ReadType.REF, score.type());
        assertEquals(47, score.score());
    }

    @Test
    public void doesNotComputesScoreForDELinLongerDELinTumor2() {
        final SAMRecord alt = TestUtils.buildSamRecord(3, "1M3D1M", "TT", "PA", false);
        final VariantScore score = SamRecordScoring.getVariantScore(alt, DELETION);
        assertEquals(ReadType.REF, score.type());
        assertEquals(39, score.score());
    }

    @Test
    public void containsDeletionMNVTest() {
        final VariantContext DELETION = VARIANTS.get(3);
        final VariantContext SNV = VARIANTS.get(4);
        final List<SAMRecord> records = deletionMNVRecords();
        final SAMRecord cleanRecord = records.get(0);
        final SAMRecord deletionMNVRecord = records.get(1);
        assertEquals(32, SamRecordScoring.getVariantScore(cleanRecord, DELETION).score());
        assertEquals(ReadType.REF, SamRecordScoring.getVariantScore(cleanRecord, DELETION).type());
        assertEquals(32, SamRecordScoring.getVariantScore(cleanRecord, SNV).score());
        assertEquals(ReadType.REF, SamRecordScoring.getVariantScore(cleanRecord, DELETION).type());
        assertEquals(32, SamRecordScoring.getVariantScore(deletionMNVRecord, DELETION).score());
        assertEquals(ReadType.ALT, SamRecordScoring.getVariantScore(deletionMNVRecord, DELETION).type());
        assertEquals(32, SamRecordScoring.getVariantScore(deletionMNVRecord, SNV).score());
        assertEquals(ReadType.ALT, SamRecordScoring.getVariantScore(deletionMNVRecord, DELETION).type());
    }

    @NotNull
    private List<SAMRecord> deletionMNVRecords() {
        final SAMRecord deletionMNVRecord = TestUtils.buildSamRecord(56654806, "69M1D82M",
                "TCTGTACTTCAGATTAGGAGGAAAAAAAAAAGAAATCAAGCCAGATGCCACAATGGACTAAAACAAGCTTCCGACTTTGCCAGTTGGTTTTGATTGTTTACAAAGAAAAAGCCAAACAAAGAAGGAGGTGGAATTTATTTCAGTAAACAGC");
        final SAMRecord noMNVRecord = TestUtils.buildSamRecord(56654804, "151M",
                "TGTCTGTACTTCAGATTAGGAGGAAAAAAAAAAGAAATCAAGCCAGATGCCACAATGGACTAAAACAAGCTCCCCGACTTTGCCAGTTGGTTTTGATTGTTTACAAAGAAAAAGCCAAACAAAGAAGGAGGTGGAATTTATTTCAGTAAAC");
        return Lists.newArrayList(noMNVRecord, deletionMNVRecord);
    }
}