SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS copyNumberRegion;


DROP TABLE IF EXISTS patient;
CREATE TABLE patient
(   id int NOT NULL AUTO_INCREMENT,
    patientIdentifier varchar(50) UNIQUE,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS baseline;
CREATE TABLE baseline
(   patientId int NOT NULL,
    registrationDate DATE,
    informedConsentDate DATE,
    gender varchar(10),
    hospital varchar(255),
    birthYear int,
    primaryTumorLocation varchar(255),
    cancerSubtype varchar(255),
    deathDate DATE,
    hasSystemicPreTreatment varchar(3),
    hasRadiotherapyPreTreatment varchar(3),
    preTreatments varchar(800),
    preTreatmentsType varchar(510),
    preTreatmentsMechanism varchar(510),
    PRIMARY KEY (patientId),
    FOREIGN KEY (patientId) REFERENCES patient(id)
);

DROP TABLE IF EXISTS preTreatmentDrug;
CREATE TABLE preTreatmentDrug
(   id int NOT NULL AUTO_INCREMENT,
    patientId int NOT NULL,
    startDate DATE,
    endDate DATE,
    name varchar(800),
    type varchar(255),
    mechanism varchar(255),
    bestResponse varchar(50),
    PRIMARY KEY (id),
    FOREIGN KEY (patientId) REFERENCES patient(id)
);

DROP TABLE IF EXISTS sample;
CREATE TABLE sample
(   sampleId varchar(255) NOT NULL,
    patientId int NOT NULL,
    arrivalDate DATE NOT NULL,
    samplingDate DATE,
    dnaNanograms int,
    limsPrimaryTumor varchar(255),
    tumorPercentage varchar(100),
    PRIMARY KEY (sampleId),
    FOREIGN KEY (patientId) REFERENCES patient(id)
);

DROP TABLE IF EXISTS biopsy;
CREATE TABLE biopsy
(   id int NOT NULL,
    sampleId varchar(255),
    patientId int NOT NULL,
    biopsyTaken varchar(255),
    biopsyEvaluable varchar(255),
    biopsyType varchar(255),
    biopsySite varchar(255),
    biopsyLocation varchar(255),
    biopsyDate DATE,
    PRIMARY KEY (id),
    FOREIGN KEY (sampleId) REFERENCES sample(sampleId),
    FOREIGN KEY (patientId) REFERENCES patient(id)
);

DROP TABLE IF EXISTS treatment;
CREATE TABLE treatment
(   id int NOT NULL,
    biopsyId int,
    patientId int NOT NULL,
    treatmentGiven varchar(3),
    radiotherapyGiven varchar(3),
    startDate DATE,
    endDate DATE,
    name varchar(800),
    type varchar(255),
    mechanism varchar(255),
    PRIMARY KEY (id),
    FOREIGN KEY (biopsyId) REFERENCES biopsy(id),
    FOREIGN KEY (patientId) REFERENCES patient(id)
);

DROP TABLE IF EXISTS drug;
CREATE TABLE drug
(   id int NOT NULL AUTO_INCREMENT,
    treatmentId int,
    patientId int NOT NULL,
    startDate DATE,
    endDate DATE,
    name varchar(800),
    type varchar(255),
    mechanism varchar(255),
    PRIMARY KEY (id),
    FOREIGN KEY (treatmentId) REFERENCES treatment(id),
    FOREIGN KEY (patientId) REFERENCES patient(id)
);

DROP TABLE IF EXISTS treatmentResponse;
CREATE TABLE treatmentResponse
(   id int NOT NULL AUTO_INCREMENT,
    treatmentId int,
    patientId int NOT NULL,
    measurementDone varchar(5),
    boneOnlyDisease varchar(5),
    responseDate DATE,
    response varchar(25),
    PRIMARY KEY (id),
    FOREIGN KEY (treatmentId) REFERENCES treatment(id),
    FOREIGN KEY (patientId) REFERENCES patient(id)
);

DROP TABLE IF EXISTS tumorMarker;
CREATE TABLE tumorMarker
(   id int NOT NULL AUTO_INCREMENT,
    patientId int NOT NULL,
    date DATE,
    marker varchar(50),
    measurement varchar(50),
    unit varchar(50),
    PRIMARY KEY (id),
    FOREIGN KEY (patientId) REFERENCES patient(id)
);

DROP TABLE IF EXISTS ranoMeasurement;
CREATE TABLE ranoMeasurement
(   id int NOT NULL AUTO_INCREMENT,
    patientId int NOT NULL,
    responseDate DATE,
    therapyGiven varchar(50),
    targetLesionResponse varchar(50),
    noTargetLesionResponse varchar(50),
    overallResponse varchar(50),
    PRIMARY KEY (id),
    FOREIGN KEY (patientId) REFERENCES patient(id)
);

DROP TABLE IF EXISTS clinicalFindings;
CREATE TABLE clinicalFindings
(   id int NOT NULL AUTO_INCREMENT,
    level varchar(30),
    patientId varchar(20),
    formStatus varchar(30),
    formLocked varchar(5),
    message varchar(1000),
    details varchar(1000),
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS ecrf;
CREATE TABLE ecrf
(   id int NOT NULL AUTO_INCREMENT,
    patientId varchar(20),
    studyEvent varchar(100),
    studyEventKey int not null,
    form varchar(100),
    formKey int not null,
    itemGroup varchar(100),
    itemGroupKey int not null,
    item varchar(100),
    itemValue varchar(1500),
    status varchar(30),
    locked varchar(5),
    sequenced varchar(5),
    fieldName varchar(100),
    relevant varchar(5),
    PRIMARY KEY (id),
    INDEX(patientId),
    INDEX(studyEvent),
    INDEX(form),
    INDEX(itemGroup),
    INDEX(item),
    INDEX(itemValue (255)),
    INDEX(status),
    INDEX(locked),
    INDEX(sequenced),
    INDEX(fieldName),
    INDEX(relevant)
);

DROP TABLE IF EXISTS ecrfDatamodel;
CREATE TABLE ecrfDatamodel
(   fieldName varchar(100),
    description varchar(500),
    codeList varchar(3000),
    relevant varchar(5)
);

DROP TABLE IF EXISTS formsMetadata;
CREATE TABLE formsMetadata
(   id int NOT NULL,
    tableName varchar(20),
    form varchar(20),
    status varchar(30),
    locked varchar(5),
    UNIQUE KEY (id, tableName, form)
);

DROP TABLE IF EXISTS drupEcrf;
CREATE TABLE drupEcrf
(   id int NOT NULL AUTO_INCREMENT,
    patientId varchar(20),
    studyEvent varchar(100),
    studyEventKey int not null,
    form varchar(100),
    formKey int not null,
    itemGroup varchar(100),
    itemGroupKey int not null,
    item varchar(100),
    itemValue varchar(15000),
    status varchar(30),
    locked varchar(5),
    sequenced varchar(5),
    fieldName varchar(100),
    relevant varchar(5),
    PRIMARY KEY (id),
    INDEX(patientId),
    INDEX(studyEvent),
    INDEX(form),
    INDEX(itemGroup),
    INDEX(item),
    INDEX(status),
    INDEX(sequenced),
    INDEX(fieldName),
    INDEX(relevant)
);

DROP TABLE IF EXISTS drupEcrfDatamodel;
CREATE TABLE drupEcrfDatamodel
(   fieldName varchar(100),
    description varchar(500),
    codeList varchar(5000),
    relevant varchar(5)
);

DROP TABLE if EXISTS metric;
CREATE TABLE metric
(   sampleId varchar(255) NOT NULL,
    refMeanCoverage DOUBLE PRECISION NOT NULL,
    refCoverage10xPercentage DOUBLE PRECISION NOT NULL,
    refCoverage20xPercentage DOUBLE PRECISION NOT NULL,
    tumorMeanCoverage DOUBLE PRECISION NOT NULL,
    tumorCoverage30xPercentage DOUBLE PRECISION NOT NULL,
    tumorCoverage60xPercentage DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (sampleId)
);

DROP TABLE IF EXISTS somaticVariant;
CREATE TABLE somaticVariant
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    chromosome varchar(255) NOT NULL,
    position int not null,
    filter varchar(255) NOT NULL,
    type varchar(255) NOT NULL,
    ref varchar(255) NOT NULL,
    alt varchar(255) NOT NULL,
    gene varchar(255) NOT NULL,
    genesEffected int not null,
    cosmicId varchar(255) NOT NULL,
    dbsnpId varchar(255) NOT NULL,
    worstEffectTranscript varchar(255) NOT NULL,
    worstEffect varchar(255) NOT NULL,
    worstCodingEffect varchar(255) NOT NULL,
    canonicalEffect varchar(255) NOT NULL,
    canonicalCodingEffect varchar(255) NOT NULL,
    canonicalHgvsCodingImpact varchar(255) NOT NULL,
    canonicalHgvsProteinImpact varchar(255) NOT NULL,
    microhomology varchar(255) NOT NULL,
    repeatSequence varchar(255) NOT NULL,
    repeatCount int NOT NULL,
    alleleReadCount int NOT NULL,
    totalReadCount int NOT NULL,
    adjustedVaf DOUBLE PRECISION NOT NULL,
    ploidy DOUBLE PRECISION NOT NULL,
    copyNumber DOUBLE PRECISION NOT NULL,
    highConfidence BOOLEAN NOT NULL,
    trinucleotideContext varchar(3) NOT NULL,
    subclonalLikelihood DOUBLE PRECISION NOT NULL,
    biallelic BOOLEAN NOT NULL,
    hotspot varchar(20) NOT NULL,
    mappability DOUBLE PRECISION NOT NULL,
    germlineStatus varchar(255) NOT NULL,
    minorAllelePloidy DOUBLE PRECISION NOT NULL,
    recovered BOOLEAN NOT NULL,
    kataegis varchar(20) NOT NULL,
    PRIMARY KEY (id),
    INDEX(sampleId),
    INDEX(filter),
    INDEX(type),
    INDEX(gene)
);

DROP TABLE IF EXISTS amber;
CREATE TABLE amber
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    chromosome varchar(255) NOT NULL,
    position int not null,
    heterozygous BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    INDEX(sampleId)
);

