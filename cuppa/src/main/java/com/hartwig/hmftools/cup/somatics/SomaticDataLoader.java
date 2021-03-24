package com.hartwig.hmftools.cup.somatics;

import static com.hartwig.hmftools.common.sigs.SnvSigUtils.populateBucketMap;
import static com.hartwig.hmftools.common.utils.MatrixUtils.loadMatrixDataFile;
import static com.hartwig.hmftools.common.sigs.SnvSigUtils.contextFromVariant;
import static com.hartwig.hmftools.cup.CuppaConfig.CUP_LOGGER;
import static com.hartwig.hmftools.cup.CuppaConfig.DATA_DELIM;
import static com.hartwig.hmftools.cup.common.CupConstants.POS_FREQ_BUCKET_SIZE;
import static com.hartwig.hmftools.cup.common.CupConstants.POS_FREQ_MAX_SAMPLE_COUNT;
import static com.hartwig.hmftools.cup.somatics.RefSomatics.convertSignatureName;
import static com.hartwig.hmftools.cup.somatics.RefSomatics.populateRefPercentileData;

import static htsjdk.tribble.AbstractFeatureReader.getFeatureReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.sigs.PositionFrequencies;
import com.hartwig.hmftools.common.utils.Matrix;
import com.hartwig.hmftools.common.sigs.SignatureAllocation;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.common.variant.SomaticVariantFactory;
import com.hartwig.hmftools.common.variant.VariantType;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.readers.LineIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.filter.CompoundFilter;
import htsjdk.variant.variantcontext.filter.PassingVariantFilter;
import htsjdk.variant.vcf.VCFCodec;

public class SomaticDataLoader
{
    public static Matrix loadSampleCountsFromFile(final String filename, final Map<String,Integer> sampleCountsIndex)
    {
        if(filename.isEmpty())
            return null;

        Matrix sampleCounts = loadMatrixDataFile(filename, sampleCountsIndex, Lists.newArrayList("BucketName"));
        sampleCounts.cacheTranspose();

        return sampleCounts;
    }

    public static Matrix loadSamplePosFreqFromFile(final String filename, final Map<String,Integer> sampleCountsIndex)
    {
        if(filename.isEmpty())
            return null;

        Matrix sampleCounts = loadMatrixDataFile(filename, sampleCountsIndex, null);
        sampleCounts.cacheTranspose();

        return sampleCounts;
    }

    public static boolean loadSigContribsFromDatabase(
            final DatabaseAccess dbAccess, final List<String> sampleIds, final Map<String,Map<String,Double>> sampleSigContributions)
    {
        if(dbAccess == null)
            return false;

        for(final String sampleId : sampleIds)
        {
            Map<String,Double> sigContribs = sampleSigContributions.get(sampleId);

            if(sigContribs == null)
            {
                sigContribs = Maps.newHashMap();
                sampleSigContributions.put(sampleId, sigContribs);
            }

            final List<SignatureAllocation> sigAllocations = dbAccess.readSignatureAllocations(sampleId);

            for(final SignatureAllocation sigAllocation : sigAllocations)
            {
                final String sigName = convertSignatureName(sigAllocation.signature());
                sigContribs.put(sigName, sigAllocation.allocation());
            }
        }

        return true;
    }

    public static boolean loadRefSignaturePercentileData(
            final String filename, final Map<String,Map<String,double[]>> refCancerSigContribs, final Map<String,double[]> refCancerSnvCounts)
    {
        if(filename.isEmpty())
            return true;

        return populateRefPercentileData(filename, refCancerSigContribs, refCancerSnvCounts);
    }

    public static Matrix loadRefSampleCounts(final String filename, final List<String> refSampleNames, final List<String> ignoreCols)
    {
        if(filename.isEmpty())
            return null;

        Matrix refSampleCounts = loadMatrixDataFile(filename, refSampleNames, ignoreCols);
        refSampleCounts.cacheTranspose();
        return refSampleCounts;
    }

    public static List<SomaticVariant> loadSomaticVariants(final String sampleId, final DatabaseAccess dbAccess)
    {
        final List<SomaticVariant> somaticVariants = dbAccess.readSomaticVariants(sampleId, VariantType.SNP);
        somaticVariants.addAll(dbAccess.readSomaticVariants(sampleId, VariantType.INDEL));
        return somaticVariants;
    }

