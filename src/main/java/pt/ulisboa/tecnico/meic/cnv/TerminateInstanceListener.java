package pt.ulisboa.tecnico.meic.cnv;

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

/**
 * Created by diogo on 18-05-2017.
 */
public class TerminateInstanceListener extends TimerTask {

    private WebServerProxy webServerProxy;

    public TerminateInstanceListener(WebServerProxy webServerProxy) {
        this.webServerProxy = webServerProxy;
    }

    @Override
    public void run() {
        List<String> ids = new ArrayList<>();
        ids.add(webServerProxy.getMyInstance().getInstanceId());

        // I think we should keep two replicas at least
        List<Request> activeJobs = webServerProxy.getActiveJobs();
        if (activeJobs.size() == 0 && LoadBalancer.farm.size() > 2) {
            ScalerService.getInstance().stopInstances(ids);
        }

        // TODO : we can simply assume that there is no request that will take more than 10 minutes to execute and we are safe to delete without size == 0
    }

}