DROP TABLE IF EXISTS purity;
CREATE TABLE purity
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    version VARCHAR(255) NOT NULL,
    sampleId varchar(255) NOT NULL,
    gender varchar(255) NOT NULL,
    status varchar(255) NOT NULL,
    qcStatus varchar(255) NOT NULL,
    purity DOUBLE PRECISION not null,
    normFactor DOUBLE PRECISION not null,
    score DOUBLE PRECISION not null,
    somaticPenalty DOUBLE PRECISION not null,
    ploidy DOUBLE PRECISION not null,
    diploidProportion DOUBLE PRECISION not null,
    polyclonalProportion DOUBLE PRECISION not null,
    wholeGenomeDuplication BOOLEAN NOT NULL,
    minPurity DOUBLE PRECISION not null,
    maxPurity DOUBLE PRECISION not null,
    minPloidy DOUBLE PRECISION not null,
    maxPloidy DOUBLE PRECISION not null,
    minDiploidProportion DOUBLE PRECISION not null,
    maxDiploidProportion DOUBLE PRECISION not null,
    msIndelsPerMb DOUBLE PRECISION not null,
    msStatus varchar(10) not null,
    PRIMARY KEY (id),
    INDEX(sampleId)
);

DROP TABLE IF EXISTS purityRange;
CREATE TABLE purityRange
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    purity DOUBLE PRECISION not null,
    normFactor DOUBLE PRECISION not null,
    score DOUBLE PRECISION not null,
    somaticPenalty DOUBLE PRECISION not null,
    ploidy DOUBLE PRECISION not null,
    diploidProportion DOUBLE PRECISION not null,
    PRIMARY KEY (id),
    INDEX(sampleId)
);

