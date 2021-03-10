package com.hartwig.hmftools.patientdb.dao;

import static com.hartwig.hmftools.patientdb.dao.DatabaseUtil.DB_BATCH_INSERT_SIZE;
import static com.hartwig.hmftools.patientdb.database.hmfpatients.Tables.DRIVERCATALOG;
import static com.hartwig.hmftools.patientdb.database.hmfpatients.Tables.SOMATICVARIANT;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalog;
import com.hartwig.hmftools.common.drivercatalog.DriverCategory;
import com.hartwig.hmftools.common.drivercatalog.DriverType;
import com.hartwig.hmftools.common.drivercatalog.ImmutableDriverCatalog;
import com.hartwig.hmftools.common.drivercatalog.LikelihoodMethod;

import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep17;
import org.jooq.Record;
import org.jooq.Result;

class DriverCatalogDAO {

    @NotNull
    private final DSLContext context;

    DriverCatalogDAO(@NotNull final DSLContext context) {
        this.context = context;
    }

    void writeGermline(@NotNull String sample, @NotNull List<DriverCatalog> germlineCatalog) {
        deleteForSample(sample, DriverType.DRIVERS_GERMLINE);
        insert(sample, germlineCatalog);
    }

    void writeSomatic(@NotNull String sample, @NotNull List<DriverCatalog> somaticCatalog) {
        Collection<DriverType> somatics = Sets.newHashSet();
        somatics.addAll(DriverType.DRIVERS_MUTATION);
        somatics.addAll(DriverType.DRIVERS_COPY_NUMBER);
        write(sample, somaticCatalog, somatics);
    }

    void writePurple(@NotNull String sample, @NotNull List<DriverCatalog> somaticCatalog, @NotNull List<DriverCatalog> germlineCatalog) {
        writeSomatic(sample, somaticCatalog);
        writeGermline(sample, germlineCatalog);
    }

    void writeLinx(@NotNull String sample, @NotNull List<DriverCatalog> somaticCatalog) {
        write(sample, somaticCatalog, DriverType.DRIVERS_LINX);
    }

    void writeAll(@NotNull String sample, @NotNull List<DriverCatalog> driverCatalog) {
        deleteForSample(sample);
        insert(sample, driverCatalog);
    }

    void write(@NotNull String sample, @NotNull List<DriverCatalog> driverCatalog, @NotNull Collection<DriverType> types) {
        final List<DriverCatalog> filtered = driverCatalog.stream().filter(x -> types.contains(x.driver())).collect(Collectors.toList());
        deleteForSample(sample, types);
        insert(sample, filtered);
    }

    private void insert(@NotNull String sample, @NotNull List<DriverCatalog> driverCatalog) {
        Timestamp timestamp = new Timestamp(new Date().getTime());
        for (List<DriverCatalog> splitRegions : Iterables.partition(driverCatalog, DB_BATCH_INSERT_SIZE)) {
            InsertValuesStep17 inserter = context.insertInto(DRIVERCATALOG,
                    DRIVERCATALOG.SAMPLEID,
                    DRIVERCATALOG.CHROMOSOME,
                    DRIVERCATALOG.CHROMOSOMEBAND,
                    DRIVERCATALOG.GENE,
                    DRIVERCATALOG.DRIVER,
                    DRIVERCATALOG.CATEGORY,
                    DRIVERCATALOG.LIKELIHOODMETHOD,
                    DRIVERCATALOG.DRIVERLIKELIHOOD,
                    DRIVERCATALOG.MISSENSE,
                    DRIVERCATALOG.NONSENSE,
                    DRIVERCATALOG.SPLICE,
                    DRIVERCATALOG.INFRAME,
                    DRIVERCATALOG.FRAMESHIFT,
                    DRIVERCATALOG.BIALLELIC,
                    DRIVERCATALOG.MINCOPYNUMBER,
                    DRIVERCATALOG.MAXCOPYNUMBER,
                    SOMATICVARIANT.MODIFIED);
            splitRegions.forEach(x -> addRecord(timestamp, inserter, sample, x));
            inserter.execute();
        }
    }

    private static void addRecord(@NotNull Timestamp timestamp, @NotNull InsertValuesStep17 inserter, @NotNull String sample,
            @NotNull DriverCatalog entry) {
        inserter.values(sample,
                entry.chromosome(),
                entry.chromosomeBand(),
                entry.gene(),
                entry.driver(),
                entry.category(),
                entry.likelihoodMethod(),
                DatabaseUtil.decimal(entry.driverLikelihood()),
                entry.missense(),
                entry.nonsense(),
                entry.splice(),
                entry.inframe(),
                entry.frameshift(),
                entry.biallelic(),
                entry.minCopyNumber(),
                entry.maxCopyNumber(),
                timestamp);
    }

    void deleteForSample(@NotNull String sample) {
        context.delete(DRIVERCATALOG).where(DRIVERCATALOG.SAMPLEID.eq(sample)).execute();
    }

    void deleteForSample(@NotNull String sample, @NotNull Collection<DriverType> types) {
        final List<String> stringTypes = types.stream().map(Enum::toString).collect(Collectors.toList());
        context.delete(DRIVERCATALOG).where(DRIVERCATALOG.SAMPLEID.eq(sample)).and(DRIVERCATALOG.DRIVER.in(stringTypes)).execute();
    }

    @NotNull
    List<DriverCatalog> readDriverData(@NotNull String sample) {
        List<DriverCatalog> dcList = Lists.newArrayList();

        Result<Record> result = context.select().from(DRIVERCATALOG).where(DRIVERCATALOG.SAMPLEID.eq(sample)).fetch();

        for (Record record : result) {
            DriverCatalog driverCatalog = ImmutableDriverCatalog.builder()
                    .gene(record.getValue(DRIVERCATALOG.GENE))
                    .chromosome(record.getValue(DRIVERCATALOG.CHROMOSOME))
                    .chromosomeBand(record.getValue(DRIVERCATALOG.CHROMOSOMEBAND))
                    .driver(DriverType.valueOf(record.getValue(DRIVERCATALOG.DRIVER)))
                    .category(DriverCategory.valueOf(record.getValue(DRIVERCATALOG.CATEGORY)))
                    .likelihoodMethod(LikelihoodMethod.valueOf(record.getValue(DRIVERCATALOG.LIKELIHOODMETHOD)))
                    .driverLikelihood(record.getValue(DRIVERCATALOG.DRIVERLIKELIHOOD))
                    .missense(record.getValue(DRIVERCATALOG.MISSENSE))
                    .nonsense(record.getValue(DRIVERCATALOG.NONSENSE))
                    .splice(record.getValue(DRIVERCATALOG.SPLICE))
                    .inframe(record.getValue(DRIVERCATALOG.INFRAME))
                    .frameshift(record.getValue(DRIVERCATALOG.FRAMESHIFT))
                    .biallelic(record.getValue(DRIVERCATALOG.BIALLELIC) != 0)
                    .minCopyNumber(record.getValue(DRIVERCATALOG.MINCOPYNUMBER))
                    .maxCopyNumber(record.getValue(DRIVERCATALOG.MAXCOPYNUMBER))
                    .build();

            dcList.add(driverCatalog);
        }

        return dcList;
    }
}