package com.hartwig.hmftools.common.genome.refgenome;

import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeFunctions.enforceChrPrefix;

public class RefChrNameCorrectorEnforceChrPrefix implements RefChrNameCorrectorInterface {
    @Override
    public String correct(String chrName) {
        return enforceChrPrefix(chrName);
    }
}