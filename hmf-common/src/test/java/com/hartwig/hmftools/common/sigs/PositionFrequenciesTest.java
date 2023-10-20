package com.hartwig.hmftools.common.sigs;

import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static com.hartwig.hmftools.common.sigs.PositionFrequencies.buildStandardChromosomeLengths;
import static org.junit.Assert.assertEquals;

public class PositionFrequenciesTest {


    @Test
    public void testBuildStandardChromosomeLengths_V37() {
        Map<String, Integer> expected = new HashMap<>();
        expected.put("1",249250621);
        expected.put("2",243199373);
        expected.put("3",198295559);
        expected.put("4",191154276);
        expected.put("5",181538259);
        expected.put("6",171115067);
        expected.put("7",159345973);
        expected.put("8",146364022);
        expected.put("9",141213431);
        expected.put("10",135534747);
        expected.put("11",135086622);
        expected.put("12",133851895);
        expected.put("13",115169878);
        expected.put("14",107349540);
        expected.put("15",102531392);
        expected.put("16",90354753);
        expected.put("17",83257441);
        expected.put("18",80373285);
        expected.put("19",59128983);
        expected.put("20",64444167);
        expected.put("21",48129895);
        expected.put("22",51304566);
        expected.put("X",156040895);
        expected.put("Y",59373566);

        assertEquals(expected,
                buildStandardChromosomeLengths(RefGenomeVersion.V37)
        );
    }

    @Test
    public void testBuildStandardChromosomeLengths_V38() {
        Map<String, Integer> expected = new HashMap<>();

        expected.put("chr1", 248956422);
        expected.put("chr2", 242193529);
        expected.put("chr3", 198295559);
        expected.put("chr4", 190214555);
        expected.put("chr5", 181538259);
        expected.put("chr6", 170805979);
        expected.put("chr7", 159345973);
        expected.put("chr8", 145138636);
        expected.put("chr9", 138394717);
        expected.put("chr10", 133797422);
        expected.put("chr11", 135086622);
        expected.put("chr12", 133275309);
        expected.put("chr13", 114364328);
        expected.put("chr14", 107043718);
        expected.put("chr15", 101991189);
        expected.put("chr16", 90338345);
        expected.put("chr17", 83257441);
        expected.put("chr18", 80373285);
        expected.put("chr19", 58617616);
        expected.put("chr20", 64444167);
        expected.put("chr21", 46709983);
        expected.put("chr22", 50818468);
        expected.put("chrX", 156040895);
        expected.put("chrY", 57227415);

        assertEquals(expected,
                buildStandardChromosomeLengths(RefGenomeVersion.V38)
        );
    }
}