package com.hartwig.hmftools.patientreporter.qcfail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class NotAnalysableStudyTest {

    @Test
    public void canDeriveStudyFromSampleName() {
        assertEquals(NotAnalysableStudy.CPCT, NotAnalysableStudy.fromSample("CPCT02010500T"));
        assertEquals(NotAnalysableStudy.DRUP, NotAnalysableStudy.fromSample("DRUP01010400TII"));
    }
}