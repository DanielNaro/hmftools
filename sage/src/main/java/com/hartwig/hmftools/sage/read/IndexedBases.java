package com.hartwig.hmftools.sage.read;

import static com.hartwig.hmftools.sage.read.ReadContextMatch.CORE;
import static com.hartwig.hmftools.sage.read.ReadContextMatch.FULL;
import static com.hartwig.hmftools.sage.read.ReadContextMatch.NONE;
import static com.hartwig.hmftools.sage.read.ReadContextMatch.PARTIAL;

import java.util.Arrays;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

public class IndexedBases {

    @NotNull
    public static IndexedBases resize(final int position, final int recordIndex, final int recordLeftCoreIndex,
            final int recordRightCoreIndex, final int flankSize, final byte[] recordBases) {
        int recordLeftFlankIndex = Math.max(0, recordLeftCoreIndex - flankSize);
        int recordLeftFlankLength = recordLeftCoreIndex - recordLeftFlankIndex;
        int recordRightFlankIndex = Math.min(recordBases.length - 1, recordRightCoreIndex + flankSize);

        int rightCentreIndex = recordLeftFlankLength + recordRightCoreIndex - recordLeftCoreIndex;
        int index = recordLeftFlankLength + recordIndex - recordLeftCoreIndex;
        byte[] bases = Arrays.copyOfRange(recordBases, recordLeftFlankIndex, recordRightFlankIndex + 1);
        return new IndexedBases(position, index, recordLeftFlankLength, rightCentreIndex, flankSize, bases);
    }

    private final int position;
    private final int index;
    private final int flankSize;
    private final int leftFlankIndex;
    private final int leftCoreIndex;
    private final int rightCoreIndex;
    private final int rightFlankIndex;
    private final byte[] bases;

    public IndexedBases(final int position, final int index, final byte[] bases) {
        this.position = position;
        this.index = index;
        this.leftCoreIndex = index;
        this.rightCoreIndex = index;
        this.bases = bases;
        this.leftFlankIndex = index;
        this.rightFlankIndex = index;
        this.flankSize = 0;
    }

    public IndexedBases(final int position, final int index, final int leftCoreIndex, int rightCoreIndex, int flankSize,
            final byte[] bases) {
        this.position = position;
        this.index = index;
        this.leftCoreIndex = leftCoreIndex;
        this.rightCoreIndex = rightCoreIndex;
        this.bases = bases;
        this.leftFlankIndex = Math.max(0, leftCoreIndex - flankSize);
        this.rightFlankIndex = Math.min(bases.length - 1, rightCoreIndex + flankSize);
        this.flankSize = flankSize;
    }

    boolean flanksComplete() {
        return leftFlankLength() == flankSize && rightFlankLength() == flankSize;
    }

    boolean coreComplete() {
        return leftFlankIndex < leftCoreIndex && rightCoreIndex < rightFlankIndex;
    }

    public boolean phased(int offset, @NotNull final IndexedBases other) {
        int otherReadIndex = other.index + offset;

        boolean centreMatch = coreMatch(otherReadIndex, other.bases);
        if (!centreMatch) {
            return false;
        }

        boolean otherCentreMatch = other.coreMatch(index - offset, bases);
        if (!otherCentreMatch) {
            return false;
        }

        int leftFlankingBases = leftFlankMatchingBases(otherReadIndex, other.bases);
        if (leftFlankingBases < 0) {
            return false;
        }

        int rightFlankingBases = rightFlankMatchingBases(otherReadIndex, other.bases);
        return rightFlankingBases >= 0 && (rightFlankingBases >= flankSize || leftFlankingBases >= flankSize);
    }

    public boolean isCentreCovered(int otherReadIndex, byte[] otherBases) {

        int otherLeftCentreIndex = otherLeftCentreIndex(otherReadIndex);
        if (otherLeftCentreIndex < 0) {
            return false;
        }

        int otherRightCentreIndex = otherRightCentreIndex(otherReadIndex);
        return otherRightCentreIndex < otherBases.length;
    }

