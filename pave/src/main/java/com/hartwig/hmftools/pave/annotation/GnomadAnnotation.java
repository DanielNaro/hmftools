package com.hartwig.hmftools.pave.annotation;

import static com.hartwig.hmftools.common.utils.file.FileDelimiters.CSV_DELIM;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.createBufferedReader;
import static com.hartwig.hmftools.common.variant.PaveVcfTags.GNOMAD_FREQ;
import static com.hartwig.hmftools.common.variant.PaveVcfTags.GNOMAD_FREQ_DESC;
import static com.hartwig.hmftools.pave.PaveConfig.PV_LOGGER;
import static com.hartwig.hmftools.pave.resources.GnomadCacheBuilder.GNOMAD_FILE_ID;
import static com.hartwig.hmftools.pave.resources.GnomadCacheBuilder.formFileId;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.utils.config.ConfigBuilder;
import com.hartwig.hmftools.pave.VariantData;

import org.apache.logging.log4j.Level;

import htsjdk.variant.vcf.VCFFilterHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;

public class GnomadAnnotation
{
    private final Map<String,Map<Integer,List<GnomadVariant>>> mFrequencies;
    private final RefGenomeVersion mRefGenomeVersion;
    private final boolean mLoadChromosomeOnDemand;
    private final Map<String,String> mChromosomeFiles;
    private final double mPonFilterThreshold;
    private boolean mHasValidData;

    public static final String GNOMAD_FREQUENCY_FILE = "gnomad_freq_file";
    public static final String GNOMAD_FREQUENCY_DIR = "gnomad_freq_dir";
    private static final String GNOMAD_LOAD_CHR_ON_DEMAND = "gnomad_load_chr_on_demand";
    private static final String GNOMAD_PON_FILTER = "gnomad_pon_filter";

    public static final String PON_GNOMAD_FILTER = "PONGnomad";

    private static final double DEFAULT_PON_FILTER_THRESHOLD = 0.00015;

    public GnomadAnnotation(final ConfigBuilder configBuilder)
    {
        mFrequencies = Maps.newHashMap();
        mChromosomeFiles = Maps.newHashMap();
        mHasValidData = true;

        mRefGenomeVersion = RefGenomeVersion.from(configBuilder);
        mLoadChromosomeOnDemand = configBuilder.hasFlag(GNOMAD_LOAD_CHR_ON_DEMAND);

        if(configBuilder.hasValue(GNOMAD_FREQUENCY_FILE))
        {
            loadFrequency(configBuilder.getValue(GNOMAD_FREQUENCY_FILE), null);
        }
        else if(configBuilder.hasValue(GNOMAD_FREQUENCY_DIR))
        {
            String gnomadDir = configBuilder.getValue(GNOMAD_FREQUENCY_DIR);
            loadAllFrequencyFiles(gnomadDir);
        }

        mPonFilterThreshold = configBuilder.getDecimal(GNOMAD_PON_FILTER);
    }

    public boolean hasData() { return !mFrequencies.isEmpty() || !mChromosomeFiles.isEmpty(); }
    public boolean hasValidData() { return mHasValidData; }

    public void annotateVariant(final VariantData variant)
    {
        Double gnomadFreq = getFrequency(variant);

        if(gnomadFreq != null)
        {
            variant.setGnomadFrequency(gnomadFreq);

            if(exceedsPonThreshold(gnomadFreq))
                variant.addFilter(PON_GNOMAD_FILTER);
        }
    }

    public boolean exceedsPonThreshold(final Double frequency)
    {
        return frequency != null && mPonFilterThreshold >= 0 && frequency >= mPonFilterThreshold;
    }

    public Double getFrequency(final VariantData variant)
    {
        checkLoadChromosome(variant.Chromosome);

        if(variant.isMnv())
        {
            // check for successive bases for an MNV
            double minFreq = 0;
            for(int i = 0; i < variant.Ref.length(); ++i)
            {
                Double freq = getFrequency(
                        variant.Chromosome, variant.Position + i, variant.Ref.substring(i, i + 1), variant.Alt.substring(i, i + 1));

                if(freq == null)
                    return null;

                if(minFreq == 0 ||freq < minFreq)
                    minFreq = freq;
            }

            return minFreq;
        }
        else
        {
            return getFrequency(variant.Chromosome, variant.Position, variant.Ref, variant.Alt);
        }
    }

    public Double getFrequency(final String chromosome, int position, final String ref, final String alt)
    {
        Map<Integer,List<GnomadVariant>> posMap = mFrequencies.get(chromosome);

        if(posMap == null)
            return null;

        List<GnomadVariant> posList = posMap.get(position);

        if(posList == null)
            return null;

        GnomadVariant match = posList.stream().filter(x -> x.matches(ref, alt)).findFirst().orElse(null);
        return match != null ? match.Frequency : null;
    }

    private void checkLoadChromosome(final String chromosome)
    {
        if(!mLoadChromosomeOnDemand)
            return;

        if(mFrequencies.containsKey(chromosome))
            return;

        mFrequencies.clear();

        String chrFilename = mChromosomeFiles.get(chromosome);

        if(chrFilename == null)
        {
            PV_LOGGER.error("missing Gnomad file for chromosome({})", chromosome);
            System.exit(1);
        }

        loadFrequency(chrFilename, chromosome);
    }

