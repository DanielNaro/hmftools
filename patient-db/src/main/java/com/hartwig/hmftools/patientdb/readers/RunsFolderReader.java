package com.hartwig.hmftools.patientdb.readers;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.context.ProductionRunContextFactory;
import com.hartwig.hmftools.common.context.RunContext;
import com.hartwig.hmftools.common.io.FolderChecker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public final class RunsFolderReader {

    private static final Logger LOGGER = LogManager.getLogger(RunsFolderReader.class);

    private RunsFolderReader() {
    }

    @NotNull
    public static List<RunContext> extractRunContexts(@NotNull final File dir) throws IOException {
        final List<RunContext> runContexts = Lists.newArrayList();
        final File[] folders = dir.listFiles(File::isDirectory);
        if (folders == null) {
            throw new IOException("List files in " + dir.getName() + " returned null.");
        }
        for (final File folder : folders) {
            try {
                final String runDirectory = FolderChecker.build().checkFolder(folder.getPath());
                final RunContext runContext = ProductionRunContextFactory.fromRunDirectory(runDirectory);
                runContexts.add(runContext);
            } catch (IOException e) {
                LOGGER.error(e.getMessage());
            }
        }
        return runContexts;
    }
}