    public static List<SomaticVariant> loadSomaticVariants(final String sampleId, final String vcfFile, final List<VariantContext.Type> types)
    {
        CompoundFilter filter = new CompoundFilter(true);
        filter.add(new PassingVariantFilter());

        SomaticVariantFactory variantFactory = new SomaticVariantFactory(filter);
        final List<SomaticVariant> variantList = Lists.newArrayList();

        try
        {
            final AbstractFeatureReader<VariantContext, LineIterator> reader = getFeatureReader(vcfFile, new VCFCodec(), false);

            for (VariantContext variant : reader.iterator())
            {
                if (filter.test(variant))
                {
                    final SomaticVariant somaticVariant = variantFactory.createVariant(sampleId, variant).orElse(null);

                    if(somaticVariant == null)
                        continue;

                    if(!types.isEmpty() && !types.contains(variant.getType()))
                        continue;

                    variantList.add(somaticVariant);
                }
            }
        }
        catch(IOException e)
        {
            CUP_LOGGER.error(" failed to read somatic VCF file({}): {}", vcfFile, e.toString());
        }

        return variantList;
    }

    public static double[] extractTrinucleotideCounts(final List<SomaticVariant> variants, final Map<String,Integer> bucketNameMap)
    {
        final double[] counts = new double[bucketNameMap.size()];

        for(final SomaticVariant variant : variants)
        {
            if(variant.isFiltered() || !variant.isSnp())
                continue;

            if(variant.alt().length() != 1)
                continue;

            final String rawContext = variant.trinucleotideContext();

            if(rawContext.contains("N"))
                continue;

            final String bucketName = contextFromVariant(variant);
            Integer bucketIndex = bucketNameMap.get(bucketName);

            if(bucketIndex == null)
            {
                CUP_LOGGER.error("invalid bucketName({}) from var({}>{}) context={})",
                        bucketName, variant.ref(), variant.alt(), variant.trinucleotideContext());
                continue;
            }

            ++counts[bucketIndex];
        }

        return counts;
    }

    public static void extractPositionFrequencyCounts(final List<SomaticVariant> variants, final PositionFrequencies positionFrequencies)
    {
        positionFrequencies.clear();

        for(final SomaticVariant variant : variants)
        {
            if(variant.isFiltered() || !variant.isSnp())
                continue;

            if(variant.alt().length() != 1)
                continue;

            final String rawContext = variant.trinucleotideContext();

            if(rawContext.contains("N"))
                continue;

            positionFrequencies.addPosition(variant.chromosome(), (int)variant.position());
        }
    }

    public static boolean loadSigContribsFromCohortFile(final String filename, final Map<String,Map<String,Double>> sampleSigContributions)
    {
        try
        {
            final List<String> fileData = Files.readAllLines(new File(filename).toPath());

            final String header = fileData.get(0);
            fileData.remove(0);

            for(final String line : fileData)
            {
                // SampleId,SigName,SigContrib,SigPercent
                final String[] items = line.split(DATA_DELIM, -1);
                String sampleId = items[0];
                String sigName = convertSignatureName(items[1]);
                double sigContrib = Double.parseDouble(items[2]);

                Map<String,Double> sigContribs = sampleSigContributions.get(sampleId);

                if(sigContribs == null)
                {
                    sigContribs = Maps.newHashMap();
                    sampleSigContributions.put(sampleId, sigContribs);
                }

                sigContribs.put(sigName, sigContrib);
            }
        }
        catch (IOException e)
        {
            CUP_LOGGER.error("failed to read sig contribution data file({}): {}", filename, e.toString());
            return false;
        }

        return true;
    }

    public static Matrix convertSomaticVariantsToSnvCounts(
            final String sampleId, final List<SomaticVariant> variants, final Map<String,Integer> sampleSnvCountsIndex)
    {
        final Map<String,Integer> triNucBucketNameMap = Maps.newHashMap();
        populateBucketMap(triNucBucketNameMap);

        final double[] triNucCounts = extractTrinucleotideCounts(variants, triNucBucketNameMap);

        final Matrix sampleSnvCounts = new Matrix(triNucCounts.length, 1);

        sampleSnvCounts.setCol(0, triNucCounts);
        sampleSnvCountsIndex.put(sampleId, 0);

        return sampleSnvCounts;
    }

    public static Matrix convertSomaticVariantsToPosFrequencies(
            final String sampleId, final List<SomaticVariant> variants, final Map<String,Integer> samplePosFreqIndex)
    {
        PositionFrequencies posFrequency = new PositionFrequencies(POS_FREQ_BUCKET_SIZE, POS_FREQ_MAX_SAMPLE_COUNT);

        extractPositionFrequencyCounts(variants, posFrequency);

        final Matrix matrix = new Matrix(posFrequency.getCounts().length, 1);

        matrix.setCol(0, posFrequency.getCounts());
        samplePosFreqIndex.put(sampleId, 0);

        return matrix;


    }

}
