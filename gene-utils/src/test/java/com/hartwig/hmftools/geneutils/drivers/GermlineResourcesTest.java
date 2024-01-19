package com.hartwig.hmftools.geneutils.drivers;

import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import junit.framework.TestCase;
import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import static com.hartwig.hmftools.geneutils.drivers.GermlineResources.resource;
import static com.hartwig.hmftools.geneutils.drivers.GermlineResources.resourceURL;

public class GermlineResourcesTest extends TestCase {

    public void testWhitelist_V37() {
        try {

            ByteArrayOutputStream expectedByteArray =
                    new ByteArrayOutputStream();
            ObjectOutputStream expectedObjectOutputStream =
                    new ObjectOutputStream(expectedByteArray);
            for(var it: resource(resourceURL("/drivers/GermlineHotspots.whitelist.37" +
                    ".vcf"))) {
                expectedObjectOutputStream.writeObject(it);
            };

            ByteArrayOutputStream actualByteArray =
                    new ByteArrayOutputStream();
            ObjectOutputStream actualObjectOutputStream =
                    new ObjectOutputStream(actualByteArray);
            for(var it: GermlineResources.whitelist(RefGenomeVersion.V37)) {
                actualObjectOutputStream.writeObject(it);
            };

            Assert.assertArrayEquals(
                expectedByteArray.toByteArray(),
                actualByteArray.toByteArray()
            );
        } catch (IOException e) {
            Assert.fail();
        }
    }

    public void testWhitelist_V38() {
        try {

            ByteArrayOutputStream expectedByteArray =
                    new ByteArrayOutputStream();
            ObjectOutputStream expectedObjectOutputStream =
                    new ObjectOutputStream(expectedByteArray);
            for(var it: resource(resourceURL("/drivers/GermlineHotspots" +
                    ".whitelist.38.vcf"))) {
                expectedObjectOutputStream.writeObject(it);
            };

            ByteArrayOutputStream actualByteArray =
                    new ByteArrayOutputStream();
            ObjectOutputStream actualObjectOutputStream =
                    new ObjectOutputStream(actualByteArray);
            for(var it: GermlineResources.whitelist(RefGenomeVersion.V38)) {
                actualObjectOutputStream.writeObject(it);
            };

            Assert.assertArrayEquals(
                    expectedByteArray.toByteArray(),
                    actualByteArray.toByteArray()
            );
        } catch (IOException e) {
            Assert.fail();
        }
    }

    public void testBlacklist_V37() {
        try {

            ByteArrayOutputStream expectedByteArray =
                    new ByteArrayOutputStream();
            ObjectOutputStream expectedObjectOutputStream =
                    new ObjectOutputStream(expectedByteArray);
            for(var it: resource(resourceURL("/drivers/GermlineHotspots.blacklist.37" +
                    ".vcf"))) {
                expectedObjectOutputStream.writeObject(it);
            };

            ByteArrayOutputStream actualByteArray =
                    new ByteArrayOutputStream();
            ObjectOutputStream actualObjectOutputStream =
                    new ObjectOutputStream(actualByteArray);
            for(var it: GermlineResources.blacklist(RefGenomeVersion.V37)) {
                actualObjectOutputStream.writeObject(it);
            };

            Assert.assertArrayEquals(
                    expectedByteArray.toByteArray(),
                    actualByteArray.toByteArray()
            );
        } catch (IOException e) {
            Assert.fail();
        }
    }

    public void testBlacklist_V38() {
        try {

            ByteArrayOutputStream expectedByteArray =
                    new ByteArrayOutputStream();
            ObjectOutputStream expectedObjectOutputStream =
                    new ObjectOutputStream(expectedByteArray);
            for(var it: resource(resourceURL("/drivers/GermlineHotspots" +
                    ".blacklist.38.vcf"))) {
                expectedObjectOutputStream.writeObject(it);
            };

            ByteArrayOutputStream actualByteArray =
                    new ByteArrayOutputStream();
            ObjectOutputStream actualObjectOutputStream =
                    new ObjectOutputStream(actualByteArray);
            for(var it: GermlineResources.blacklist(RefGenomeVersion.V38)) {
                actualObjectOutputStream.writeObject(it);
            };

            Assert.assertArrayEquals(
                    expectedByteArray.toByteArray(),
                    actualByteArray.toByteArray()
            );
        } catch (IOException e) {
            Assert.fail();
        }
    }
}