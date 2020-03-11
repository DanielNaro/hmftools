package com.hartwig.hmftools.isofox.exp_rates;

import static com.hartwig.hmftools.isofox.IsofoxConfig.RE_LOGGER;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.RESIDUAL_PERC;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.RESIDUAL_TOTAL;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.calcResiduals;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.calculateFittedCounts;
import static com.hartwig.hmftools.sig_analyser.common.DataUtils.sumVector;

import com.hartwig.hmftools.sig_analyser.common.SigMatrix;

public class ExpectationMaxFit
{
    private static final int MAX_ITERATIONS = 20;
    private static final double RESIDUALS_EXIT_PERC = 0.001;

    public static final double[] performFit(final double[] transCounts, final SigMatrix transDefinitions)
    {
        return performFit(transCounts, transDefinitions, RESIDUALS_EXIT_PERC, MAX_ITERATIONS);
    }

    public static final double[] performFit(
            final double[] transCounts, final SigMatrix transDefinitions, double minResidualsPerc, int maxIterations)
    {
        int definitionCount = transDefinitions.Cols;
        int categoryCount = transDefinitions.Rows;

        double totalCounts = sumVector(transCounts);
        double initialAlloc = 1 / (double)definitionCount;

        double[] allocations = new double[definitionCount];

        for(int transId = 0; transId < definitionCount; ++transId)
        {
            allocations[transId] = initialAlloc;
        }

        int iteration = 0;
        double[] newAllocations = new double[definitionCount];
        double lastResiduals = 0;

        while(iteration < maxIterations)
        {
            double[] allocFactors = new double[categoryCount];

            for(int transId = 0; transId < definitionCount; ++transId)
            {
                newAllocations[transId] = 0;

                final double[] ratios = transDefinitions.getCol(transId);

                for(int catId = 0; catId < categoryCount; ++catId)
                {
                    allocFactors[catId] += allocations[transId] * ratios[catId];
                }
            }

            for(int transId = 0; transId < definitionCount; ++transId)
            {
                final double[] ratios = transDefinitions.getCol(transId);
                double transAlloc = allocations[transId];

                for(int catId = 0; catId < categoryCount; ++catId)
                {
                    if(allocFactors[catId] == 0)
                        continue;

                    double catCount = transCounts[catId];
                    newAllocations[transId] += catCount * ratios[catId] * transAlloc / allocFactors[catId];
                }
            }

            // calculate residuals
            double[] fittedCounts = calculateFittedCounts(transDefinitions, newAllocations);

            double[] residuals = calcResiduals(transCounts, fittedCounts, totalCounts);

            RE_LOGGER.trace(String.format("totalCount(%.0f) residuals(%.0f perc=%.3f) iteration(%d)",
                    totalCounts, residuals[RESIDUAL_TOTAL], residuals[RESIDUAL_PERC], iteration));

            /*
            if(lastResiduals > 0)
            {
                // exit if the residuals total has almost stopped improving each iteration
                double residualChange = lastResiduals - residuals[RESIDUAL_TOTAL];
                double residualsChangePerc = residualChange / totalCounts;

                if(residualsChangePerc < RESIDUALS_EXIT_PERC)
                    break;
            }
            */

            lastResiduals = residuals[RESIDUAL_TOTAL];

            if(residuals[RESIDUAL_PERC] < RESIDUALS_EXIT_PERC)
                break;

            for(int transId = 0; transId < definitionCount; ++transId)
            {
                allocations[transId] = newAllocations[transId] / totalCounts;
            }

            ++iteration;
        }

        return newAllocations;
    }

}
