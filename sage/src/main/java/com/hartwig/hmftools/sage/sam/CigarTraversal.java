package com.hartwig.hmftools.sage.sam;

import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.SAMRecord;

public class CigarTraversal {

    public static void traverseCigar(@NotNull final SAMRecord record, @NotNull final CigarHandler handler) {
        final Cigar cigar = record.getCigar();

        int readIndex = 0;
        int refBase = record.getAlignmentStart();

        for (int i = 0; i < cigar.numCigarElements(); i++) {
            final CigarElement e = cigar.getCigarElement(i);
            switch (e.getOperator()) {
                case H:
                    break; // ignore hard clips
                case P:
                    break; // ignore pads
                case S:
                    readIndex += e.getLength();
                    break; // soft clip read bases
                case N:
                    refBase += e.getLength();
                    break;  // reference skip
                case D:
                    handler.handleDelete(record, e, readIndex - 1, refBase - 1);
                    refBase += e.getLength();
                    break;
                case I:
                    // TODO: Handle 1I150M
                    int refIndex = refBase - 1 - record.getAlignmentStart();
                    if (refIndex >= 0) {
                        handler.handleInsert(record, e, readIndex - 1, refBase - 1);
                    }
                    readIndex += e.getLength();
                    break;
                case M:
                case EQ:
                case X:
                    boolean isFollowedByIndel = i < cigar.numCigarElements() - 1 && cigar.getCigarElement(i + 1).getOperator().isIndel();
                    final CigarElement element = isFollowedByIndel ? new CigarElement(e.getLength() - 1, e.getOperator()) : e;
                    handler.handleAlignment(record, element, readIndex, refBase);
                    readIndex += e.getLength();
                    refBase += e.getLength();
                    break;
                default:
                    throw new IllegalStateException("Case statement didn't deal with op: " + e.getOperator() + "in CIGAR: " + cigar);
            }
        }
    }
}
