package com.hartwig.hmftools.patientdb.curators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import com.hartwig.hmftools.patientdb.data.CuratedTumorLocation;

import org.junit.Test;

public class TumorLocationCuratorTest {

    @Test
    public void canCreateFromProductionResource() throws IOException {
        assertNotNull(TumorLocationCurator.fromProductionResource());
    }

    @Test
    public void canDetermineUnusedTerms() {
        TumorLocationCurator curator = TestCuratorFactory.tumorLocationCurator();
        assertEquals(7, curator.unusedSearchTerms().size());

        curator.search("Breast cancer");
        assertEquals(6, curator.unusedSearchTerms().size());
    }

    @Test
    public void canCurateDesmoidTumor() {
        // KODU: See DEV-275
        TumorLocationCurator curator = TestCuratorFactory.tumorLocationCurator();
        String desmoidTumor = "desmoïd tumor";
        CuratedTumorLocation tumorLocation = curator.search(desmoidTumor);

        String location = tumorLocation.primaryTumorLocation();
        assertNotNull(location);
        assertEquals("sarcoma", location.toLowerCase());
    }
}