package com.hartwig.hmftools.iclusion.api;

import static com.google.common.base.Strings.nullToEmpty;

import static org.junit.Assert.assertEquals;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.iclusion.data.IclusionTrial;

import org.junit.Test;

public class IclusionApiObjectMapperTest {

    @Test
    public void canMapApiObjectsToIclusionTrial() {
        IclusionObjectMutation mutation1 = new IclusionObjectMutation();
        mutation1.variantId = "var1";
        mutation1.geneId = "gen1";

        IclusionObjectMutation mutation2 = new IclusionObjectMutation();
        mutation2.variantId = "var2";
        mutation2.geneId = "gen1";

        IclusionObjectGene gene = new IclusionObjectGene();
        gene.id = "gen1";
        gene.geneName = "gene1";

        IclusionObjectVariant variant1 = new IclusionObjectVariant();
        variant1.id = "var1";
        variant1.variantName = "variant1";

        IclusionObjectVariant variant2 = new IclusionObjectVariant();
        variant2.id = "var2";
        variant2.variantName = "variant2";

        IclusionObjectIndication indication = new IclusionObjectIndication();
        indication.id = "ind1";
        indication.parentId = "par1";
        indication.doid = "123";
        indication.doid2 = "456";
        indication.indicationName = "indName";
        indication.indicationNameFull = "indNameFull";
        indication.nodeIds = Lists.newArrayList();

        IclusionObjectStudy study = new IclusionObjectStudy();
        study.id = "stu1";
        study.acronym = "acronym";
        study.title = "title";
        study.eudra = "eudra";
        study.nct = "nct";
        study.ipn = null;
        study.ccmo = "ccmo";
        study.mutations = Lists.newArrayList(mutation1, mutation2);
        study.indicationIds = Lists.newArrayList("ind1");

        List<IclusionTrial> trials = IclusionApiObjectMapper.fromApiObjects(Lists.newArrayList(study), Lists.newArrayList(indication),
                Lists.newArrayList(gene), Lists.newArrayList(variant1, variant2));

        assertEquals(1, trials.size());

        assertEquals(study.id, trials.get(0).id());
        assertEquals(study.acronym, trials.get(0).acronym());
        assertEquals(study.title, trials.get(0).title());
        assertEquals(study.eudra, trials.get(0).eudra());
        assertEquals(nullToEmpty(study.nct), trials.get(0).nct());
        assertEquals(nullToEmpty(study.ipn), trials.get(0).ipn());
        assertEquals(study.ccmo, trials.get(0).ccmo());

        assertEquals(indication.indicationNameFull, trials.get(0).tumorLocations().get(0).primaryTumorLocation());
        assertEquals(indication.doid, trials.get(0).tumorLocations().get(0).doids().get(0));
        assertEquals(indication.doid2, trials.get(0).tumorLocations().get(0).doids().get(1));

        assertEquals(gene.geneName, trials.get(0).mutations().get(0).gene());
        assertEquals(variant1.variantName, trials.get(0).mutations().get(0).name());
        assertEquals(gene.geneName, trials.get(0).mutations().get(1).gene());
        assertEquals(variant2.variantName, trials.get(0).mutations().get(1).name());
    }

}