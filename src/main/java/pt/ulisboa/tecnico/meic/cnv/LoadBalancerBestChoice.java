package pt.ulisboa.tecnico.meic.cnv;

import java.util.List;
import java.util.Map;

public class LoadBalancerBestChoice implements LoadBalancerChoiceStrategy {
    private List<WebServerProxy> farm;
    private Map<Argument, Metric> metricCache;
    private Estimator estimator;
    private RepositoryService repositoryService;

    public LoadBalancerBestChoice(List<WebServerProxy> farm, Map<Argument, Metric> metricCache,
                                  Estimator estimator, RepositoryService repositoryService) {
        this.farm = farm;
        this.metricCache = metricCache;
        this.estimator = estimator;
        this.repositoryService = repositoryService;
    }

    @Override
    public WebServerProxy chooseBestNode(Request request) {
        Metric metric = null;
        Argument argument = request.getArgument();

        //the metric is not in cache
        if (metricCache.get(argument) == null) {
            try {
                metric = repositoryService.getMetric(argument);
            } catch (MultipleResultsException e) {
                final List<Metric> multipleResults = e.getMultipleResults();
                //FIXME shoule we do something?
            }

            //but it's in the database!!
            if (metric != null) {
                metricCache.put(argument, metric);
                request.setMetric(metric);
            }
            //get an estimate
            else {
                metric = estimator.getMetricEstimate(argument);
                request.setMetric(metric);
            }
        } else {
            metric = metricCache.get(argument);
            request.setMetric(metricCache.get(argument));
        }

        //In case we don't have a estimate nor an exact value
        if (metric.getRank() < 0)
            return new LoadBalancerLRUChoice(farm).chooseBestNode(request);

        WebServerProxy bestNode = null;
        for (WebServerProxy wsp : farm) {
            if (bestNode == null)
                bestNode = wsp;

                //we have to take in consideration that big requests that are still running
                // but possibly finishing will count as a full request
                // we might want to divide a request rank by time (??) to get a better estimate
                //TODO
                //get an average estimate to get the least avg rank (the bigger the rank is, the heavier it is
            else if (bestNode.getAvgRank(request) > wsp.getAvgRank(request))
                bestNode = wsp;
        }
        return bestNode;
    }
}
