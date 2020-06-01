package com.hartwig.hmftools.svtools.sequence;

import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.linx.LinxConfig.GENE_TRANSCRIPTS_DIR;
import static com.hartwig.hmftools.linx.LinxConfig.REF_GENOME_FILE;
import static com.hartwig.hmftools.linx.LinxConfig.formOutputPath;
import static com.hartwig.hmftools.svtools.common.ConfigUtils.DATA_OUTPUT_DIR;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.hartwig.hmftools.common.genome.refgenome.RefGenomeInterface;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeSource;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.reference.IndexedFastaSequenceFile;

public class KmerGenerator
{
    private RefGenomeInterface mRefGenome;
    private final String mOutputDir;
    private final String mKmerInputFile;

    private static final Logger LOGGER = LogManager.getLogger(KmerGenerator.class);

    public KmerGenerator(final String refGenomeFile, final String kmerInputFile, final String outputDir)
    {
        try
        {
            IndexedFastaSequenceFile refGenome =
                    new IndexedFastaSequenceFile(new File(refGenomeFile));
            mRefGenome = new RefGenomeSource(refGenome);
        }
        catch(IOException e)
        {
            LOGGER.error("failed to load ref genome: {}", e.toString());
        }

        mKmerInputFile = kmerInputFile;
        mOutputDir = outputDir;
    }

    private static final int COL_KMER_ID = 0;
    private static final int COL_CHR = 1;
    private static final int COL_POS_START = 2;
    private static final int COL_ORIENT_START = 3;
    private static final int COL_POS_END = 4;
    private static final int COL_ORIENT_END = 5;
    private static final int COL_BASE_LENGTH = 6;
    private static final int COL_INSERT_SEQ = 7;

    public void generateKmerData()
    {
        if(mOutputDir.isEmpty() || mKmerInputFile.isEmpty())
            return;

        try
        {
            BufferedWriter writer;

            final String outputFileName = mOutputDir + "LNX_KMER_STRINGS.csv";

            writer = createBufferedWriter(outputFileName, false);
            writer.write("KmerId,Chromosome,PosStart,OrientStart,PosEnd,OrientEnd,KmerPosStrand,KmerNegStrand");
            writer.newLine();

            BufferedReader fileReader = new BufferedReader(new FileReader(mKmerInputFile));

            String line = fileReader.readLine(); // skip header

            while ((line = fileReader.readLine()) != null)
            {
                // parse CSV data
                String[] items = line.split(",", -1);

                if(items.length < COL_INSERT_SEQ+1)
                {
                    LOGGER.warn("invalid input string: {}", line);
                    continue;
                }

                final String kmerId = items[COL_KMER_ID];
                final String chromosome = items[COL_CHR];
                int posStart = Integer.parseInt(items[COL_POS_START]);
                int orientStart = Integer.parseInt(items[COL_ORIENT_START]);
                int posEnd = Integer.parseInt(items[COL_POS_END]);
                int orientEnd = Integer.parseInt(items[COL_ORIENT_END]);
                int baseLength = Integer.parseInt(items[COL_BASE_LENGTH]);
                final String insertSeq = items[COL_INSERT_SEQ];

                LOGGER.debug("producing KMER({}) for chr({}) pos({} -> {})", kmerId, chromosome, posStart, posEnd);

                final String kmerStringStart = getBaseString(chromosome, posStart, orientStart, baseLength);
                final String kmerStringEnd = getBaseString(chromosome, posEnd, orientEnd, baseLength);
                final String kmerPosStrand = kmerStringStart + insertSeq + kmerStringEnd;
                final String kmerNegStrand = reverseString(kmerPosStrand);

                writer.write(String.format("%s,%s,%d,%d,%d,%d,%s,%s",
                        kmerId, chromosome, posStart, orientStart, posEnd, orientEnd, kmerPosStrand, kmerNegStrand));
                writer.newLine();
            }

            closeBufferedWriter(writer);

        }
        catch(IOException exception)
        {
            LOGGER.error("Failed to read k-mer CSV file({})", mKmerInputFile);
        }
    }

    private final String getBaseString(final String chromosome, int position, int orientation, int length)
    {
        if(orientation == 1)
            return mRefGenome.getBaseString(chromosome, position - length, position);
        else
            return mRefGenome.getBaseString(chromosome, position, position + length);
    }

    public static final String reverseString(final String str)
    {
        String reverse = "";

        for(int i = str.length() - 1; i >= 0; --i)
        {
            reverse += str.charAt(i);
        }

        return reverse;
    }

    private static final String KMER_INPUT_FILE = "kmer_input_file";

    public static void main(@NotNull final String[] args) throws ParseException
    {
        final Options options = new Options();
        options.addOption(DATA_OUTPUT_DIR, true, "Output directory");
        options.addOption(GENE_TRANSCRIPTS_DIR, true, "Ensembl gene transcript data cache directory");
        options.addOption(KMER_INPUT_FILE, true, "File specifying locations to produce K-mers for");
        options.addOption(REF_GENOME_FILE, true, "Ref genome file");

        final CommandLineParser parser = new DefaultParser();
        final CommandLine cmd = parser.parse(options, args);

        Configurator.setRootLevel(Level.DEBUG);

        final String outputDir = formOutputPath(cmd.getOptionValue(DATA_OUTPUT_DIR));
        final String kmerInputFile = formOutputPath(cmd.getOptionValue(KMER_INPUT_FILE));
        final String refGenomeFile = formOutputPath(cmd.getOptionValue(REF_GENOME_FILE));

        KmerGenerator kmerGenerator = new KmerGenerator(refGenomeFile, kmerInputFile, outputDir);
        kmerGenerator.generateKmerData();
    }


}
