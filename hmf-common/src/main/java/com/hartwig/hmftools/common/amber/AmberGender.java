package com.hartwig.hmftools.common.amber;

import static com.hartwig.hmftools.common.purple.Gender.FEMALE;
import static com.hartwig.hmftools.common.purple.Gender.MALE;

import java.util.Collection;
import java.util.Map;

import com.google.common.collect.Multimap;
import com.hartwig.hmftools.common.genome.chromosome.Chromosome;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.purple.Gender;
import com.hartwig.hmftools.common.region.BaseRegion;

public final class AmberGender
{
    private static final double MIN_BAF_PERC = 0.01;
    private static final BaseRegion PSEUDOAUTOSOMAL_REGION_V37 = new BaseRegion(2699520, 155260560);
    private static final BaseRegion PSEUDOAUTOSOMAL_REGION_V38 = new BaseRegion(2781479, 156030895);
    //Lifted from PSEUDOAUTOSOMAL_REGION_V38 using UCSC:
    private static final BaseRegion PSEUDOAUTOSOMAL_REGION_CHM13 =
            new BaseRegion(2394410, 154257572);


    public static Gender determineGender(final RefGenomeVersion version, final Multimap<Chromosome,AmberBAF> chromosomeBafs)
    {
        BaseRegion inclusionRegion;

        if (version ==  RefGenomeVersion.V37) {
            inclusionRegion = PSEUDOAUTOSOMAL_REGION_V37;
        } else if (version == RefGenomeVersion.V38) {
            inclusionRegion = PSEUDOAUTOSOMAL_REGION_V38;
        } else if (version == RefGenomeVersion.HS1) {
            inclusionRegion = PSEUDOAUTOSOMAL_REGION_CHM13;
        } else {
            inclusionRegion = null;
            throw new IllegalArgumentException();
        }

        int totalPoints = 0;
        long inclusionPoints = 0;

        for(Map.Entry<Chromosome, Collection<AmberBAF>> entry : chromosomeBafs.asMap().entrySet())
        {
            totalPoints += entry.getValue().size();

            if(entry.getKey() == HumanChromosome._X)
                inclusionPoints = entry.getValue().stream().filter(x -> inclusionRegion.containsPosition(x.position())).count();
        }

        double inclusionPerc = inclusionPoints / (double)totalPoints;
        return inclusionPerc > MIN_BAF_PERC ? FEMALE : MALE;
    }


}
