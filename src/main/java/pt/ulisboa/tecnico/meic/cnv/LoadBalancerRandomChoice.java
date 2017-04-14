package pt.ulisboa.tecnico.meic.cnv;

import java.util.List;
import java.util.Random;

public class LoadBalancerRandomChoice implements LoadBalancerChoiceStrategy{
    @Override
    public WebServerProxy chooseBestNode(List<WebServerProxy> farm) {
        return farm.get(new Random().nextInt(farm.size()));
    }
}
