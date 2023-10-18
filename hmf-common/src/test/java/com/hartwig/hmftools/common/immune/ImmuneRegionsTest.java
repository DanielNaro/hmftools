package com.hartwig.hmftools.common.immune;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.region.ChrBaseRegion;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ImmuneRegionsTest{


    @Test
    public void testGetIgRegions_V37() {
        ChrBaseRegion IGK_REGION_V37 = new ChrBaseRegion("2", 89156674, 90538397);
        ChrBaseRegion IGH_REGION_V37 = new ChrBaseRegion("14", 106032614, 107288051);
        ChrBaseRegion IGL_REGION_V37 = new ChrBaseRegion("22", 22380474, 23265085);

        List<ChrBaseRegion> IG_REGIONS_V37 = Lists.newArrayList(IGK_REGION_V37, IGH_REGION_V37, IGL_REGION_V37);

        assertEquals(IG_REGIONS_V37,
                ImmuneRegions.getIgRegions(RefGenomeVersion.V37));
    }

    @Test
    public void testGetIgRegions_V38() {
        ChrBaseRegion IGK_REGION_V38 = new ChrBaseRegion("chr2", 88857161, 90315836);
        ChrBaseRegion IGH_REGION_V38 = new ChrBaseRegion("chr14", 105586437, 106879844);
        ChrBaseRegion IGL_REGION_V38 = new ChrBaseRegion("chr22", 22026076, 22922913);

        List<ChrBaseRegion> IG_REGIONS_V38 =
                Lists.newArrayList(IGK_REGION_V38, IGH_REGION_V38,
                        IGL_REGION_V38);

        assertEquals(IG_REGIONS_V38,
                ImmuneRegions.getIgRegions(RefGenomeVersion.V38));
    }


    @Test
    public void testGetTrRegions_V37() {
        ChrBaseRegion TRG_REGION_V37 = new ChrBaseRegion("7", 38279181, 38407483);
        ChrBaseRegion TRB_REGION_V37 = new ChrBaseRegion("7", 141999017, 142510554);
        ChrBaseRegion TRAD_REGION_V37 = new ChrBaseRegion("14", 22090446, 23021099);
        List<ChrBaseRegion> TR_REGIONS_V37 = Lists.newArrayList(TRG_REGION_V37, TRB_REGION_V37, TRAD_REGION_V37);

        assertEquals(TR_REGIONS_V37,
                ImmuneRegions.getTrRegions(RefGenomeVersion.V37));
    }

    @Test
    public void testGetTrRegions_V38() {
        ChrBaseRegion TRG_REGION_V38 = new ChrBaseRegion("chr7", 38239580, 38367882);
        ChrBaseRegion TRB_REGION_V38 = new ChrBaseRegion("chr7", 142299177, 142812869);
        ChrBaseRegion TRAD_REGION_V38 = new ChrBaseRegion("chr14", 21622293, 22552156);
        List<ChrBaseRegion> TR_REGIONS_V38 = Lists.newArrayList(TRG_REGION_V38, TRB_REGION_V38, TRAD_REGION_V38);

        assertEquals(TR_REGIONS_V38,
                ImmuneRegions.getTrRegions(RefGenomeVersion.V38));
    }

    @Test
    public void testGetIgRegion_V37() {
        ChrBaseRegion IGK_REGION_V37 = new ChrBaseRegion("2", 89156674, 90538397);
        ChrBaseRegion IGH_REGION_V37 = new ChrBaseRegion("14", 106032614, 107288051);
        ChrBaseRegion IGL_REGION_V37 = new ChrBaseRegion("22", 22380474, 23265085);

        assertEquals(IGK_REGION_V37, ImmuneRegions.getIgRegion("IGK",
                RefGenomeVersion.V37));
        assertEquals(IGH_REGION_V37, ImmuneRegions.getIgRegion("IGH",
                RefGenomeVersion.V37));
        assertEquals(IGL_REGION_V37, ImmuneRegions.getIgRegion("IGL",
                RefGenomeVersion.V37));
        assertNull(ImmuneRegions.getIgRegion("test",
                RefGenomeVersion.V37));
    }

    @Test
    public void testGetIgRegion_V38() {
        ChrBaseRegion IGK_REGION_V38 = new ChrBaseRegion("chr2", 88857161, 90315836);
        ChrBaseRegion IGH_REGION_V38 = new ChrBaseRegion("chr14", 105586437, 106879844);
        ChrBaseRegion IGL_REGION_V38 = new ChrBaseRegion("chr22", 22026076, 22922913);

        assertEquals(IGK_REGION_V38, ImmuneRegions.getIgRegion("IGK",
                RefGenomeVersion.V38));
        assertEquals(IGH_REGION_V38, ImmuneRegions.getIgRegion("IGH",
                RefGenomeVersion.V38));
        assertEquals(IGL_REGION_V38, ImmuneRegions.getIgRegion("IGL",
                RefGenomeVersion.V38));
        assertNull(ImmuneRegions.getIgRegion("test",
                RefGenomeVersion.V38));
    }
}