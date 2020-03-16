package com.hartwig.hmftools.linx.misc;

import static com.hartwig.hmftools.linx.LinxConfig.REF_GENOME_HG37;
import static com.hartwig.hmftools.linx.LinxConfig.REF_GENOME_HG38;
import static com.hartwig.hmftools.linx.LinxConfig.RG_VERSION;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.refGenomeChromosome;
import static com.hartwig.hmftools.linx.types.ChromosomeArm.P_ARM;
import static com.hartwig.hmftools.linx.types.ChromosomeArm.Q_ARM;
import static com.hartwig.hmftools.linx.utils.SvTestUtils.createBnd;
import static com.hartwig.hmftools.linx.utils.SvTestUtils.createDel;
import static com.hartwig.hmftools.linx.utils.SvTestUtils.createDup;
import static com.hartwig.hmftools.linx.utils.SvTestUtils.createIns;
import static com.hartwig.hmftools.linx.utils.SvTestUtils.createInv;
import static com.hartwig.hmftools.linx.utils.SvTestUtils.createSgl;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.appendStr;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.calcConsistency;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.findCentromereBreakendIndex;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.makeChrArmStr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.hartwig.hmftools.linx.analysis.SvUtilities;
import com.hartwig.hmftools.linx.types.SvBreakend;
import com.hartwig.hmftools.linx.types.SvVarData;

import org.junit.Test;

import com.hartwig.hmftools.linx.utils.LinxTester;

public class MiscTest
{

    @Test
    public void testConsistency()
    {
        final SvVarData del = createDel(1, "1", 100, 200);
        assertEquals(calcConsistency(del), 0);

        final SvVarData ins = createIns(1, "1", 100, 200);
        assertEquals(calcConsistency(ins), 0);

        final SvVarData dup = createDup(1, "1", 100, 200);
        assertEquals(calcConsistency(dup), 0);

        final SvVarData inv = createInv(1, "1", 100, 200, 1);
        assertEquals(calcConsistency(inv), 2);

        final SvVarData bnd = createBnd(1, "1", 100, 1, "2", 100, -1);
        assertEquals(calcConsistency(bnd), 0);

        final SvVarData sgl = createSgl(1, "1", 100, 1);
        assertEquals(calcConsistency(sgl), 1);
    }

    @Test
    public void testMiscMethods()
    {
        assertTrue(makeChrArmStr("1", "P").equals("1_P"));

        String test = "something";
        test = appendStr(test, "else", ';');

        assertEquals("something;else", test);
    }

    @Test
    public void testChromosomeConversion()
    {
        String chr37 = "10";
        String chr38 = "chr10";

        RG_VERSION = REF_GENOME_HG37;
        assertEquals(chr37, refGenomeChromosome(chr37));

        RG_VERSION = REF_GENOME_HG38;
        assertEquals(chr38, refGenomeChromosome(chr37));

        assertEquals(chr38, refGenomeChromosome(chr38));

        RG_VERSION = REF_GENOME_HG37;
        assertEquals(chr37, refGenomeChromosome(chr38));
    }

    @Test
    public void testBreakendLists()
    {
        LinxTester tester = new LinxTester();

        // 4 breakends, 2 on each arm
        long centromerePos = SvUtilities.getChromosomalArmLength("1", P_ARM);
        long qArmPos = centromerePos + 10000000;
        final SvVarData var1 = createDel(tester.nextVarId(), "1", 100,200);
        final SvVarData var2 = createDel(tester.nextVarId(), "1", 300,400);
        final SvVarData var3 = createDel(tester.nextVarId(), "1", qArmPos + 1000, qArmPos + 2000);
        final SvVarData var4 = createDel(tester.nextVarId(), "1", qArmPos + 10000,qArmPos + 20000);

        // add them out of order which will require partial chain reconciliation
        tester.AllVariants.add(var1);
        tester.AllVariants.add(var2);
        tester.AllVariants.add(var3);
        tester.AllVariants.add(var4);

        tester.preClusteringInit();

        List<SvBreakend> breakendList = tester.Analyser.getState().getChrBreakendMap().get("1");

        assertEquals(3, findCentromereBreakendIndex(breakendList, P_ARM));
        assertEquals(4, findCentromereBreakendIndex(breakendList, Q_ARM));

        // try again with breakends only in 1 list
        tester.clearClustersAndSVs();
        tester.AllVariants.add(var1);
        tester.AllVariants.add(var2);

        tester.preClusteringInit();

        breakendList = tester.Analyser.getState().getChrBreakendMap().get("1");

        assertEquals(3, findCentromereBreakendIndex(breakendList, P_ARM));
        assertEquals(-1, findCentromereBreakendIndex(breakendList, Q_ARM));

        tester.clearClustersAndSVs();
        tester.AllVariants.add(var3);
        tester.AllVariants.add(var4);

        tester.preClusteringInit();

        breakendList = tester.Analyser.getState().getChrBreakendMap().get("1");

        assertEquals(-1, findCentromereBreakendIndex(breakendList, P_ARM));
        assertEquals(0, findCentromereBreakendIndex(breakendList, Q_ARM));
    }

}
