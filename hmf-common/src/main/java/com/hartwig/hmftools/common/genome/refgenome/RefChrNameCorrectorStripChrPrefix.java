package com.hartwig.hmftools.common.genome.refgenome;

import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeFunctions.stripChrPrefix;

public class RefChrNameCorrectorStripChrPrefix implements RefChrNameCorrectorInterface {
    @Override
    public String correct(String chrName) {
        return stripChrPrefix(chrName);
    }
}