DROP TABLE IF EXISTS copyNumber;
CREATE TABLE copyNumber
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    chromosome varchar(255) NOT NULL,
    start int not null,
    end int not null,
    segmentStartSupport varchar(255) NOT NULL,
    segmentEndSupport varchar(255) NOT NULL,
    depthWindowCount int not null,
    bafCount int not null,
    observedBaf DOUBLE PRECISION not null,
    baf DOUBLE PRECISION not null,
    copyNumber DOUBLE PRECISION not null,
    minorAllelePloidy DOUBLE PRECISION not null,
    majorAllelePloidy DOUBLE PRECISION not null,
    copyNumberMethod varchar(255) NOT NULL,
    gcContent DOUBLE PRECISION not null,
    minStart int not null,
    maxStart int not null,
    PRIMARY KEY (id),
    INDEX(sampleId)
);

DROP TABLE IF EXISTS copyNumberGermline;
CREATE TABLE copyNumberGermline
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    chromosome varchar(255) NOT NULL,
    start int not null,
    end int not null,
    segmentStartSupport varchar(255) NOT NULL,
    segmentEndSupport varchar(255) NOT NULL,
    depthWindowCount int not null,
    bafCount int not null,
    observedBaf DOUBLE PRECISION not null,
    baf DOUBLE PRECISION not null,
    copyNumber DOUBLE PRECISION not null,
    minorAllelePloidy DOUBLE PRECISION not null,
    majorAllelePloidy DOUBLE PRECISION not null,
    copyNumberMethod varchar(255) NOT NULL,
    gcContent DOUBLE PRECISION not null,
    minStart int not null,
    maxStart int not null,
    PRIMARY KEY (id),
    INDEX(sampleId)
);

