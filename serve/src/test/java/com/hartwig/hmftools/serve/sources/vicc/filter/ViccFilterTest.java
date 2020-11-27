package com.hartwig.hmftools.serve.sources.vicc.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.serve.sources.vicc.ViccTestFactory;
import com.hartwig.hmftools.vicc.datamodel.Feature;
import com.hartwig.hmftools.vicc.datamodel.ViccEntry;
import com.hartwig.hmftools.vicc.datamodel.ViccSource;

import org.junit.Test;

public class ViccFilterTest {

    @Test
    public void canFilterOncogenicEvents() {
        ViccEntry oncogenic =
                ViccTestFactory.testViccEntryWithOncogenic("Oncogenic", "gene", "event", "description", "chromosome", "pos", null);
        ViccEntry benign =
                ViccTestFactory.testViccEntryWithOncogenic("Inconclusive", "gene", "event", "description", "chromosome", "pos", null);

        ViccFilter filter = new ViccFilter();
        List<ViccEntry> filteredEntries = filter.run(Lists.newArrayList(oncogenic, benign));
        assertEquals(1, filteredEntries.size());
        assertTrue(filteredEntries.contains(oncogenic));

        filter.reportUnusedFilterEntries();
    }

    @Test
    public void canFilterIndividualFeatures() {
        ViccFilter filter = new ViccFilter();

        String keywordToFilter = FilterFactory.FEATURE_KEYWORDS_TO_FILTER.iterator().next();
        Feature featureWithExactKeyword = ViccTestFactory.testFeatureWithName(keywordToFilter, "description", "chromosome", "pos", null);
        Feature featureWithFilterKeyword =
                ViccTestFactory.testFeatureWithName(keywordToFilter + " filter me", "description", "chromosome", "pos", null);
        assertFalse(filter.include(ViccSource.CIVIC, featureWithExactKeyword));
        assertFalse(filter.include(ViccSource.CIVIC, featureWithFilterKeyword));

        String nameToFilter = FilterFactory.FEATURES_TO_FILTER.iterator().next();
        Feature featureWithExactName = ViccTestFactory.testFeatureWithName(nameToFilter, "description", "chromosome", "pos", null);
        Feature featureWithFilterName =
                ViccTestFactory.testFeatureWithName(nameToFilter + " filter me", "description", "chromosome", "pos", null);
        assertFalse(filter.include(ViccSource.CIVIC, featureWithExactName));
        assertTrue(filter.include(ViccSource.CIVIC, featureWithFilterName));

        FilterKey keyToFilter = FilterFactory.FEATURE_KEYS_TO_FILTER.iterator().next();
        Feature featureToFilter = ViccTestFactory.testFeatureWithGeneAndName(keyToFilter.gene(),
                keyToFilter.name(),
                "description",
                "chromosome",
                "pos",
                null);
        assertFalse(filter.include(keyToFilter.source(), featureToFilter));

        Feature featureWithoutFilterName = ViccTestFactory.testFeatureWithName("don't filter me", "description", "chromosome", "pos", null);
        assertTrue(filter.include(ViccSource.CIVIC, featureWithoutFilterName));

        filter.reportUnusedFilterEntries();
    }
}