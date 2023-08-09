package com.hartwig.hmftools.amber;

import static com.hartwig.hmftools.amber.AmberConfig.AMB_LOGGER;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.ListMultimap;
import com.hartwig.hmftools.common.amber.AmberBAF;
import com.hartwig.hmftools.common.amber.AmberBAFFile;
import com.hartwig.hmftools.common.amber.BaseDepth;
import com.hartwig.hmftools.common.amber.AmberQC;
import com.hartwig.hmftools.common.amber.AmberQCFile;
import com.hartwig.hmftools.common.amber.ImmutableAmberQC;
import com.hartwig.hmftools.common.genome.chromosome.Chromosome;
import com.hartwig.hmftools.common.utils.version.VersionInfo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AmberPersistence
{
    private final AmberConfig mConfig;

    public AmberPersistence(final AmberConfig config)
    {
        mConfig = config;
    }

    void persistVersionInfo(@NotNull final VersionInfo versionInfo) throws IOException
    {
        versionInfo.write(mConfig.OutputDir);
    }

    void persistBAF(@NotNull final List<AmberBAF> result) throws IOException, InterruptedException
    {
        final String filename = AmberBAFFile.generateAmberFilenameForWriting(mConfig.OutputDir, mConfig.getSampleId());
        AmberBAFFile.write(filename, result);

        if(mConfig.TumorId != null)
        {
            AMB_LOGGER.info("Applying pcf segmentation");
            new BAFSegmentation(mConfig.OutputDir).applySegmentation(mConfig.TumorId, filename);
        }
    }

    void persistQC(@NotNull final List<TumorContamination> contaminationRecords,
            double consanguinityProportion, @Nullable Chromosome uniparentalDisomy) throws IOException
    {
        final double contamination = new TumorContaminationModel().contamination(contaminationRecords);

        AmberQC qcStats = ImmutableAmberQC.builder()
                .contamination(contamination)
                .consanguinityProportion(consanguinityProportion)
                .uniparentalDisomy(uniparentalDisomy != null ? uniparentalDisomy.toString() : null).build();

        final String qcFilename = AmberQCFile.generateFilename(mConfig.OutputDir, mConfig.getSampleId());
        AmberQCFile.write(qcFilename, qcStats);
    }

    void persistContamination(@NotNull final List<TumorContamination> contaminationList) throws IOException
    {
        Collections.sort(contaminationList);

        final String outputVcf = mConfig.OutputDir + File.separator + mConfig.TumorId + ".amber.contamination.vcf.gz";
        AMB_LOGGER.info("Writing {} contamination records to {}", contaminationList.size(), outputVcf);
        new AmberVCF(mConfig).writeContamination(outputVcf, contaminationList);

        final String filename = TumorContaminationFile.generateContaminationFilename(mConfig.OutputDir, mConfig.TumorId);
        TumorContaminationFile.write(filename, contaminationList);
    }

    void persistSnpCheck(@NotNull final ListMultimap<Chromosome, BaseDepth> baseDepths)
    {
        if (baseDepths.size() > 0)
        {
            final String outputVcf = mConfig.OutputDir + File.separator + mConfig.primaryReference() + ".amber.snp.vcf.gz";
            AMB_LOGGER.info("Writing {} germline snp records to {}", baseDepths.size(), outputVcf);
            AmberVCF.writeBaseDepths(outputVcf, baseDepths.values(), mConfig.primaryReference());
        }
    }

    void persistPrimaryRefUnfiltered(@NotNull final ListMultimap<Chromosome, BaseDepth> baseDepths)
    {
        if (baseDepths.size() > 0)
        {
            final String outputVcf = mConfig.OutputDir + File.separator + mConfig.primaryReference() + ".amber.unfiltered.vcf.gz";
            AMB_LOGGER.info("Writing {} germline unfiltered records to {}", baseDepths.size(), outputVcf);
            AmberVCF.writeBaseDepths(outputVcf, baseDepths.values(), mConfig.primaryReference());
        }
    }

    void persistHomozygousRegions(@NotNull final List<RegionOfHomozygosity> regionOfHomozygosities) throws IOException
    {
        final String filename = RegionOfHomozygosityFile.generateFilename(mConfig.OutputDir, mConfig.primaryReference());
        RegionOfHomozygosityFile.write(filename, regionOfHomozygosities);
    }
}