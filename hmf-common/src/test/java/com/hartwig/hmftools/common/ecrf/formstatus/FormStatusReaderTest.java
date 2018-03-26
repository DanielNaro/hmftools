package com.hartwig.hmftools.common.ecrf.formstatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import com.google.common.io.Resources;

import org.junit.Test;

public class FormStatusReaderTest {

    private static final String BASE_RESOURCE_DIR = Resources.getResource("ecrf").getPath();
    private static final String TEST_FILE = BASE_RESOURCE_DIR + File.separator + "formstatus" + File.separator + "formstatus.csv";

    @Test
    public void canLoadFromCsv() throws IOException {
        final FormStatusModel formStatusModel = FormStatusReader.buildModelFromCsv(TEST_FILE);

        final Map<FormStatusKey, FormStatusData> formStatuses = formStatusModel.formStatuses();
        assertEquals(3, formStatuses.size());
        final FormStatusKey key1 = new ImmutableFormStatusKey("CPCT02000001", "Anti Coagulants", "0", "Anti Coagulants", "0");
        final FormStatusKey key2 =
                new ImmutableFormStatusKey("CPCT02000002", "Death Page", "0", "Neoadjuvant treatment, recurrence and survival", "0");
        final FormStatusKey key3 = new ImmutableFormStatusKey("CPCT02000004", "Eligibility Screening", "0", "BASELINE", "0");

        assertTrue(formStatuses.containsKey(key1));
        assertTrue(formStatuses.containsKey(key2));
        assertTrue(formStatuses.containsKey(key3));

        assertTrue(formStatuses.get(key1).locked());
        assertTrue(formStatuses.get(key2).locked());
        assertFalse(formStatuses.get(key3).locked());

        assertEquals(FormStatusState.SUBMITTED, formStatuses.get(key1).state());
        assertEquals(FormStatusState.SUBMITTED_WITH_MISSING, formStatuses.get(key2).state());
        assertEquals(FormStatusState.VERIFIED, formStatuses.get(key3).state());
    }
}
