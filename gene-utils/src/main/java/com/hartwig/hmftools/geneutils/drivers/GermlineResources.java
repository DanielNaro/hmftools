package com.hartwig.hmftools.geneutils.drivers;

import static htsjdk.tribble.AbstractFeatureReader.getFeatureReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;

import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import org.jetbrains.annotations.NotNull;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFCodec;

public final class GermlineResources
{
    static List<VariantContext> whitelist(RefGenomeVersion refGenomeVersion) throws IOException
    {

        switch (refGenomeVersion){
            case V37:
                return resource(resourceURL("/drivers/GermlineHotspots.whitelist.37.vcf"));
            case V38:
                return resource(resourceURL("/drivers/GermlineHotspots.whitelist.38.vcf"));
            case HS1:
                return resource(resourceURL("/drivers/GermlineHotspots.whitelist.chm13.vcf"));
            default:
                throw new IllegalArgumentException();
        }
    }

    static List<VariantContext> blacklist(RefGenomeVersion refGenomeVersion) throws IOException
    {
        switch (refGenomeVersion) {
            case V37:
                return resource(resourceURL("/drivers/GermlineHotspots.blacklist.37.vcf"));
            case V38:
                return resource(resourceURL("/drivers/GermlineHotspots.blacklist.38.vcf"));
            case HS1:
                return resource(resourceURL("/drivers/GermlineHotspots.blacklist.chm13.vcf"));
            default:
                throw new IllegalArgumentException();
        }
    }


    static List<VariantContext> resource(final String file) throws IOException
    {
        return getFeatureReader(file, new VCFCodec(), false).iterator().toList();
    }

    static String resourceURL(final String location)
    {
        return GenerateDriverGeneFiles.class.getResource(location).toString();
    }
}
