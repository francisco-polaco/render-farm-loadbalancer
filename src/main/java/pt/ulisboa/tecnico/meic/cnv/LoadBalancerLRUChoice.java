package pt.ulisboa.tecnico.meic.cnv;

import java.util.List;

public class LoadBalancerLRUChoice implements LoadBalancerChoiceStrategy {
    private List<WebServerProxy> farm;

    public LoadBalancerLRUChoice(List<WebServerProxy> farm){
        this.farm = farm;
    }

    @Override
    public WebServerProxy chooseBestNode(Request request) {
        WebServerProxy minimum = null;

        for(WebServerProxy wsp: farm){
            if(minimum == null || wsp.getLastTimeUsed() == 0)
                minimum = wsp;
            else if(wsp.getLastTimeUsed() < minimum.getLastTimeUsed())
                    minimum = wsp;
        }

        return minimum;
    }
}