    @NotNull
    public ReadContextMatch matchAtPosition(int otherReadIndex, byte[] otherBases) {

        if (otherReadIndex < 0) {
            return NONE;
        }

        boolean centreMatch = coreMatch(otherReadIndex, otherBases);
        if (!centreMatch) {
            return NONE;
        }

        int leftFlankingBases = leftFlankMatchingBases(otherReadIndex, otherBases);
        if (leftFlankingBases < 0) {
            return CORE;
        }

        int rightFlankingBases = rightFlankMatchingBases(otherReadIndex, otherBases);
        if (rightFlankingBases < 0) {
            return CORE;
        }

        if (leftFlankingBases != flankSize && rightFlankingBases != flankSize) {
            return CORE;
        }

        return leftFlankingBases == rightFlankingBases ? FULL : PARTIAL;
    }

    boolean coreMatch(final int otherRefIndex, final byte[] otherBases) {

        int otherLeftCentreIndex = otherLeftCentreIndex(otherRefIndex);
        if (otherLeftCentreIndex < 0) {
            return false;
        }

        int otherRightCentreIndex = otherRightCentreIndex(otherRefIndex);
        if (otherRightCentreIndex >= otherBases.length) {
            return false;
        }

        for (int i = 0; i < centreLength(); i++) {
            if (bases[leftCoreIndex + i] != otherBases[otherLeftCentreIndex + i]) {
                return false;
            }
        }

        return true;
    }

    int rightFlankMatchingBases(int otherRefIndex, byte[] otherBases) {
        int otherRightCentreIndex = otherRefIndex + rightCoreIndex - index;
        int otherRightFlankLength = Math.min(otherBases.length - 1, otherRightCentreIndex + this.flankSize) - otherRightCentreIndex;
        int maxLength = Math.min(rightFlankLength(), otherRightFlankLength);

        for (int i = 1; i <= maxLength; i++) {
            if (bases[rightCoreIndex + i] != otherBases[otherRightCentreIndex + i]) {
                return -1;
            }
        }

        return maxLength;
    }

    int leftFlankMatchingBases(int otherRefIndex, byte[] otherBases) {
        int otherLeftCentreIndex = otherRefIndex + leftCoreIndex - index;
        int otherLeftFlankLength = otherLeftCentreIndex - Math.max(0, otherLeftCentreIndex - this.flankSize);
        int totalLength = Math.min(leftFlankLength(), otherLeftFlankLength);

        for (int i = 1; i <= totalLength; i++) {
            if (bases[leftCoreIndex - i] != otherBases[otherLeftCentreIndex - i]) {
                return -1;
            }
        }

        return totalLength;
    }

    private int otherLeftCentreIndex(int otherRefIndex) {
        return otherRefIndex + leftCoreIndex - index;
    }

    private int otherRightCentreIndex(int otherRefIndex) {
        return otherRefIndex + rightCoreIndex - index;
    }

    @NotNull
    public String centerString() {
        return bases.length == 0 ? Strings.EMPTY : new String(bases, leftCoreIndex, centreLength());
    }

    @Override
    public String toString() {
        return bases.length == 0 ? Strings.EMPTY : new String(bases, leftFlankIndex, length());
    }

    public int flankSize() {
        return flankSize;
    }

    public int index() {
        return index;
    }

    public int index(int position) {
        return position - this.position + index();
    }

    public byte[] bases() {
        return bases;
    }

    private int length() {
        return rightFlankIndex - leftFlankIndex + 1;
    }

    private int leftFlankLength() {
        return leftCoreIndex - leftFlankIndex;
    }

    private int rightFlankLength() {
        return rightFlankIndex - rightCoreIndex;
    }

    private int centreLength() {
        return rightCoreIndex - leftCoreIndex + 1;
    }

    public int leftCentreIndex() {
        return leftCoreIndex;
    }

    public int rightCentreIndex() {
        return rightCoreIndex;
    }

    public int position() {
        return position;
    }

    public int leftFlankIndex() {
        return leftFlankIndex;
    }

    public int rightFlankIndex() {
        return rightFlankIndex;
    }

    public byte base(int position) {
        return bases[position - this.position + index];
    }

    public byte[] trinucleotideContext(int position) {
        return new byte[] { base(position - 1), base(position), base(position + 1) };
    }

}
