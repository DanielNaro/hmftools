# Parse the arguments
args <- commandArgs(trailing=T)
bafFile <- args[1]
pcfFile   <- args[2]
kmin <- 1

library(dplyr)
library(copynumber)
print("line 9")
baf <- read.table(bafFile, header=TRUE, stringsAsFactors = T)
print("line 11")
chromosomeLevels = levels(baf$chromosome)
chromosomePrefix = ""
print("line 14")
if (any(grepl("chr", chromosomeLevels, ignore.case = T))) {
    chromosomePrefix = substr(chromosomeLevels[1], 1, 3)
}
print("line 18")

baf <- baf[,c("chromosome","position","tumorModifiedBAF")]
print("line 21")
baf$chromosome <- gsub(chromosomePrefix, "", baf$chromosome, ignore.case = T)
print("line 23")
baf.seg<-pcf(baf, verbose=FALSE, gamma=100, kmin=kmin)
print("line 25")

# copynumber pcf seems to have a bug that causes issue when n.probes == kmin
# we correct it by setting mean to tumorModifiedBAF
baf.seg = left_join(baf.seg, baf, by=c("chrom" = "chromosome", "start.pos" = "position"))
print("line 29")
baf.seg$mean = ifelse(baf.seg$n.probes==1, baf.seg$tumorModifiedBAF, baf.seg$mean)
print("line 32")

baf.seg = subset(baf.seg, select = -tumorModifiedBAF)
print("line 34")
baf.seg$chrom = paste0(chromosomePrefix, baf.seg$chrom)
print("line 36")
write.table(baf.seg, file = pcfFile, row.names = F, sep = "\t", quote = F)
print("line 39")