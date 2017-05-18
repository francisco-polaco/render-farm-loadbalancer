package pt.ulisboa.tecnico.meic.cnv;

/**
 * Created by diogo on 17-05-2017.
 */
public class Load {

    private long RTT;
    private double cpuUsage;
    private State state;


    public Load(long rtt, double cpuUsage, State state) {
        RTT = rtt;
        this.cpuUsage = cpuUsage;
        this.state = state;
    }

    public Load(State state) {
        this.state = state;
    }

    public long getRTT() {
        return RTT;
    }

    public void setRTT(long RTT) {
        this.RTT = RTT;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }
}
