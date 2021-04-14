package com.hartwig.hmftools.serve.refgenome.liftover;

import java.io.File;
import java.io.IOException;

import com.hartwig.hmftools.serve.ServeConfig;
import com.hartwig.hmftools.serve.ServeLocalConfigProvider;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import htsjdk.samtools.liftover.LiftOver;
import htsjdk.samtools.util.Interval;

public class LiftOverTestApplication {

    private static final Logger LOGGER = LogManager.getLogger(LiftOverTestApplication.class);

    public static void main(String[] args) throws IOException {
        Configurator.setRootLevel(Level.DEBUG);

        ServeConfig config = ServeLocalConfigProvider.create();

        Interval original = new Interval("chr17", 41197710, 41197710);
        LOGGER.debug("Starting interval is {}", original);

        LiftOver liftOver37To38 = new LiftOver(new File(config.refGenome37To38Chain()));
        Interval result = liftOver37To38.liftOver(original);
        LOGGER.debug("Interval lifted from 37 to 38 is {}", result);

        LiftOver liftOver38to37 = new LiftOver(new File(config.refGenome38To37Chain()));
        Interval backToOriginal = liftOver38to37.liftOver(result);
        LOGGER.debug("Interval lifted back to 38 is {}", backToOriginal);
    }
}
