package com.hartwig.hmftools.common.ensemblcache;

import static com.hartwig.hmftools.common.ensemblcache.EnsemblDataLoader.loadEnsemblGeneData;
import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import com.hartwig.hmftools.common.gene.GeneData;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.utils.file.DelimFileReader;
import com.hartwig.hmftools.common.utils.file.DelimFileWriter;

import org.junit.Assert;
import org.junit.Test;

public class EnsemblDataLoaderTest
{
    @Test
    public void testLoadEnsemblGeneData()
    {
        String dataPath = "src/test/resources/ensemblcache/";
        List<String> restrictedGeneIds = new ArrayList<>();
        Map<String,List<GeneData>> chrGeneDataMap = new HashMap<>();
        loadEnsemblGeneData(
                dataPath, restrictedGeneIds, chrGeneDataMap,
                RefGenomeVersion.HS1);
        Assert.assertEquals(24, chrGeneDataMap.size());
    }
}
