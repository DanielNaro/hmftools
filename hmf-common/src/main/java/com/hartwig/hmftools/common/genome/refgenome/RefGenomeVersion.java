package com.hartwig.hmftools.common.genome.refgenome;

import com.hartwig.hmftools.common.immune.ImmuneRegionInterface;
import com.hartwig.hmftools.common.immune.ImmuneRegion_V37;
import com.hartwig.hmftools.common.immune.ImmuneRegion_V38;
import com.hartwig.hmftools.common.region.ExcludedRegionsInterface;
import com.hartwig.hmftools.common.region.ExcludedRegionsInterfaceImpl37;
import com.hartwig.hmftools.common.region.ExcludedRegionsInterfaceImpl38;
import com.hartwig.hmftools.common.utils.config.ConfigBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public enum RefGenomeVersion
{
    V37(
            "37",
            new RefChrNameCorrectorStripChrPrefix(),
            new ExcludedRegionsInterfaceImpl37(),
            new ImmuneRegion_V37(),
            RefGenomeCoordinates.COORDS_37
    ),
    V38(
            "38",
            new RefChrNameCorrectorEnforceChrPrefix(),
            new ExcludedRegionsInterfaceImpl38(),
            new ImmuneRegion_V38(),
            RefGenomeCoordinates.COORDS_38
    );

    @NotNull
    private final String mIdentifier;
    private final RefChrNameCorrectorInterface chrNameCorrector;
    private final ImmuneRegionInterface immuneRegions;
    private final RefGenomeCoordinates refGenomeCoordinates;

    public ExcludedRegionsInterface getExcludedRegionsInterface() {
        return excludedRegionsInterface;
    }

    public ImmuneRegionInterface getImmuneRegions() {
        return immuneRegions;
    }
    private final ExcludedRegionsInterface excludedRegionsInterface;

    // config option
    public static final String REF_GENOME_VERSION = "ref_genome_version";
    public static final String REF_GENOME_VERSION_CFG_DESC = "Ref genome version, 37 or 38";

    private static final Logger LOGGER = LogManager.getLogger(RefGenomeVersion.class);
    private static final String GZIP_EXTENSION = ".gz";

    private static boolean stringVersionIs37(@NotNull final String version){
        return version.equals(V37.toString()) || version.equals("37") || version.equals("HG37");
    }

    private static boolean stringVersionIs38(@NotNull final String version){
        return version.equals(V38.toString()) || version.equals("38") || version.equals("HG38");
    }

    @NotNull
    public static RefGenomeVersion from(@NotNull final String version)
    {
        if(stringVersionIs37(version))
        {
            return V37;
        }
        else if(stringVersionIs38(version))
        {
            return V38;
        }

        throw new IllegalArgumentException("Cannot resolve ref genome version: " + version);
    }

    public static RefGenomeVersion from(final ConfigBuilder configBuilder)
    {
        return configBuilder.hasValue(REF_GENOME_VERSION) ? RefGenomeVersion.from(configBuilder.getValue(REF_GENOME_VERSION)) : V37;
    }

    RefGenomeVersion(@NotNull final String identifier,
                     RefChrNameCorrectorInterface chrNameCorrector,
                     ExcludedRegionsInterface excludedRegionsInterface,
                     ImmuneRegionInterface immuneRegions, RefGenomeCoordinates refGenomeCoordinates)
    {
        mIdentifier = identifier;
        this.chrNameCorrector = chrNameCorrector;
        this.excludedRegionsInterface = excludedRegionsInterface;
        this.immuneRegions = immuneRegions;
        this.refGenomeCoordinates = refGenomeCoordinates;
    }

    public boolean is37() { return stringVersionIs37(mIdentifier); }
    public boolean is38 () { return stringVersionIs38(mIdentifier); }

    public String identifier() { return mIdentifier; }

    @NotNull
    public String versionedChromosome(@NotNull String chromosome)
    {
        if (chrNameCorrector != null){
            return chrNameCorrector.correct(chromosome);
        }
        LOGGER.warn("Unrecognized ref genome version for making chromosome ref genome specific: {}", this);
        return chromosome;
    }

    public String addVersionToFilePath(final String filePath)
    {
        String modifiedFilePath = filePath;
        if(filePath.endsWith(GZIP_EXTENSION))
        {
            modifiedFilePath = filePath.substring(0, filePath.indexOf(GZIP_EXTENSION));
        }

        if(!modifiedFilePath.contains("."))
        {
            throw new IllegalStateException("Cannot include ref genome version in file path that has no proper extension: " + filePath);
        }

        int extensionStart = modifiedFilePath.lastIndexOf(".");
        String versionedFilePath =
                modifiedFilePath.substring(0, extensionStart) + "." + this.mIdentifier + modifiedFilePath.substring(extensionStart);

        if(filePath.endsWith(GZIP_EXTENSION))
        {
            versionedFilePath = versionedFilePath + GZIP_EXTENSION;
        }

        return versionedFilePath;
    }

    public RefGenomeCoordinates getRefGenomeCoordinates() {
        return refGenomeCoordinates;
    }
}
