package com.hartwig.hmftools.common.utils;

import java.util.Comparator;

public final class Doubles {

    private static final double EPSILON = 1e-10;

    public static boolean equal(double first, double second) {
        return Math.abs(first - second) < EPSILON;
    }

    public static boolean isZero(double value) {
        return equal(value, 0);
    }

    public static boolean lessThan(double value, double reference) {
        return value - reference < -EPSILON;
    }

    public static boolean lessOrEqual(double value, double reference) {
        return value - reference < EPSILON;
    }

    public static boolean greaterThan(double value, double reference) {
        return value - reference > EPSILON;
    }

    public static boolean greaterOrEqual(double value, double reference) {
        return value - reference > -EPSILON;
    }

    public static double replaceNaNWithZero(double value) {
        return Double.isNaN(value) ? 0d : value;
    }

    public static boolean positive(double value) {
        return greaterThan(value, 0);
    }

    public static boolean positiveOrZero(double value) {
        return greaterOrEqual(value, 0);
    }

    public static double absDistanceFromInteger(double value) {
        return Math.abs((value - Math.round(value)));
    }

    public static Comparator<Double> comparator() {
        return (o1, o2) -> Doubles.equal(o1, o2) ? 0 :  Double.compare(o1, o2);
    }
}
