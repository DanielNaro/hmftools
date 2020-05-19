package com.hartwig.hmftools.gripss

import com.hartwig.hmftools.extensions.dedup.TransitiveDedup
import com.hartwig.hmftools.gripss.link.AssemblyLink
import com.hartwig.hmftools.gripss.store.LinkStore
import com.hartwig.hmftools.gripss.store.VariantStore
import htsjdk.variant.vcf.VCFFileReader
import org.apache.logging.log4j.LogManager
import java.io.File

fun main(args: Array<String>) {

    val time = System.currentTimeMillis();
    println("Starting")

    val inputVCF = "/Users/jon/hmf/analysis/gridss/CPCT02010893R_CPCT02010893T.gridss.vcf.gz"
//    val inputVCF = "/Users/jon/hmf/analysis/gridss/CPCT02010893T.gridss.somatic.vcf"
    val outputVCF = "/Users/jon/hmf/analysis/gridss/CPCT02010893T.post.vcf"
    val filterConfig = GripssFilterConfig(
            0.03,
            8,
            0.005,
            0.95,
            1000,
            350,
            50,
            6,
            50,
            5,
            32)
    val config = GripssConfig(inputVCF, outputVCF, filterConfig)

    GripssApplication(config).use { x -> x.run() }

    println("Finished in ${(System.currentTimeMillis() - time) / 1000} seconds")
}


class GripssApplication(private val config: GripssConfig) : AutoCloseable, Runnable {

    companion object {
        private val logger = LogManager.getLogger(this::class.java)
    }

    private val fileReader = VCFFileReader(File(config.inputVcf), false)
    private val fileWriter = GripssVCF(config.outputVcf)

    override fun run() {
        logger.info("Reading file: ${config.inputVcf}")

        fileWriter.writeHeader(fileReader.fileHeader)
        val structuralVariants = hardFilterVariants(fileReader)


        logger.info("LINKING")

        val variantStore = VariantStore.create(structuralVariants)
        val assemblyLinks = AssemblyLink().create(structuralVariants)
        val links = LinkStore.create(assemblyLinks)
        val assemblyDedup = TransitiveDedup(links, variantStore);

        logger.info("Writing file: ${config.outputVcf}")
        for (variant in structuralVariants) {
            val dedup = assemblyDedup.dedup(variant)
            fileWriter.writeVariant(variant.context(config.filterConfig, links.localLinkedBy(variant.vcfId), links.localLinkedBy(variant.mateId), dedup))
        }

    }

    private fun hardFilterVariants(fileReader: VCFFileReader): List<StructuralVariantContext> {
        val unfiltered: MutableSet<String> = mutableSetOf()
        val hardFilter: MutableSet<String> = mutableSetOf()
        val structuralVariants: MutableList<StructuralVariantContext> = mutableListOf()

        for (variantContext in fileReader) {
            val structuralVariant = StructuralVariantContext(variantContext)
            if (hardFilter.contains(structuralVariant.vcfId) || structuralVariant.isHardFilter(config.filterConfig)) {
                structuralVariant.mateId?.let { hardFilter.add(it) }
            } else {
                unfiltered.add(variantContext.id)
                structuralVariants.add(structuralVariant)
            }
        }

        val mateIsValidOrNull = { x: StructuralVariantContext -> x.mateId?.let { unfiltered.contains(it) } != false}
        return structuralVariants.filter { x -> !hardFilter.contains(x.vcfId) && mateIsValidOrNull(x) }
    }


    override fun close() {
        fileReader.close()
        fileWriter.close()
    }
}

