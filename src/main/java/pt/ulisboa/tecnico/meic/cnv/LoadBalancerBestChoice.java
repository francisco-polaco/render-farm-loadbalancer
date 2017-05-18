package pt.ulisboa.tecnico.meic.cnv;

import com.amazonaws.services.ec2.model.Instance;

import java.util.List;
import java.util.Map;

public class LoadBalancerBestChoice implements LoadBalancerChoiceStrategy {

    // 70 milliseconds - based on pings done through google to various ireland locations
    private final double AVG_PING_IRELAND = 120;
    // t2.micro max CPU clock GHz
    private final double T2_MICRO_CLOCK = 3.3 * 1000000000;
    // lets two clocks per instruction
    private final double CPI = 2.0;
    private final double STARTUP_TIME_REUSE = 30;
    private final double STARTUP_TIME_NEW = 60;

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
        double estimatedWorkLoad = 0d;
        for (WebServerProxy wsp : farm) {
            // check the state of wsp && if its alive check its current load
            Load state = wsp.isAvailable();
            if (state.getState() == State.ALIVE) {
                double estimate = getEstimatedWorkLoad(wsp, state);
                //we have to take in consideration that big requests that are still running
                // but possibly finishing will count as a full request
                //get an average estimate to get the least avg rank (the bigger the rank is, the heavier it is
                if (bestNode == null || estimatedWorkLoad > estimate) {
                    bestNode = wsp;
                    estimatedWorkLoad = getEstimatedWorkLoad(wsp, state);
                }
            }
        }

        // we should consider to launch another WebServerProxy if:
        // - no bestnode available since all instances are dead
        // - the total amount of time to complete that request will take 1:30 minute of delay,
        // we should check if the estimatedworkload doens't surpass the amount of time to open an new instance and calculating the result
        List<Instance> reuse = ScalerService.getInstance().reuse();
        double timeToBootUp = reuse.size() != 0 ? STARTUP_TIME_REUSE : STARTUP_TIME_NEW;

        // time to execute in the current bestnode
        double timeToExecute = (((request.getRank() + estimatedWorkLoad) * CPI) / T2_MICRO_CLOCK) * 1000;
        // basic time to serve the request in a clean machine
        double timeToCalculate = ((request.getRank() * CPI) / T2_MICRO_CLOCK) * 1000;

        if (bestNode == null || (timeToExecute > timeToBootUp + timeToCalculate)) {
            // between all my options we should consider that a pending machine is much more inexpensive than going from stopped or stopping state to running
            Instance toUse = null;
            if (reuse.size() != 0) {
                for (Instance instance : reuse) {
                    if (toUse == null || instance.getState().getName().equalsIgnoreCase("pending") ||
                            (instance.getState().getName().equalsIgnoreCase("stopped") && toUse.getState().getName().equalsIgnoreCase("stopping")))
                        toUse = instance;
                }
            } else {
                List<Instance> instances = ScalerService.getInstance().createInstance(1, 1);
                toUse = instances.get(0);
            }
            // TODO: Thread to accompany the state change of instance on amazon
            // TODO: Set a new WebServerProxy when running
        }


        return bestNode;
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
