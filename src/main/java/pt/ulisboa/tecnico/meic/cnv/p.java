package pt.ulisboa.tecnico.meic.cnv;

import com.amazonaws.services.ec2.model.Instance;

import java.util.List;

/**
 * Created by diogo on 19-05-2017.
 */
public class p {


    public static void main(String[] args) {
        List<Instance> instance = ScalerService.getInstance().createInstance(1, 1);
        Instance i = instance.get(0);
        System.out.println(i.getPrivateDnsName());
        System.out.println(i.getPrivateIpAddress());
        System.out.println(i.getPublicIpAddress());
        System.out.println(i.getPublicDnsName());
    }
}
