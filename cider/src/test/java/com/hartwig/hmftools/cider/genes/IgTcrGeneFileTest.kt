package com.hartwig.hmftools.cider.genes

import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion
import junit.framework.TestCase

class IgTcrGeneFileTest : TestCase() {

    fun testGetResourcePath_V37() {
        assertEquals("igtcr_gene.37.tsv",IgTcrGeneFile.getResourcePath(RefGenomeVersion.V37))
    }

    fun testGetResourcePath_V38() {
        assertEquals("igtcr_gene.38.tsv",IgTcrGeneFile.getResourcePath
        (RefGenomeVersion.V38))
    }
}