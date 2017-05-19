package pt.ulisboa.tecnico.meic.cnv;

import com.amazonaws.services.ec2.model.Instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class LoadBalancerBestChoice implements LoadBalancerChoiceStrategy {

    // measured by sampling
    private final double THRESHOLD = 4441439;
    private List<WebServerProxy> farm;
    private Estimator estimator;

    public LoadBalancerBestChoice(List<WebServerProxy> farm, Estimator estimator) {
        this.farm = farm;
        this.estimator = estimator;
    }

    // You should remember that anything that comes from here the instance needs to be resolved, i.e. you need to check
    // if the WSP is ready to start processing the request
    @Override
    public synchronized WebServerProxy chooseBestNode(final Request request) {
        // We should not give up on instances that are not alive!
        // We should not be greedy on choosing the instance, because we should not keep alot of instances up!

        Argument argument = request.getArgument();
        Metric metric = estimator.estimate(argument);

        //In case we don't have a estimate nor an exact value
        if (metric == null)
            return new LoadBalancerLRUChoice(farm).chooseBestNode(request);

        WebServerProxy bestNode = null;
        double workLoad = 0d;
        double estimate;

        for (final WebServerProxy wsp : farm) {
            if (wsp.getServerState() == ServerState.TERMINATING)
                continue;
            // check the state of wsp && if its alive check its current load
            Load state = wsp.isAvailable();
            if (state.getState() == State.ALIVE || state.getState() == State.ZOMBIE) {
                estimate = getEstimatedWorkLoad(wsp, state);
                //we have to take in consideration that big requests that are still running
                //but possibly finishing will count as a full request
                //get an average estimate to get the least avg rank (the bigger the rank is, the heavier it is
                if (bestNode == null || workLoad > estimate) {
                    bestNode = wsp;
                    workLoad = estimate;
                }
            }
        }

        // if the work I am about to do surpasses the amount I can physically do
        // possible load to second that will be added to the current node
        // we multiply for 90% since, just because those thresholds were taken at 100% CPU
        double possibleLoad = (workLoad + request.getRank()) / 60;
        if (bestNode != null && possibleLoad >= (THRESHOLD) * 0.90)
            bestNode = null;

        return bestNode == null ? getInstance() : bestNode;
    }

    private WebServerProxy getInstance() {
        // we should consider to launch another WebServerProxy if:
        // - no bestnode available since all instances are dead

        boolean newWSP = false;
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
            newWSP = true;
        }

        final WebServerProxy webServerProxy = new WebServerProxy(toUse.getPublicIpAddress() + ":" + "8000", toUse);

        // TODO: BERNARDO
        if (newWSP)
            farm.add(webServerProxy);

        // is safe to assume that any of the instances that will from here will be payed for 1 hour
        // so we should only consider to terminate instances right before the 1 hour mark
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (webServerProxy.getActiveJobs().size() == 0 && farm.size() >= 2) {
                    webServerProxy.setServerState(ServerState.TERMINATING);
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            List<String> ids = new ArrayList<>();
                            ids.add(webServerProxy.getMyInstance().getInstanceId());
                            ScalerService.getInstance().stopInstances(ids);
                        }
                    }, 9 * 60 * 1000);
                }
            }
        }, 50 * 60 * 1000);

        return webServerProxy;
    }

    private double getEstimatedWorkLoad(WebServerProxy wsp, Load state) {
        double work = 0d;
        for (Request req : wsp.getActiveJobs())
            work += req.getRank();

        // Calculating something on a CPU that is already in a bottleneck becomes expensive
        work = work * (1 + (state.getCpuUsage() / 100));
        // we need to discount the amount of work that the wsp has already performed

        // to know an estimate of how much work was performed we can consider the sum of the metrics instrumented the number of instructions to execute
        // ofc not every instruction is "simple"
        // and also we are not considering the access to disk and ram
        // its also true that more "instructions" were executed
        // we agreed on that trade-off when we grabbed only the most important metrics - precision/performance

        return work - state.getPerformed().doubleValue();
    }


    // TODO: better queries on dynamodb
    // TODO: metrics downsize
    // TODO: Politicas para dar downsize à farm

}
