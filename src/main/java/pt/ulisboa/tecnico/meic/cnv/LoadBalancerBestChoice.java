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
        Argument argument = request.getArgument();
        Metric metric = estimator.estimate(argument);

        //In case we don't have a estimate nor an exact value
        if (metric == null)
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
