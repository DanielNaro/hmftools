## Running SAGE in Germline Mode

SAGE can be configured to detect germline variants by switching the tumor and germline samples, and adjusting filtering parameters, panel definition and hotspot inputs.  

A '-panel_coverage' option has also been added which when set, provides coverage statistics per gene in the PANEL file.   This can be used to estimate the likelihood of missing a genuine germline variant.

The following sections describe how SAGE can be configured to detect pathogenic germline variants. 

## Panel
The panel is constructed from the supplied DriverGenePanel.hgxx.tsv file using the [DriverGenePanelConversion](../hmf-common/src/main/java/com/hartwig/hmftools/common/drivercatalog/panel/DriverGenePanelConversion.java) function. 

All germline and somatic reportable genes are included in the germline panel. 
Unlike the somatic panel, the germline panel includes the UTR regions in addition to the coding regions. 
Splice sites (+1,+2,+5,-2,-1) are included as well.
 
## Hotspots / Whitelist
The germline hotspot is created at the same time as the germline panel. 
We select all variants from the clinvar file that are Pathogenic or Likely_pathogenic (but not Benign or Likely_benign) and are marked as report germline hotspot in the driver gene panel.

A select number of [whitelist](../hmf-common/src/main/java/com/hartwig/hmftools/common/drivercatalog/panel/GermlineWhitelist.java) variants are added to the hotspot file. 

## BlackList
[Blacklisted](../hmf-common/src/main/java/com/hartwig/hmftools/common/drivercatalog/panel/GermlineBlacklist.java) variants are not reported as pathogenic.  
Neither are BRCA variants in the range 13:32972625-32972907 (or v38 -> chr13:32398488-32398770).

## Parameters
To run SAGE in germline mode we use the following parameters:

```
-tumor REFERENCE_SAMPLE
-tumor_bam REFERENCE_BAM
-reference TUMOR_SAMPLE
-reference_bam TUMOR_BAM
-panel_only
-panel_coverage true
-hotspot_min_tumor_qual 50
-panel_min_tumor_qual 75
-hotspot_max_germline_vaf 100
-hotspot_max_germline_rel_raw_base_qual 100
-panel_max_germline_vaf 100
-panel_max_germline_rel_raw_base_qual 100
-mnv_filter_enabled false
``` 

Note that the tumor and reference labels/bams are switched. 

These changes disable the germline filters (which is actually the tumor).

## Post Process

- Filter for PASS
- Re-arrange sample names to be reference first then tumor
- Annotate with mappability
- Annotate with clinvar
- Annotate with blacklist bed file (brca locations)
- Annotate with blacklist vcf file
- Annotate with SnpEff


```
/opt/tools/bcftools/1.9/bcftools filter -i 'FILTER=\"PASS\"' /data/output/tumor.sage.germline.vcf.gz -O z -o /data/output/tumor.sage.pass.vcf.gz
/opt/tools/bcftools/1.9/bcftools view -s reference,tumor /data/output/tumor.sage.pass.vcf.gz -O z -o /data/output/tumor.sage.sort.vcf.gz
/opt/tools/bcftools/1.9/bcftools annotate -a /opt/resources/mappability/37/out_150.mappability.37.bed.gz -h /opt/resources/mappability/mappability.hdr -c CHROM,FROM,TO,-,MAPPABILITY /data/output/tumor.sage.sort.vcf.gz -O z -o /data/output/tumor.mappability.annotated.vcf.gz
/opt/tools/bcftools/1.9/bcftools annotate -a /opt/resources/sage/37/clinvar.37.vcf.gz -c INFO/CLNSIG,INFO/CLNSIGCONF /data/output/tumor.mappability.annotated.vcf.gz -O z -o /data/output/tumor.clinvar.vcf.gz
/opt/tools/bcftools/1.9/bcftools annotate -a /opt/resources/sage/37/KnownBlacklist.germline.37.bed.gz -m BLACKLIST_BED -c CHROM,FROM,TO /data/output/tumor.clinvar.vcf.gz -O z -o /data/output/tumor.blacklist.regions.vcf.gz
/opt/tools/bcftools/1.9/bcftools annotate -a /opt/resources/sage/37/KnownBlacklist.germline.37.vcf.gz -m BLACKLIST_VCF /data/output/tumor.blacklist.regions.vcf.gz -O z -o /data/output/tumor.blacklist.variants.vcf.gz
opt/tools/snpEff/4.3s/snpEff.sh /opt/tools/snpEff/4.3s/snpEff.jar /opt/resources/snpeff/snpEff.config GRCh37.75 /data/output/tumor.blacklist.variants.vcf.gz /data/output/tumor.sage.germline.filtered.vcf
```

## Known Issues

INDEL VAFs in long repeats are systematically underestimated by SAGE (due to jitter).
This makes things even worse when evaluating variant copy number in tumors for germline variants as we will assume germline VAF = 0.5 if we make the germline genotype HET incorrectly.
