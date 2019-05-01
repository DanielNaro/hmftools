package com.hartwig.hmftools.common.hospital;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Map;

import com.google.common.collect.Maps;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class HospitalModelTest {

    @Test
    public void canDetermineRequesterForCPCTAndDrup() {
        final HospitalModel hospitalModel = buildTestHospitalModel();
        final HospitalData hospital = hospitalModel.hospitalPerId("01");
        assertNotNull(hospital);
        assertEquals("CpctPI", HospitalModel.determineRequester("CPCT02010001", hospital, "henk"));
        assertEquals("DrupPI", HospitalModel.determineRequester("DRUP01010001", hospital, "henk"));
        assertEquals("WidePI", HospitalModel.determineRequester("WIDE01010001", hospital, "henk"));

        // Revert to CPCT PI with '*' for DRUP PI & recipients
        final HospitalData hospital2 = hospitalModel.hospitalPerId("02");
        assertNotNull(hospital2);
        assertEquals("CpctPI2", HospitalModel.determineRequester("CPCT02010001", hospital2, "henk"));
        assertEquals("CpctPI2", HospitalModel.determineRequester("DRUP01010001", hospital2, "henk"));

        assertNull(hospitalModel.hospitalPerId("03"));
    }

    @Test
    public void canReadAddress() {
        final HospitalModel hospitalModel = buildTestHospitalModel();
        final HospitalData hospital = hospitalModel.hospitalPerId("01");
        assertNotNull(hospital);
        assertEquals("Address", hospital.addressName());
        assertEquals("Zip", hospital.addressZip());
        assertEquals("City", hospital.addressCity());
    }

    @Test
    public void canLookupAddresseeForSample() {
        final HospitalModel hospitalModel = buildTestHospitalModel();

        assertEquals("CpctPI, Address, Zip City", hospitalModel.addresseeStringForSample("CPCT02010001T", "henk"));
    }

    @Test
    public void canLookAddressForCORESample() {
        final HospitalModel hospitalModel = buildTestHospitalModel();
        assertEquals("Address, Zip City", hospitalModel.addresseeStringForSample("CORE18001224T", "henk"));
    }

    @NotNull
    private static HospitalModel buildTestHospitalModel() {
        Map<String, HospitalData> hospitalPerId = Maps.newHashMap();
        Map<String, HospitalSampleMapping> hospitalPerIdManual = Maps.newHashMap();

        hospitalPerId.put("01",
                ImmutableHospitalData.of("HOSP1", "CPCT Recip", "DRUP Recip", "WIDE Recip", "Address", "Zip", "City", "CpctPI", "DrupPI", "WidePI"));
        hospitalPerId.put("02", ImmutableHospitalData.of("HOSP2", "CPCT Recip2", "*", "", "Address2", "Zip2", "City2", "CpctPI2", "*", "WidePI2"));
        hospitalPerIdManual.put("CORE18001224T", ImmutableHospitalSampleMapping.of("HOSP1"));

        return ImmutableHospitalModel.of(hospitalPerId, hospitalPerIdManual);
    }
}