DROP TABLE IF EXISTS geneCopyNumber;
CREATE TABLE geneCopyNumber
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    chromosome varchar(255) NOT NULL,
    start int not null,
    end int not null,
    gene varchar(255) NOT NULL,
    chromosomeBand varchar(255) NOT NULL,
    transcriptId varchar(255) NOT NULL,
    transcriptVersion int not null,
    minCopyNumber DOUBLE PRECISION not null,
    maxCopyNumber DOUBLE PRECISION not null,
    somaticRegions int not null,
    germlineHomDeletionRegions int not null,
    germlineHetToHomDeletionRegions int not null,
    minRegions int not null,
    minRegionStart int not null,
    minRegionEnd int not null,
    minRegionStartSupport varchar(255) NOT NULL,
    minRegionEndSupport varchar(255) NOT NULL,
    minRegionMethod varchar(255) NOT NULL,
    minMinorAllelePloidy DOUBLE PRECISION not null,
    PRIMARY KEY (id),
    INDEX(sampleId, gene),
    INDEX(gene)
);

DROP TABLE IF EXISTS structuralVariant;
CREATE TABLE structuralVariant
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    svId INT NOT NULL,
    sampleId varchar(255) NOT NULL,
    startChromosome varchar(255) NOT NULL,
    endChromosome varchar(255),
    startPosition int not null,
    endPosition int,
    startOrientation tinyint not null,
    endOrientation tinyint,
    startHomologySequence varchar(255) not null,
    endHomologySequence varchar(255),
    startAF DOUBLE PRECISION,
    endAF DOUBLE PRECISION,
    ploidy DOUBLE PRECISION,
    adjustedAFStart DOUBLE PRECISION,
    adjustedAFEnd DOUBLE PRECISION,
    adjustedCopyNumberStart DOUBLE PRECISION,
    adjustedCopyNumberEnd DOUBLE PRECISION,
    adjustedCopyNumberChangeStart DOUBLE PRECISION,
    adjustedCopyNumberChangeEnd DOUBLE PRECISION,
    insertSequence varchar(2048) not null,
    type varchar(255) NOT NULL,
    filter varchar(255) NOT NULL,
    imprecise BOOLEAN NOT NULL,
    qualScore DOUBLE PRECISION,
    event varchar(255),
    startTumorVariantFragmentCount int,
    startTumorReferenceFragmentCount int,
    startNormalVariantFragmentCount int,
    startNormalReferenceFragmentCount int,
    endTumorVariantFragmentCount int,
    endTumorReferenceFragmentCount int,
    endNormalVariantFragmentCount int,
    endNormalReferenceFragmentCount int,
    startIntervalOffsetStart int,
    startIntervalOffsetEnd int,
    endIntervalOffsetStart int,
    endIntervalOffsetEnd int,
    inexactHomologyOffsetStart int,
    inexactHomologyOffsetEnd int,
    startLinkedBy varchar(512),
    endLinkedBy varchar(512),
    vcfId varchar(255),
    recovered BOOLEAN NOT NULL,
    recoveryMethod varchar(64),
    recoveryFilter varchar(255),
    startRefContext varchar(255),
    endRefContext varchar(255),
    insertSequenceAlignments varchar(512),
    insertSequenceRepeatClass varchar(64),
    insertSequenceRepeatType varchar(64),
    insertSequenceRepeatOrientation tinyint,
    insertSequenceRepeatCoverage DOUBLE PRECISION,
    startAnchoringSupportDistance int,
    endAnchoringSupportDistance int,
    PRIMARY KEY (id),
    INDEX(sampleId)
);