    private void loadAllFrequencyFiles(final String gnomadDir)
    {
        try
        {
            final Stream<Path> stream = Files.walk(Paths.get(gnomadDir), 1, FileVisitOption.FOLLOW_LINKS);

            List<String> files = stream.filter(x -> !x.toFile().isDirectory())
                    .map(x -> x.toFile().toString())
                    .filter(x -> x.contains(GNOMAD_FILE_ID))
                    .collect(Collectors.toList());

            for(HumanChromosome humanChr : HumanChromosome.values())
            {
                String fileChrStrNoId = formFileId(gnomadDir, humanChr.toString(), null);
                String fileChrStrWithId = GNOMAD_FILE_ID + "_chr" + humanChr + "_";

                // expect file name: gnomad_variants_chr10_v38.csv.gz

                String chrFile = files.stream()
                        .filter(x -> x.endsWith(fileChrStrNoId) || x.contains(fileChrStrWithId))
                        .findFirst().orElse(null);

                String chrStr = mRefGenomeVersion.versionedChromosome(humanChr.toString());

                if(chrFile == null)
                {
                    PV_LOGGER.error("missing Gnomad chromosome({}) file", chrStr);
                    mHasValidData = false;
                    return;
                }

                if(mLoadChromosomeOnDemand)
                {
                    mChromosomeFiles.put(chrStr, chrFile);
                }
                else
                {
                    loadFrequency(chrFile, chrStr);
                }
            }
        }
        catch(IOException e)
        {
            PV_LOGGER.error("failed to find Gnomad chromosome files in dir({}): {}", gnomadDir, e.toString());
        }
    }

    private void loadFrequency(final String filename, final String fileChromosome)
    {
        // if file chromosome is supplied then it is not read from the input file
        if(filename == null)
            return;

        try
        {
            BufferedReader fileReader = createBufferedReader(filename);

            int itemCount = 0;
            String line = fileReader.readLine(); // skip header
            String currentChr = "";
            int currentPos = 0;

            Map<Integer,List<GnomadVariant>> posMap = null;
            List<GnomadVariant> posList = null;

            Integer chrIndex = fileChromosome != null ? -1 : 0;
            int index = fileChromosome != null ? 0 : 1;
            int posIndex = index++;
            int refIndex = index++;
            int altIndex = index++;
            int freqIndex = index;

            while((line = fileReader.readLine()) != null)
            {
                final String[] values = line.split(CSV_DELIM, -1);

                String chromosome = fileChromosome != null ? fileChromosome : values[chrIndex];
                int position = Integer.parseInt(values[posIndex]);
                String ref = values[refIndex];
                String alt = values[altIndex];
                double frequency = Double.parseDouble(values[freqIndex]);

                if(!chromosome.equals(currentChr))
                {
                    currentChr = chromosome;
                    currentPos = position;
                    posMap = Maps.newHashMap();
                    mFrequencies.put(chromosome, posMap);

                    posList = Lists.newArrayList();
                    posMap.put(position, posList);
                }
                else if(currentPos != position)
                {
                    currentPos = position;
                    posList = Lists.newArrayList();
                    posMap.put(position, posList);
                }

                posList.add(new GnomadVariant(ref, alt, frequency));

                ++itemCount;
            }

            Level logLevel = !mLoadChromosomeOnDemand ? Level.INFO : Level.DEBUG;
            PV_LOGGER.log(logLevel, "loaded {} gnomad frequency records from file({})", itemCount, filename);
        }
        catch(IOException e)
        {
            PV_LOGGER.error("failed to load Gnoamd frequency file({}): {}", filename, e.toString());
            return;
        }
    }

    public static void addConfig(final ConfigBuilder configBuilder)
    {
        configBuilder.addPath(GNOMAD_FREQUENCY_FILE, false, "Gnomad frequency file");
        configBuilder.addPath(GNOMAD_FREQUENCY_DIR, false, "Gnomad frequency directory");
        configBuilder.addFlag(GNOMAD_LOAD_CHR_ON_DEMAND, "Gnomad load frequency files by chromosome on demand");
        configBuilder.addDecimal(GNOMAD_PON_FILTER, "Gnomad PON frequency filter", DEFAULT_PON_FILTER_THRESHOLD);
    }

    public static void addHeader(final VCFHeader header)
    {
        header.addMetaDataLine(new VCFInfoHeaderLine(GNOMAD_FREQ, 1, VCFHeaderLineType.Float, GNOMAD_FREQ_DESC));
        header.addMetaDataLine(new VCFFilterHeaderLine(PON_GNOMAD_FILTER, "Filter Gnoamd PON"));
    }

    private class GnomadVariant
    {
        public final String Ref;
        public final String Alt;
        public final double Frequency;

        public GnomadVariant(final String ref, final String alt, final double frequency)
        {
            Ref = ref;
            Alt = alt;
            Frequency = frequency;
        }

        public boolean matches(final String ref, final String alt) { return ref.equals(Ref) && alt.equals(Alt); }
    }
}
