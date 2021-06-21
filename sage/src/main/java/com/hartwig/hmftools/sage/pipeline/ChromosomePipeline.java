package com.hartwig.hmftools.sage.pipeline;

import static com.hartwig.hmftools.sage.SageCommon.SG_LOGGER;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.chromosome.MitochondrialChromosome;
import com.hartwig.hmftools.common.genome.region.GenomeRegion;
import com.hartwig.hmftools.common.utils.sv.BaseRegion;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.sage.config.SageConfig;
import com.hartwig.hmftools.sage.coverage.Coverage;
import com.hartwig.hmftools.sage.phase.Phase;
import com.hartwig.hmftools.sage.quality.QualityRecalibrationMap;
import com.hartwig.hmftools.sage.read.ReadContextCounter;
import com.hartwig.hmftools.sage.variant.SageVariant;
import com.hartwig.hmftools.sage.variant.SageVariantContextFactory;
import com.hartwig.hmftools.sage.variant.SageVariantTier;

import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.variant.variantcontext.VariantContext;

public class ChromosomePipeline implements AutoCloseable
{
    private final String mChromosome;
    private final SageConfig mConfig;
    private final List<RegionFuture<List<SageVariant>>> mRegions = Lists.newArrayList();
    private final IndexedFastaSequenceFile mRefGenome;
    private final SageVariantPipeline mSageVariantPipeline;
    private final Consumer<VariantContext> mConsumer;
    private final ChromosomePartition mPartition;
    private final Phase mPhase;

    private static final EnumSet<SageVariantTier> PANEL_ONLY_TIERS = EnumSet.of(SageVariantTier.HOTSPOT, SageVariantTier.PANEL);

    public ChromosomePipeline(
            final String chromosome, final SageConfig config, final Executor executor,
            final List<VariantHotspot> hotspots, final List<BaseRegion> panelRegions,
            final List<BaseRegion> highConfidenceRegions, final Map<String, QualityRecalibrationMap> qualityRecalibrationMap,
            final Coverage coverage, final Consumer<VariantContext> consumer) throws IOException
    {
        mChromosome = chromosome;
        mConfig = config;
        mRefGenome = new IndexedFastaSequenceFile(new File(config.RefGenomeFile));
        mConsumer = consumer;
        mSageVariantPipeline = new SomaticPipeline(config,
                executor,
                mRefGenome,
                hotspots,
                panelRegions,
                highConfidenceRegions,
                qualityRecalibrationMap,
                coverage);
        mPartition = new ChromosomePartition(config, mRefGenome);
        mPhase = new Phase(config, chromosome, this::write);
    }

    @NotNull
    public String chromosome()
    {
        return mChromosome;
    }

    public void process() throws ExecutionException, InterruptedException
    {
        for(BaseRegion region : mPartition.partition(mChromosome))
        {
            final CompletableFuture<List<SageVariant>> future = mSageVariantPipeline.variants(region);
            final RegionFuture<List<SageVariant>> regionFuture = new RegionFuture<>(region, future);
            mRegions.add(regionFuture);
        }

        submit().get();
    }

    public void process(int minPosition, int maxPosition) throws ExecutionException, InterruptedException
    {
        for(BaseRegion region : mPartition.partition(mChromosome, minPosition, maxPosition))
        {
            final CompletableFuture<List<SageVariant>> future = mSageVariantPipeline.variants(region);
            final RegionFuture<List<SageVariant>> regionFuture = new RegionFuture<>(region, future);
            mRegions.add(regionFuture);
        }

        submit().get();
    }

    @NotNull
    private CompletableFuture<ChromosomePipeline> submit()
    {
        // Even if regions were executed out of order, they must be phased in order
        mRegions.sort(Comparator.comparing(RegionFuture::region));

        // Phasing must be done in order but we can do it eagerly as each new region comes in.
        // It is not necessary to wait for the entire chromosome to be finished to start.
        CompletableFuture<Void> done = CompletableFuture.completedFuture(null);
        final Iterator<RegionFuture<List<SageVariant>>> regionsIterator = mRegions.iterator();
        while(regionsIterator.hasNext())
        {
            CompletableFuture<List<SageVariant>> region = regionsIterator.next().future();
            done = done.thenCombine(region, (aVoid, sageVariants) ->
            {

                sageVariants.forEach(mPhase);
                return null;
            });

            regionsIterator.remove();
        }

        return done.thenApply(aVoid ->
        {
            mPhase.flush();
            SG_LOGGER.info("Processing chromosome {} complete", mChromosome);
            return ChromosomePipeline.this;
        });
    }

    private void write(final SageVariant entry)
    {
        if(include(entry, mPhase.passingPhaseSets()))
        {
            mConsumer.accept(SageVariantContextFactory.create(entry));
        }
    }

    private boolean include(final SageVariant entry, final Set<Integer> passingPhaseSets)
    {
        if(mConfig.PanelOnly && !PANEL_ONLY_TIERS.contains(entry.tier()))
        {
            return false;
        }

        if(entry.isPassing())
        {
            return true;
        }

        if(mConfig.Filter.HardFilter)
        {
            return false;
        }

        if(entry.tier() == SageVariantTier.HOTSPOT)
        {
            return true;
        }

        // Its not always 100% transparent whats happening with the mixed germline dedup logic unless we keep all the associated records.
        if(entry.mixedGermlineImpact() > 0)
        {
            return true;
        }

        if(!entry.isNormalEmpty() && !entry.isTumorEmpty() && !MitochondrialChromosome.contains(entry.chromosome())
                && !passingPhaseSets.contains(entry.localPhaseSet()))
        {
            final ReadContextCounter normal = entry.normalAltContexts().get(0);
            if(normal.altSupport() > mConfig.Filter.FilteredMaxNormalAltSupport)
            {
                return false;
            }
        }

        return true;
    }

    @Override
    public void close() throws IOException
    {
        mRefGenome.close();
    }

    private static class RegionFuture<T>
    {
        private final CompletableFuture<T> mFuture;
        private final BaseRegion mRegion;

        public RegionFuture(final BaseRegion region, final CompletableFuture<T> future)
        {
            mRegion = region;
            mFuture = future;
        }

        public CompletableFuture<T> future()
        {
            return mFuture;
        }

        public BaseRegion region()
        {
            return mRegion;
        }
    }
}