DROP TABLE IF EXISTS svAnnotation;
CREATE TABLE svAnnotation
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    svId INT NOT NULL,
    clusterId INT NOT NULL,
    clusterReason VARCHAR(255) NULL,
    fragileSiteStart BOOLEAN NOT NULL,
    fragileSiteEnd BOOLEAN NOT NULL,
    isFoldback BOOLEAN NOT NULL,
    lineTypeStart VARCHAR(20),
    lineTypeEnd VARCHAR(20),
    ploidyMin DOUBLE PRECISION,
    ploidyMax DOUBLE PRECISION,
    geneStart VARCHAR(20),
    geneEnd varchar(20),
    replicationTimingStart DOUBLE PRECISION,
    replicationTimingEnd DOUBLE PRECISION,
    localTopologyIdStart INT,
    localTopologyIdEnd INT,
    localTopologyStart varchar(20),
    localTopologyEnd VARCHAR(20),
    localTICountStart INT,
    localTICountEnd INT,
    PRIMARY KEY (id),
    INDEX(sampleId),
    INDEX(clusterId),
    INDEX(svId)
);

DROP TABLE IF EXISTS svCluster;
CREATE TABLE svCluster
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    clusterId INT NOT NULL,
    resolvedType VARCHAR(20),
    synthetic BOOLEAN NOT NULL,
    subClonal BOOLEAN NOT NULL,
    subType VARCHAR(20),
    clusterCount INT,
    clusterDesc VARCHAR(50),
    PRIMARY KEY (id),
    INDEX(sampleId),
    INDEX(clusterId)
);

DROP TABLE IF EXISTS svLink;
CREATE TABLE svLink
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    clusterId INT NOT NULL,
    chainId INT NOT NULL,
    chainIndex VARCHAR(50) NOT NULL,
    chainLinkCount INT NOT NULL,
    lowerSvId INT NOT NULL,
    upperSvId INT NOT NULL,
    lowerBreakendIsStart BOOLEAN NOT NULL,
    upperBreakendIsStart BOOLEAN NOT NULL,
    chromosome VARCHAR(3),
    arm VARCHAR(3),
    assembled BOOLEAN NOT NULL,
    traversedSVCount INT,
    linkLength INT,
    ploidy DOUBLE PRECISION,
    ploidyUncertainty DOUBLE PRECISION,
    pseudogeneInfo varchar(255),
    PRIMARY KEY (id),
    INDEX(sampleId),
    INDEX(clusterId)
);

