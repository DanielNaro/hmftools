package com.hartwig.hmftools.geneutils.common;

import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

import static com.hartwig.hmftools.geneutils.common.FileLiftover.inferDestVersion;

public class FileLiftoverTest {

    @Test
    public void testInferDestVersion() {
        Assert.assertEquals(RefGenomeVersion.V38,
                inferDestVersion(RefGenomeVersion.V37));
    }
}