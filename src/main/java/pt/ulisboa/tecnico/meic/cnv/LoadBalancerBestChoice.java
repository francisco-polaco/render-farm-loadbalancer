package pt.ulisboa.tecnico.meic.cnv;

import com.amazonaws.services.ec2.model.Instance;

import java.util.List;
import java.util.Map;
import java.util.Timer;

public class LoadBalancerBestChoice implements LoadBalancerChoiceStrategy {

    // 70 milliseconds - based on pings done through google to various ireland locations
    private final double AVG_PING_IRELAND = 120;
    // t2.micro max CPU clock GHz
    private final double T2_MICRO_CLOCK = 3.3 * 1000000000;
    // lets assume two clocks per instruction
    private final double CPI = 2.0;

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

    // You should remember that anything that comes from here the instance needs to be resolved, i.e. you need to check
    // if the WSP is ready to start processing the request
    @Override
    public WebServerProxy chooseBestNode(final Request request) {
        // We should not give up on instances that are not alive!
        // We should not be greedy on choosing the instance, because we should not keep alot of instances up!

        Argument argument = request.getArgument();
        Metric metric = estimator.estimate(argument);

        //In case we don't have a estimate nor an exact value
        if (metric == null)
            return new LoadBalancerLRUChoice(farm).chooseBestNode(request);

        WebServerProxy bestNode = null;
        double estimatedWorkLoad = 0d;
        double estimate = 0d;

        for (final WebServerProxy wsp : farm) {
            // check the state of wsp && if its alive check its current load
            Load state = wsp.isAvailable();
            if (state.getState() == State.ALIVE || state.getState() == State.ZOMBIE) {
                estimate = getEstimatedWorkLoad(wsp, state);
                //we have to take in consideration that big requests that are still running
                // but possibly finishing will count as a full request
                //get an average estimate to get the least avg rank (the bigger the rank is, the heavier it is
                if (bestNode == null || estimatedWorkLoad > estimate) {
                    bestNode = wsp;
                    estimatedWorkLoad = getEstimatedWorkLoad(wsp, state);
                }
            }
        }

        // if the work I am about to do surpasses the amount I can physically do
        if (estimatedWorkLoad + request.getRank() >= (T2_MICRO_CLOCK / CPI) * 0.85)
            bestNode = null;

        return bestNode == null ? getInstance() : bestNode;
    }

    private WebServerProxy getInstance() {
        // we should consider to launch another WebServerProxy if:
        // - no bestnode available since all instances are dead

        WebServerProxy webServerProxy = null;
        List<Instance> reuse = ScalerService.getInstance().reuse();
        // between all my options we should consider that a pending machine is much more inexpensive than going from stopped or stopping state to running
        Instance toUse = null;
        if (reuse.size() != 0) {
            for (Instance instance : reuse) {
                if (
                        toUse == null ||
                                instance.getState().getName().equalsIgnoreCase("pending") ||
                                (instance.getState().getName().equalsIgnoreCase("stopped") && toUse.getState().getName().equalsIgnoreCase("stopping"))
                        )
                    toUse = instance;
            }
        } else {
            List<Instance> instances = ScalerService.getInstance().createInstance(1, 1);
            toUse = instances.get(0);
        }

        webServerProxy = new WebServerProxy(toUse.getPublicIpAddress() + ":" + "8000", toUse);
        // is safe to assume that any of the instances that will from here will be payed for 1 hour
        // so we should only consider to terminate instances right before the 1 hour mark
        new Timer().schedule(new TerminateInstanceListener(webServerProxy), 50 * 60 * 1000);
        return webServerProxy;
    }

    private double getEstimatedWorkLoad(WebServerProxy wsp, Load state) {
        // Instant CPU usage and RTT of ping should have an effect on the present state of the wsp
        double currentLoadAvg = wsp.getAvgRank() * (1 + (state.getCpuUsage() / 100 + state.getRTT() / AVG_PING_IRELAND));
        // we need to discount the amount of work that the wsp has already performed

        // to know an estimate of how much work was performed we can consider the sum of the metrics instrumented the number of instructions to execute
        // ofc not every instruction is "simple"
        // and also we are not considering the access to disk and ram
        // its also true that more "instructions" were executed
        // we agreed on that trade-off when we grabbed only the most important metrics - precision/performance
        double performedWork = 0d;
        for (Request req : wsp.getActiveJobs()) {
            // assuming it comes in seconds
            double totalCPUTime = (req.getRank() * CPI) / T2_MICRO_CLOCK;
            double delta = totalCPUTime * 1000 - (System.currentTimeMillis() - req.getTimestamp());
            double percentageCompleted = delta / totalCPUTime;
            performedWork += req.getRank() * percentageCompleted;
        }

        return currentLoadAvg - performedWork;
    }


}