DROP TABLE IF EXISTS svDriver;
CREATE TABLE svDriver
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    clusterId INT NULL,
    gene VARCHAR(50) NOT NULL,
    eventType VARCHAR(50),
    PRIMARY KEY (id),
    INDEX(sampleId),
    INDEX(clusterId)
);

DROP TABLE IF EXISTS svBreakend;
CREATE TABLE svBreakend
(   id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    svId INT NOT NULL,
    startBreakend BOOLEAN NOT NULL,
    gene VARCHAR(50) NOT NULL, # length here comes from ensembl db schema
    transcriptId VARCHAR(128) NOT NULL, # length here comes from ensembl db schema
    canonicalTranscript BOOLEAN NOT NULL,
    geneOrientation VARCHAR(20) NOT NULL,
    disruptive BOOLEAN NOT NULL,
    reportedDisruption BOOLEAN NOT NULL,
    regionType VARCHAR(20) NOT NULL,
    codingContext VARCHAR(20),
    biotype VARCHAR(255),
    exonicBasePhase TINYINT,
    nextSpliceExonRank TINYINT UNSIGNED,
    nextSpliceExonPhase TINYINT,
    nextSpliceDistance INT,
    totalExonCount SMALLINT NOT NULL,
    PRIMARY KEY (id),
    INDEX(svId),
    INDEX(sampleId),
    INDEX(gene),
    INDEX(transcriptId)
);

DROP TABLE IF EXISTS svFusion;
CREATE TABLE svFusion
(   id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    fivePrimeBreakendId INT UNSIGNED NOT NULL,
    threePrimeBreakendId INT UNSIGNED NOT NULL,
    name VARCHAR(50) NOT NULL,
    reported BOOLEAN NOT NULL,
    reportedType varchar(255) NULL,
    phased BOOLEAN NULL,
    chainLength INT,
    chainLinks INT,
    chainTerminated BOOLEAN,
    domainsKept VARCHAR(255),
    domainsLost VARCHAR(255),
    skippedExonsUp INT,
    skippedExonsDown INT,
    fusedExonUp INT,
    fusedExonDown INT,
    PRIMARY KEY (id),
    INDEX(fivePrimeBreakendId),
    INDEX(threePrimeBreakendId),
    INDEX(sampleId)
);

DROP TABLE IF EXISTS viralInsertion;
CREATE TABLE viralInsertion
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    svId INT NOT NULL,
    virusId VARCHAR(50) NOT NULL,
    virusName VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    INDEX(sampleId),
    INDEX(svId)
);

DROP TABLE IF EXISTS canonicalTranscript;
CREATE TABLE canonicalTranscript
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    assembly varchar(255) NOT NULL,
    gene varchar(255) NOT NULL,
    geneId varchar(255) NOT NULL,
    chromosomeBand varchar(255) NOT NULL,
    chromosome varchar(255) NOT NULL,
    geneStart int not null,
    geneEnd int not null,
    transcriptId varchar(255) NOT NULL,
    transcriptVersion int not null,
    transcriptStart int not null,
    transcriptEnd int not null,
    exons int not null,
    exonStart int not null,
    exonEnd int not null,
    exonBases int not null,
    strand varchar(255) not null,
    codingStart int not null,
    codingEnd int not null,
    codingBases int not null,
    PRIMARY KEY (id),
    INDEX(gene),
    INDEX(transcriptId)
);

