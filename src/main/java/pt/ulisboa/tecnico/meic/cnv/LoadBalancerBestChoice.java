package pt.ulisboa.tecnico.meic.cnv;

import java.util.List;

public class LoadBalancerBestChoice implements LoadBalancerChoiceStrategy {
    @Override
    public WebServerProxy chooseBestNode(List<WebServerProxy> farm) {
        for(WebServerProxy wsp: farm){
            if(wsp.getUnfinishedJobs().isEmpty()) return wsp;
        }
        return new LoadBalancerLRUChoice().chooseBestNode(farm);
    }
}
