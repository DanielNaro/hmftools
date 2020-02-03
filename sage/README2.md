
# Somatic Alterations in Genome (SAGE)

SAGE is a precise and highly sensitive somatic SNV, MNV and small INDEL caller 

Key features include:
  - 3 tiered (Hotspot,Panel,Wide) calling allows high sensitivity calling in regions of high prior likelihood including hotspots in low mappability regions such as HIST2H3C K28M
  - kmer based model which determines a unique [read context](#read-context) for the variant + 25 bases of anchoring flanks and rigorously checks for partial or full evidence in tumor and normal regardless of local mapping alignment
  - modified [quality score](#modified-tumor-quality-score) incorporates different sources of error (MAPQ, BASEQ, edge distance, improper pair, distance from ref genome, repeat sequencing errors) without hard cutoffs
  - Explicit modelling of ‘jitter’ sequencing errors in microsatellite allows improved sensitivity in microsatelites while ignoring common sequencing errors
  - no cutoff for homopolymer repeat length for improved INDEL handling 
  - [Phasing](#5-phasing) of somatic + somatic and somatic + germline up to 25 bases
  - Native MNV handling 

 # Read context 
 
 SAGE defines a core read context around each candidate point mutation position which uniquely identifies the variant from both the reference and other possible variants at that location regardless of local alignment. 
 This read context is used to search for evidence supporting the variant and also to calculate the allelic depth and frequency.
 
 The core read context is a distinct set of bases surrounding a variant after accounting for any microhomology in the read and any repeats in either the read or ref genome.
 A 'repeat' in this context, a repeat is defined as having 1 - 10 bases repeated at least 2 times. 
 The core is a minimum of 5 bases long.  
 
 For a SNV/MNV in a non-repeat sequence this will just be the alternate base(s) with 2 bases either side. 
 For a SNV/MNV in a repeat, the entire repeat will be included as well as one base on either side, eg 'TAAAAC'.
 
 A DEL always includes the bases on either side of the deleted sequence. 
 If the delete is part of a microhomology or repeat sequence, this will also be included in the core read context.
 
 An INSERT always includes the base to the left of the insert as well as the new sequence. 
 As with a DEL, the core read context will be extended to include any repeats and/or microhomology.
 
 The importance of capturing the microhomology is demonstrated in the following example. This delete of 4 bases in a AAAC microhomology is nominally left aligned as 7: AAAAC > A but can equally be represented as 8:AAACA > A, 9:AACAA > A, 10: ACAAA > A, 11: CAAAC > C etc. 
 
 Using a (bolded) read context of `CAAAAACAAACAAACAAT` spanning the microhomology matches every alt but not the ref:
 
 <pre>
 REF:   GTCTCAAAAACAAACAAACAAACAATAAAAAAC 
 ALT:   GTCT<b>CAA    AAACAAACAAACAAT</b>AAAAAAC
 ALT:   GTCT<b>CAAA    AACAAACAAACAAT</b>AAAAAAC
 ALT:   GTCT<b>CAAAA    ACAAACAAACAAT</b>AAAAAAC
 ALT:   GTCT<b>CAAAAA    CAAACAAACAAT</b>AAAAAAC
 ALT:   GTCT<b>CAAAAAC    AAACAAACAAT</b>AAAAAAC
 ALT:   GTCT<b>CAAAAACA    AACAAACAAT</b>AAAAAAC
 ALT:   GTCT<b>CAAAAACAA    ACAAACAAT</b>AAAAAAC
 ALT:   GTCT<b>CAAAAACAAA    CAAACAAT</b>AAAAAAC
 ALT:   GTCT<b>CAAAAACAAAC    AAACAAT</b>AAAAAAC
 ALT:   GTCT<b>CAAAAACAAACA    AACAAT</b>AAAAAAC
 ALT:   GTCT<b>CAAAAACAAACAA    ACAAT</b>AAAAAAC
 ALT:   GTCT<b>CAAAAACAAACAAA    CAAT</b>AAAAAAC
 ALT:   GTCT<b>CAAAAACAAACAAAC    AAT</b>AAAAAAC
 ALT:   GTCT<b>CAAAAACAAACAAACA    AT</b>AAAAAAC
 ALT:   GTCT<b>CAAAAACAAACAAACAA    T</b>AAAAAAC
 </pre>
 
 A similar principle applies to any repeat sequences. Spanning them in the read context permits matching alternate alignments.
 
 The complete read context is the core read context flanked on either side by an additional 25 bases. 
 
# Algorithm

There are 7 key steps in the SAGE algorithm described in detail below:
  1. [Candidate Variants And Read Contexts](#1-candidate-variants-and-read-contexts)
  2. [Tumor Counts and Quality](#2-tumor-counts-and-quality)
  3. [Normal Counts and Quality](#3-normal-counts-and-quality)
  4. [Soft Filter](#4-soft-filters)
  5. [Phasing](#5-phasing)
  6. [MNV Handling](#6-de-duplication)
  7. [Output](#7-output)
 
 
## 1. Candidate Variants And Read Contexts

In this first parse of the tumor BAM, SAGE uses the `I` and `D` flag in the CIGAR to find INDELs and compares the bases in every aligned region (flags `M`, `X` or `=`) with the provided reference genome to find SNVs.
MNVs of 2 ('2X') or 3 bases ('1X1M1X' or '3X') are considered explicitly also at this stage as an independent candidate variant.

Note that there are no base quality or mapping quality requirements when looking for candidates.

SAGE tallies the raw ref/alt support and base quality and collects the read contexts of each variant.
Once finished, each variant is assigned its most frequently found read context as its primary one. 
If a variant does not have at least one complete read context (including flanks) it is discarded.
All remaining variants are then considered candidates for processing in the second pass. 

The variants at this stage have the following properties available in the VCF:

Field | Description
---|---
RC | (Core) Read Context
RC_REPS | Repeat sequence in read context
RC_REPC | Count of repeat sequence in read context
RC_MH | Microhomology in read context
RDP | Raw Depth
RAD\[0,1\] | Raw Allelic Depth \[Ref,Alt\]
RABQ\[0,1\] | Raw Allelic Base Quality \[Ref,Alt\]

Note that these raw depth values do NOT contribute to the AD, DP, QUAL or AF fields. These are calculated in the second pass. 

### Hard Filters

To reduce processing time two hard filters are applied at this stage. 

Filter | Default Value | Field
---|---|---
hard_min_tumor_raw_alt_support |2| `RAD[1]`
hard_min_tumor_raw_base_quality |0| `RABQ[1]`

These variants are excluded from this point onwards and have no further processing applied to them.  

## 2. Tumor Counts and Quality

The aim of the stage it to collect evidence of each candidate variant's read context in the tumor. 
SAGE examines every read overlapping the variant tallying matches of the read context. 
A match can be:
  - `FULL` - Core and both flanks match read at same reference location.
  - `PARTIAL` - Core and at least one flank match read fully at same position. Remaining flank matches but is truncated. 
  - `CORE` - Core matches read but neither flank does.
  - `REALIGNED` - Core and both flanks match read exactly but offset from the expected position.

Failing any of the above matches, SAGE searches for matches that would occur if a microsatellite in the complete read context was extended or retracted. 
Matches of this type we call 'jitter' and are tallied as `LENGTHENED` or `SHORTENED`. 

Lastly, if the base the variant location matches the ref genome, the `REFERENCE` tally is incremented while any read which spans the core read context increments the `TOTAL` tally. 

### Modified Tumor Quality Score

If a `FULL` or `PARTIAL` match is made, we update the quality of the variant. 
No other match contributes to quality.  
There are a number of constraints to penalise the quality if it:
  1. approaches the edge of a read,
  2. encompasses more than one variant, or
  3. has the ImproperPair flag set 

The quality is incremented as follows:

distanceFromReadEdge = minimum distance from either end of the complete read context to the edge of the read  
baseQuality (SNV/MNV) = BASEQ at variant location(s)  
baseQuality (Indel) = min BASEQ over core read context  
modifiedBaseQuality = min(baseQuality - `baseQualityFixedPenalty (12)` , 3 * distanceFromReadEdge - `distanceFromReadEdgeFixedPenalty (0)` ) 

improperPairPenalty = `mapQualityImproperPaidPenalty (15)`  if improper pair flag set else 0  
distanceFromReference = number of somatic alterations to get to reference from the complete read context  
distanceFromReferencePenalty =  (distanceFromReference - 1) * `mapQualityAdditionalDistanceFromRefPenalty (10)` 
modifiedMapQuality = MAPQ - `mapQualityFixedPenalty (15)`  - improperPairPenalty - distanceFromReferencePenalty  

matchQuality += max(0, min(modifiedMapQuality, modifiedBaseQuality))

A 'jitter penalty' is also calculated.  The jitter penalty is meant to model common sequencing errors whereby a repeat can be extended or contracted by 1 repeat unit.  Weakly supported variants with read contexts which differ by only 1 repeat from a true read context found in the tumor with a lot of support may be artefacts of these sequencing errors and are penalised.  If a `LENGTHENED` or `SHORTENED` jitter match is made we increment the jitter penalty as a function of the count of the repeat sequence in the microsatellite:

`JITTER_PENALTY` += `jitterPenalty (0.25)`  * max(0, repeatCount - `jitterMinRepeatCount (3)` )

The final quality score also takes into account jitter and is calculated as:

`QUAL` =  matchQuality - `JITTER_PENALTY`

### Output

The outputs of this stage are found in the VCF as:

Field | Formula
---|---
RC_CNT\[0,1,2,3,4,5\] | Read Context Count \[`FULL`, `PARTIAL`, `CORE`, `REALIGNED`, `REFERENCE`, `TOTAL`\]
RC_JIT\[0,1,2\] | Read Context Jitter \[`SHORTENED`, `LENGTHENED`, `JITTER_PENALTY`\]
AD\[0,1\] | Allelic Depth \[`REFERENCE`, `FULL` + `PARTIAL` + `CORE` + `REALIGNED`\]
DP | Allelic Depth (=RC_CNT\[5\])
AF | Allelic Frequency AD\[1\] / DP
QUAL | Variant Quality

### Hard Filters

To reduce processing time an additional hard filter is applied at this stage. 

Filter | Default Value | Field
---|---|---
hard_min_tumor_qual |1**| `QUAL`

** Hotspots are kept regardless of tumor quality

These variants are excluded from this point onwards and have no further processing applied to them.  
 
## 3. Normal Counts and Quality

For each candidate variant evidence in the normal is collected in same manner as step 2. 

## 4. Soft Filters

Given evidence of the variants in the tumor and normal we apply somatic filters. 
The key principles behind the filters are ensuring sufficient support for the variant (minimum VAF and score) in the tumor sample and validating that the variant is highly unlikely to be present in the normal sample.

The filters are tiered to maximise sensitivity in regions of high prior likelihood for variants. 
A hotspot panel of 10,000 specific variants are set to the highest sensitivity (TIER=`HOTSPOT`) followed by medium sensitivity for a panel of cancer related gene exons and splice regions (TIER =`PANEL`) and more aggressive filtering genome wide in both high confidence (TIER=`HIGH_CONFIDENCE`) and low confidence (TIER=`LOW_CONFIDENCE`) regions to ensure a low false positive rate genome wide.

The specific filters and default settings for each tier are:

Filter  | Hotspot | Panel | High Confidence | Low Confidence | Field
---|---|---|---|---|---
min_tumor_qual|35**|100|125|200|`QUAL`
min_tumor_vaf|0.5%|1.5%|2.5%|2.5%|`AF`
min_germline_depth|0|0|10 | 10 | Normal `RC_CNT[6]`
min_germline_depth_allosome|0|0|6 | 6 | Normal `RC_CNT[6]`
max_germline_vaf***|10%|4%|4% | 4% | Normal`RC_CNT[1+2+3+4]` / `RC_CNT[6]`
max_germline_rel_raw_base_qual|100%|4%|4% | 4% | Normal `RABQ[1]` / Tumor `RABQ[1]` 

** Even if tumor qual score cutoff is not met, hotspots are also called so long as raw tumor vaf >= 0.05 and raw allelic depth in tumor supporting the ALT >= 5 reads.  This allows calling of pathogenic hotspots even in known poor mappability regions, eg. HIST2H3C K28M.

*** A special filter (max_germline_alt_support) is applied for MNVs such that it is filtered if 1 or more read in the germline contains evidence of the variant.

## 5. Phasing

Somatic variants can be phased using the complete read context with nearby germline variants or other somatic variants.

Phasing is interesting for somatic calling from 2 perspectives: 
  - understanding the somatic mutational mechanism which has led to the variant; and 
  - understanding the functional impact of the variation.
  
Regarding mechanism, multiple somatic cis-phased variants can frequently occur together with prominent mechanisms being 2 base MNVs (eg. CC>TT in UV Signatures and CC>AA in Lung smoking signatures) and micro-inversions (which are normally called as offsetting INS and DEL).

Phasing somatic and germline variants together can also aid in understanding somatic mechanisms - for example if the germline has a 6 base deletion in a microsatellite and the tumor has a 7 base deletion, then the likely somatic mechanism is a 1 base deletion. 

Phasing is also important for functional impact particularly in 2 prominent cases: 
  - 2 nearby phased frameshift variants can lead to an inframe (and potentially activating) INDEL; and 
  - 2 phased synonymous SNVs in the same codon could potentially cause a nonsense or missense effect together.    

Two variants are considered phased if their read contexts are identical after adjusting for their relative position.
This is demonstrated in the example below where two SNVs share an identical sequence of bases.

<pre>
REF: CAACAATCGAACGATATAAATCTGAAA
A>T: CAACAATCGA<b>T</b>CGATACAATC
T>C:       TCGATCGATA<b>C</b>AAATCTGAAA
</pre>

Similarly, SNVs, MNVs and INDELs may be phased together. Any variants that are phased together will be given a shared local phase set (`LPS`) identifier.


## 6. De-duplication

### INDEL

While the read context is designed to capture a unique sequence of bases, it it sometimes possible that repeat sequences in the flanks of the read context coupled with an aligners alternate view on the same event can cause duplicate INDELs. 
If SAGE finds two phased INDELs of the same type at the same position where one is a subset of the other, then the longer is filtered with `dedup`.

### SNV / MNV

Any passing SNVs that are phased with and part of a passing MNVs will be filtered with `dedup`. 
This may occur in particular when a somatic SNV is phased with a germline SNV which given the rate of germline variants in the genome may be expected to occur approximately 1 in ~250 variants. 
In this case the functional impact of the variant is as an MNV but the mechanism is SNV.   

## 7. Output

There are two more 'hard' filters that are lazily applied at the end of the process just before writing to file. 
They only apply to variants that are already filtered. 
They do not save any processing time but do reduce the output file size. 

Filter | Default Value | Field
---|---|---
hard_min_tumor_qual_vcf | 30 | `QUAL`
hard_max_normal_alt_support |2| Normal `AD[1]`

Including the `hard_filter` flag will turn all the [soft filters](#4-soft-filters) described above into (lazily applied) hard filters. Again, hotspots are excluded from these filters.
