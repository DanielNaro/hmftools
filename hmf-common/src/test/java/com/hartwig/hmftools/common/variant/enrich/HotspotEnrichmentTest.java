package com.hartwig.hmftools.common.variant.enrich;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.utils.collection.Multimaps;
import com.hartwig.hmftools.common.variant.Hotspot;
import com.hartwig.hmftools.common.variant.ImmutableSomaticVariantImpl;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.common.variant.SomaticVariantTestBuilderFactory;
import com.hartwig.hmftools.common.variant.VariantContextFromString;
import com.hartwig.hmftools.common.variant.hotspot.ImmutableVariantHotspotImpl;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;

public class HotspotEnrichmentTest {

    @Test
    public void testOverlap() {
        final String hotspotRef = "GATTACA";
        final String variantRef = "T";

        final VariantHotspot hotspot = ImmutableVariantHotspotImpl.builder().chromosome("11").position(100).ref(hotspotRef).alt("A").build();
        // Max should be 100 + 7 - 1 + 5
        // Min should be 100 - 1 + 1 - 5

        assertOverlap(Hotspot.NON_HOTSPOT, hotspot, 93, variantRef);
        assertOverlap(Hotspot.NON_HOTSPOT, hotspot, 94, variantRef);
        assertOverlap(Hotspot.NEAR_HOTSPOT, hotspot, 95, variantRef);
        assertOverlap(Hotspot.NEAR_HOTSPOT, hotspot, 96, variantRef);

        assertOverlap(Hotspot.NEAR_HOTSPOT, hotspot, 99, variantRef);
        assertOverlap(Hotspot.NEAR_HOTSPOT, hotspot, 100, variantRef);
        assertOverlap(Hotspot.NEAR_HOTSPOT, hotspot, 101, variantRef);

        assertOverlap(Hotspot.NEAR_HOTSPOT, hotspot, 110, variantRef);
        assertOverlap(Hotspot.NEAR_HOTSPOT, hotspot, 111, variantRef);
        assertOverlap(Hotspot.NON_HOTSPOT, hotspot, 112, variantRef);
        assertOverlap(Hotspot.NON_HOTSPOT, hotspot, 113, variantRef);
    }

    @Test
    public void testExactMatch() {
        final String hotspotRef = "GATTACA";

        final VariantHotspot hotspot = ImmutableVariantHotspotImpl.builder().chromosome("11").position(100).ref(hotspotRef).alt("A").build();
        assertOverlap(Hotspot.HOTSPOT, hotspot, 100, hotspotRef);
    }

    @Test
    public void testFromVariant() {
        VariantContext variant = createNonHotspotHG37(1, "G");
        assertEquals(Hotspot.NON_HOTSPOT, HotspotEnrichment.fromVariant(variant));

        VariantContext tierPanel = new VariantContextBuilder(variant).attribute("TIER", "PANEL").make();
        assertEquals(Hotspot.NON_HOTSPOT, HotspotEnrichment.fromVariant(tierPanel));

        VariantContext tierHotspot = new VariantContextBuilder(variant).attribute("TIER", "HOTSPOT").make();
        assertEquals(Hotspot.HOTSPOT, HotspotEnrichment.fromVariant(tierHotspot));
    }

    private void assertOverlap(Hotspot expected, @NotNull VariantHotspot hotspot, int variantStart, @NotNull final String variantRef) {
        final HotspotEnrichment victim = new HotspotEnrichment(Multimaps.fromPositions(Lists.newArrayList(hotspot)));
        ImmutableSomaticVariantImpl.Builder builder = SomaticVariantTestBuilderFactory.create();

        final SomaticVariant hg37Variant = victim.enrich(builder, createNonHotspotHG37(variantStart, variantRef)).build();
        final SomaticVariant hg38Variant = victim.enrich(builder, createNonHotspotHG38(variantStart, variantRef)).build();

        assertEquals(expected, hg37Variant.hotspot());
        assertEquals(expected, hg38Variant.hotspot());
    }

    @NotNull
    private static VariantContext createNonHotspotHG37(int start, @NotNull final String ref) {
        final String line = "11\t" + start + "\tCOSM123;COSM456\t" + ref
                + "\tA\t.\tPASS\tCOSM2ENST=COSM123|GENE_TRANS1|c.1A>G|p.E1E|1,COSM456|GENE_TRANS2|c.2A>G|p.E2E|1\tGT:AD:DP\t0/1:73,17:91";
        return VariantContextFromString.decode(line);
    }

    @NotNull
    private static VariantContext createNonHotspotHG38(int start, @NotNull final String ref) {
        final String line = "chr11\t" + start + "\tCOSM123;COSM456\t" + ref
                + "\tA\t.\tPASS\tCOSM2ENST=COSM123|GENE_TRANS1|c.1A>G|p.E1E|1,COSM456|GENE_TRANS2|c.2A>G|p.E2E|1\tGT:AD:DP\t0/1:73,17:91";
        return VariantContextFromString.decode(line);
    }
}
