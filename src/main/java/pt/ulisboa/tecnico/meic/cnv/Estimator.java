package pt.ulisboa.tecnico.meic.cnv;

import java.util.Map;
import java.util.Random;

public class Estimator {

    private final double THRESHOLD_PERCENTAGE = 0.1;
    private final double PROBABILITY_LRU = 0.25;
    private Map<Argument, Metric> metricCache;
    private RepositoryService repositoryService;


    public Estimator(Map<Argument, Metric> metricCache, RepositoryService repositoryService) {
        this.metricCache = metricCache;
        this.repositoryService = repositoryService;
    }

    public synchronized Metric estimate(Argument argument) {
        Metric candidate = null;

        // try to find an equal argument in cache
        if (metricCache.containsKey(argument)) {
            candidate = metricCache.get(argument);
        }
        // try to find something close enough in the metricCache
        else {
            // something similar should have an equal model
            for (Argument arg : metricCache.keySet()) {
                if (arg.getModel().equals(argument.getModel()) && isSimilar(argument, arg))
                    candidate = metricCache.get(arg);
            }

            // have a probability of using LRU, to add randomness
            if (new Random().nextFloat() <= (1 - PROBABILITY_LRU)) {
                // cache can't suffice - try using dynamodb
                if (candidate == null)
                    candidate = chooseBestCandidate(argument, repositoryService.getMetricsEstimator(argument));
            }
        }
        return candidate;
    }

    private Metric chooseBestCandidate(Argument request, Map<Argument, Metric> candidates) {
        Metric candidate = null;
        long min = -1;
        for (Argument argument : candidates.keySet()) {
            long res = isBetter(request, argument);
            if (min == -1 || min >= res) {
                min = res;
                candidate = candidates.get(argument);
            }
        }
        return candidate;
    }

    private long isBetter(Argument request, Argument candidate) {
        return Math.abs(request.getWindowColumns() - candidate.getWindowColumns()) + Math.abs(request.getWindowRows() + candidate.getWindowRows());
    }


    private synchronized boolean isSimilar(Argument request, Argument inMem) {
        long reqComplexity = request.getWindowRows() * request.getWindowColumns();
        double delta = Math.abs(reqComplexity - (inMem.getWindowRows() * inMem.getWindowColumns()));
        return delta <= (THRESHOLD_PERCENTAGE * reqComplexity);
    }
}