DROP TABLE IF EXISTS germlineVariant;
CREATE TABLE germlineVariant
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    chromosome varchar(255) NOT NULL,
    position int not null,
    filter varchar(255) NOT NULL,
    type varchar(255) NOT NULL,
    ref varchar(255) NOT NULL,
    alt varchar(255) NOT NULL,
    gene varchar(255) NOT NULL,
    cosmicId varchar(255) NOT NULL,
    dbsnpId varchar(255) NOT NULL,
    transcript varchar(255) NOT NULL,
    effect varchar(255) NOT NULL,
    codingEffect varchar(255) NOT NULL,
    microhomology varchar(255) NOT NULL,
    repeatSequence varchar(255) NOT NULL,
    repeatCount int NOT NULL,
    alleleReadCount int NOT NULL,
    totalReadCount int NOT NULL,
    adjustedVaf DOUBLE PRECISION NOT NULL,
    adjustedCopyNumber DOUBLE PRECISION NOT NULL,
    highConfidence BOOLEAN NOT NULL,
    trinucleotideContext varchar(3) NOT NULL,
    hgvsProtein varchar(255) NOT NULL,
    hgvsCoding varchar(255) NOT NULL,
    biallelic BOOLEAN NOT NULL,
    hotspot BOOLEAN NOT NULL,
    mappability DOUBLE PRECISION NOT NULL,
    minorAllelePloidy DOUBLE PRECISION NOT NULL,
    refStatus varchar(20) NOT NULL,
    source varchar(255) NOT NULL,
    program varchar(255) NOT NULL,
    PRIMARY KEY (id),
    INDEX(sampleId)
);

DROP TABLE IF EXISTS driverCatalog;
CREATE TABLE driverCatalog
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    chromosome varchar(255) NOT NULL,
    chromosomeBand varchar(255) NOT NULL,
    gene varchar(255) NOT NULL,
    category varchar(255) NOT NULL,
    driver varchar(255) NOT NULL,
    likelihoodMethod varchar(255) NOT NULL,
    dndsLikelihood DOUBLE PRECISION NOT NULL,
    driverLikelihood DOUBLE PRECISION NOT NULL,
    missense int NOT NULL,
    nonsense int NOT NULL,
    splice int NOT NULL,
    frameshift int NOT NULL,
    inframe int NOT NULL,
    biallelic BOOLEAN NOT NULL,
    minCopyNumber DOUBLE PRECISION NOT NULL,
    maxCopyNumber DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (id),
    INDEX(sampleId, gene),
    INDEX(gene)
);

DROP TABLE IF EXISTS chord;
CREATE TABLE chord
(   sampleId varchar(255) NOT NULL,
    BRCA1 DOUBLE PRECISION NOT NULL,
    nothing DOUBLE PRECISION NOT NULL,
    BRCA2 DOUBLE PRECISION NOT NULL,
    hrd DOUBLE PRECISION NOT NULL,
    predictedResponse DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (sampleId)
);

DROP TABLE IF EXISTS clinicalEvidence;
CREATE TABLE clinicalEvidence
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    event varchar(255) NOT NULL,
    eventMatch varchar(255) NOT NULL,
    name varchar(500) NOT NULL,
    type varchar(255) NOT NULL,
    response varchar(255) NOT NULL,
    level varchar(50) NOT NULL,
    source varchar(255) NOT NULL,
    cancerType varchar(500) NOT NULL,
    isOnLabel BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    INDEX(sampleId)
);

DROP TABLE IF EXISTS sampleMapping;
CREATE TABLE sampleMapping
(   modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    sampleId varchar(50),
    hmfId varchar(50),
    PRIMARY KEY (sampleId)
);

DROP TABLE IF EXISTS patientMapping;
CREATE TABLE patientMapping
(   modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    sourceId varchar(50),
    targetId varchar(50),
    PRIMARY KEY (sourceId, targetId)
);


SET FOREIGN_KEY_CHECKS = 1;