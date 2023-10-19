package com.hartwig.hmftools.common.genome.refgenome;

import com.hartwig.hmftools.common.genome.chromosome.Chromosome;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RefGenomeCoordinatesTest {

    @Test
    public void testLengths_V37() {
        Map<Chromosome,Integer> expected = new HashMap<>();
        expected.put(HumanChromosome.fromString("1"), 249250621);
        expected.put(HumanChromosome.fromString("2"), 243199373);
        expected.put(HumanChromosome.fromString("3"), 198022430);
        expected.put(HumanChromosome.fromString("4"), 191154276);
        expected.put(HumanChromosome.fromString("5"), 180915260);
        expected.put(HumanChromosome.fromString("6"), 171115067);
        expected.put(HumanChromosome.fromString("7"), 159138663);
        expected.put(HumanChromosome.fromString("8"), 146364022);
        expected.put(HumanChromosome.fromString("9"), 141213431);
        expected.put(HumanChromosome.fromString("10"), 135534747);
        expected.put(HumanChromosome.fromString("11"), 135006516);
        expected.put(HumanChromosome.fromString("12"), 133851895);
        expected.put(HumanChromosome.fromString("13"), 115169878);
        expected.put(HumanChromosome.fromString("14"), 107349540);
        expected.put(HumanChromosome.fromString("15"), 102531392);
        expected.put(HumanChromosome.fromString("16"), 90354753);
        expected.put(HumanChromosome.fromString("17"), 81195210);
        expected.put(HumanChromosome.fromString("18"), 78077248);
        expected.put(HumanChromosome.fromString("19"), 59128983);
        expected.put(HumanChromosome.fromString("20"), 63025520);
        expected.put(HumanChromosome.fromString("21"), 48129895);
        expected.put(HumanChromosome.fromString("22"), 51304566);
        expected.put(HumanChromosome.fromString("X"), 155270560);
        expected.put(HumanChromosome.fromString("Y"), 59373566);
        assertEquals(expected, RefGenomeCoordinates.COORDS_37.lengths());
    }

    @Test
    public void testLengths_V38() {
        Map<Chromosome,Integer> expected = new HashMap<>();
        expected.put(HumanChromosome.fromString("1"), 248956422);
        expected.put(HumanChromosome.fromString("2"), 242193529);
        expected.put(HumanChromosome.fromString("3"), 198295559);
        expected.put(HumanChromosome.fromString("4"), 190214555);
        expected.put(HumanChromosome.fromString("5"), 181538259);
        expected.put(HumanChromosome.fromString("6"), 170805979);
        expected.put(HumanChromosome.fromString("7"), 159345973);
        expected.put(HumanChromosome.fromString("8"), 145138636);
        expected.put(HumanChromosome.fromString("9"), 138394717);
        expected.put(HumanChromosome.fromString("10"), 133797422);
        expected.put(HumanChromosome.fromString("11"), 135086622);
        expected.put(HumanChromosome.fromString("12"), 133275309);
        expected.put(HumanChromosome.fromString("13"), 114364328);
        expected.put(HumanChromosome.fromString("14"), 107043718);
        expected.put(HumanChromosome.fromString("15"), 101991189);
        expected.put(HumanChromosome.fromString("16"), 90338345);
        expected.put(HumanChromosome.fromString("17"), 83257441);
        expected.put(HumanChromosome.fromString("18"), 80373285);
        expected.put(HumanChromosome.fromString("19"), 58617616);
        expected.put(HumanChromosome.fromString("20"), 64444167);
        expected.put(HumanChromosome.fromString("21"), 46709983);
        expected.put(HumanChromosome.fromString("22"), 50818468);
        expected.put(HumanChromosome.fromString("X"), 156040895);
        expected.put(HumanChromosome.fromString("Y"), 57227415);
        assertEquals(expected, RefGenomeCoordinates.COORDS_38.lengths());
    }

    @Test
    public void testCentromeres_V37() {
        Map<Chromosome,Integer> expected = new HashMap<>();
        expected.put(HumanChromosome.fromString("1"), 123035434);
        expected.put(HumanChromosome.fromString("2"), 93826171);
        expected.put(HumanChromosome.fromString("3"), 92004854);
        expected.put(HumanChromosome.fromString("4"), 51160117);
        expected.put(HumanChromosome.fromString("5"), 47905641);
        expected.put(HumanChromosome.fromString("6"), 60330166);
        expected.put(HumanChromosome.fromString("7"), 59554331);
        expected.put(HumanChromosome.fromString("8"), 45338887);
        expected.put(HumanChromosome.fromString("9"), 48867679);
        expected.put(HumanChromosome.fromString("10"), 40754935);
        expected.put(HumanChromosome.fromString("11"), 53144205);
        expected.put(HumanChromosome.fromString("12"), 36356694);
        expected.put(HumanChromosome.fromString("13"), 17500000);
        expected.put(HumanChromosome.fromString("14"), 17500000);
        expected.put(HumanChromosome.fromString("15"), 18500000);
        expected.put(HumanChromosome.fromString("16"), 36835801);
        expected.put(HumanChromosome.fromString("17"), 23763006);
        expected.put(HumanChromosome.fromString("18"), 16960898);
        expected.put(HumanChromosome.fromString("19"), 26181782);
        expected.put(HumanChromosome.fromString("20"), 27869569);
        expected.put(HumanChromosome.fromString("21"), 12788129);
        expected.put(HumanChromosome.fromString("22"), 14500000);
        expected.put(HumanChromosome.fromString("X"), 60132012);
        expected.put(HumanChromosome.fromString("Y"), 11604553);
        assertEquals(expected, RefGenomeCoordinates.COORDS_37.centromeres());
    }

    @Test
    public void testCentromeres_V38() {
        Map<Chromosome,Integer> expected = new HashMap<>();
        expected.put(HumanChromosome.fromString("1"), 123605523);
        expected.put(HumanChromosome.fromString("2"), 93139351);
        expected.put(HumanChromosome.fromString("3"), 92214016);
        expected.put(HumanChromosome.fromString("4"), 50726026);
        expected.put(HumanChromosome.fromString("5"), 48272854);
        expected.put(HumanChromosome.fromString("6"), 59191911);
        expected.put(HumanChromosome.fromString("7"), 59498944);
        expected.put(HumanChromosome.fromString("8"), 44955505);
        expected.put(HumanChromosome.fromString("9"), 44377363);
        expected.put(HumanChromosome.fromString("10"), 40640102);
        expected.put(HumanChromosome.fromString("11"), 52751711);
        expected.put(HumanChromosome.fromString("12"), 35977330);
        expected.put(HumanChromosome.fromString("13"), 17025624);
        expected.put(HumanChromosome.fromString("14"), 17086762);
        expected.put(HumanChromosome.fromString("15"), 18362627);
        expected.put(HumanChromosome.fromString("16"), 37295920);
        expected.put(HumanChromosome.fromString("17"), 24849830);
        expected.put(HumanChromosome.fromString("18"), 18161053);
        expected.put(HumanChromosome.fromString("19"), 25844927);
        expected.put(HumanChromosome.fromString("20"), 28237290);
        expected.put(HumanChromosome.fromString("21"), 11890184);
        expected.put(HumanChromosome.fromString("22"), 14004553);
        expected.put(HumanChromosome.fromString("X"), 60509061);
        expected.put(HumanChromosome.fromString("Y"), 10430492);
        assertEquals(expected, RefGenomeCoordinates.COORDS_38.centromeres());
    }

    @Test
    public void testLength_v37() {
        Map<Chromosome,Integer> expected = new HashMap<>();
        expected.put(HumanChromosome.fromString("1"), 249250621);
        expected.put(HumanChromosome.fromString("2"), 243199373);
        expected.put(HumanChromosome.fromString("3"), 198022430);
        expected.put(HumanChromosome.fromString("4"), 191154276);
        expected.put(HumanChromosome.fromString("5"), 180915260);
        expected.put(HumanChromosome.fromString("6"), 171115067);
        expected.put(HumanChromosome.fromString("7"), 159138663);
        expected.put(HumanChromosome.fromString("8"), 146364022);
        expected.put(HumanChromosome.fromString("9"), 141213431);
        expected.put(HumanChromosome.fromString("10"), 135534747);
        expected.put(HumanChromosome.fromString("11"), 135006516);
        expected.put(HumanChromosome.fromString("12"), 133851895);
        expected.put(HumanChromosome.fromString("13"), 115169878);
        expected.put(HumanChromosome.fromString("14"), 107349540);
        expected.put(HumanChromosome.fromString("15"), 102531392);
        expected.put(HumanChromosome.fromString("16"), 90354753);
        expected.put(HumanChromosome.fromString("17"), 81195210);
        expected.put(HumanChromosome.fromString("18"), 78077248);
        expected.put(HumanChromosome.fromString("19"), 59128983);
        expected.put(HumanChromosome.fromString("20"), 63025520);
        expected.put(HumanChromosome.fromString("21"), 48129895);
        expected.put(HumanChromosome.fromString("22"), 51304566);
        expected.put(HumanChromosome.fromString("X"), 155270560);
        expected.put(HumanChromosome.fromString("Y"), 59373566);

        expected.forEach((chromosome, integer) -> assertEquals(integer.longValue(),
                RefGenomeCoordinates.COORDS_37.length(chromosome.toString())));
    }

    @Test
    public void testLength_v38() {
        Map<Chromosome,Integer> expected = new HashMap<>();
        expected.put(HumanChromosome.fromString("1"), 248956422);
        expected.put(HumanChromosome.fromString("2"), 242193529);
        expected.put(HumanChromosome.fromString("3"), 198295559);
        expected.put(HumanChromosome.fromString("4"), 190214555);
        expected.put(HumanChromosome.fromString("5"), 181538259);
        expected.put(HumanChromosome.fromString("6"), 170805979);
        expected.put(HumanChromosome.fromString("7"), 159345973);
        expected.put(HumanChromosome.fromString("8"), 145138636);
        expected.put(HumanChromosome.fromString("9"), 138394717);
        expected.put(HumanChromosome.fromString("10"), 133797422);
        expected.put(HumanChromosome.fromString("11"), 135086622);
        expected.put(HumanChromosome.fromString("12"), 133275309);
        expected.put(HumanChromosome.fromString("13"), 114364328);
        expected.put(HumanChromosome.fromString("14"), 107043718);
        expected.put(HumanChromosome.fromString("15"), 101991189);
        expected.put(HumanChromosome.fromString("16"), 90338345);
        expected.put(HumanChromosome.fromString("17"), 83257441);
        expected.put(HumanChromosome.fromString("18"), 80373285);
        expected.put(HumanChromosome.fromString("19"), 58617616);
        expected.put(HumanChromosome.fromString("20"), 64444167);
        expected.put(HumanChromosome.fromString("21"), 46709983);
        expected.put(HumanChromosome.fromString("22"), 50818468);
        expected.put(HumanChromosome.fromString("X"), 156040895);
        expected.put(HumanChromosome.fromString("Y"), 57227415);

        expected.forEach((chromosome, integer) -> assertEquals(integer.longValue(),
                RefGenomeCoordinates.COORDS_38.length(chromosome.toString())));
    }

    @Test
    public void testCentromere_V37() {
        Map<Chromosome,Integer> expected = new HashMap<>();
        expected.put(HumanChromosome.fromString("1"), 123035434);
        expected.put(HumanChromosome.fromString("2"), 93826171);
        expected.put(HumanChromosome.fromString("3"), 92004854);
        expected.put(HumanChromosome.fromString("4"), 51160117);
        expected.put(HumanChromosome.fromString("5"), 47905641);
        expected.put(HumanChromosome.fromString("6"), 60330166);
        expected.put(HumanChromosome.fromString("7"), 59554331);
        expected.put(HumanChromosome.fromString("8"), 45338887);
        expected.put(HumanChromosome.fromString("9"), 48867679);
        expected.put(HumanChromosome.fromString("10"), 40754935);
        expected.put(HumanChromosome.fromString("11"), 53144205);
        expected.put(HumanChromosome.fromString("12"), 36356694);
        expected.put(HumanChromosome.fromString("13"), 17500000);
        expected.put(HumanChromosome.fromString("14"), 17500000);
        expected.put(HumanChromosome.fromString("15"), 18500000);
        expected.put(HumanChromosome.fromString("16"), 36835801);
        expected.put(HumanChromosome.fromString("17"), 23763006);
        expected.put(HumanChromosome.fromString("18"), 16960898);
        expected.put(HumanChromosome.fromString("19"), 26181782);
        expected.put(HumanChromosome.fromString("20"), 27869569);
        expected.put(HumanChromosome.fromString("21"), 12788129);
        expected.put(HumanChromosome.fromString("22"), 14500000);
        expected.put(HumanChromosome.fromString("X"), 60132012);
        expected.put(HumanChromosome.fromString("Y"), 11604553);

        expected.forEach((chromosome, integer) -> assertEquals(integer.longValue(),
                RefGenomeCoordinates.COORDS_37.centromere(chromosome.toString())));
    }

    @Test
    public void testCentromere_V38() {
        Map<Chromosome,Integer> expected = new HashMap<>();
        expected.put(HumanChromosome.fromString("1"), 123605523);
        expected.put(HumanChromosome.fromString("2"), 93139351);
        expected.put(HumanChromosome.fromString("3"), 92214016);
        expected.put(HumanChromosome.fromString("4"), 50726026);
        expected.put(HumanChromosome.fromString("5"), 48272854);
        expected.put(HumanChromosome.fromString("6"), 59191911);
        expected.put(HumanChromosome.fromString("7"), 59498944);
        expected.put(HumanChromosome.fromString("8"), 44955505);
        expected.put(HumanChromosome.fromString("9"), 44377363);
        expected.put(HumanChromosome.fromString("10"), 40640102);
        expected.put(HumanChromosome.fromString("11"), 52751711);
        expected.put(HumanChromosome.fromString("12"), 35977330);
        expected.put(HumanChromosome.fromString("13"), 17025624);
        expected.put(HumanChromosome.fromString("14"), 17086762);
        expected.put(HumanChromosome.fromString("15"), 18362627);
        expected.put(HumanChromosome.fromString("16"), 37295920);
        expected.put(HumanChromosome.fromString("17"), 24849830);
        expected.put(HumanChromosome.fromString("18"), 18161053);
        expected.put(HumanChromosome.fromString("19"), 25844927);
        expected.put(HumanChromosome.fromString("20"), 28237290);
        expected.put(HumanChromosome.fromString("21"), 11890184);
        expected.put(HumanChromosome.fromString("22"), 14004553);
        expected.put(HumanChromosome.fromString("X"), 60509061);
        expected.put(HumanChromosome.fromString("Y"), 10430492);

        expected.forEach((chromosome, integer) -> assertEquals(integer.longValue(),
                RefGenomeCoordinates.COORDS_38.centromere(chromosome.toString())));
    }
}