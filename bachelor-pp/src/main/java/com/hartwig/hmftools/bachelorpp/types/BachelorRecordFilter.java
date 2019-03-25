package com.hartwig.hmftools.bachelorpp.types;

import static com.hartwig.hmftools.common.variant.VariantConsequence.STOP_GAINED;

public class BachelorRecordFilter
{
    public final String Chromosome;
    public final long Position;
    public final String Id;
    public final String Ref;
    public final String Alt;
    public final String Effects;

    public final String Significance;
    public final String Diagnosis;

    public BachelorRecordFilter(final String chromosome, final long position, final String id, final String ref, final String alt,
            final String diagnosis, final String sig, final String effects)
    {
        Chromosome = chromosome;
        Position = position;
        Ref = ref;
        Alt = alt;
        Id = id;
        Diagnosis = diagnosis;
        Significance = sig;
        Effects = effects;
    }

    public boolean matches(final BachelorGermlineVariant var)
    {
        if(!var.Chromosome.equals(Chromosome) || var.Position != Position
        || !var.Ref.equals(Ref) || !var.Alts.equals(Alt))
        {
            return false;
        }

        for(final String effect : var.effectsList())
        {
            if (STOP_GAINED.isParentTypeOf(var.Effects) && Effects.contains("nonsense"))
                return true;

            if (Effects.contains(effect))
                return true;
        }

        return false;
    }

}
