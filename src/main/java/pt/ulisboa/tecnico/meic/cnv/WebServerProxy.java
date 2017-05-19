package pt.ulisboa.tecnico.meic.cnv;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.util.IOUtils;
import com.sun.net.httpserver.HttpExchange;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

public class WebServerProxy {

    private String address;
    private int port;
    private long lastTimeUsed;
    private List<Request> activeJobs;
    private State state = State.ALIVE;
    private ServerState serverState = ServerState.READY;
    private Instance myInstance;

    /*
    public WebServerProxy(String remoteAddress, Instance instance) throws ArrayIndexOutOfBoundsException, NumberFormatException {
        String[] args = remoteAddress.split(":");
        this.address = args[0];
        this.port = Integer.valueOf(args[1]);
        activeJobs = Collections.synchronizedList(new ArrayList<Request>());
        myInstance = instance;
    }*/

    public WebServerProxy(Instance myInstance){
        port = 8000;
        this.myInstance = myInstance;
        activeJobs = Collections.synchronizedList(new ArrayList<Request>());
    }

    /*
    public WebServerProxy(String address, int port) {
        this.address = address;
        this.port = port;
        activeJobs = Collections.synchronizedList(new ArrayList<Request>());
    }*/

    public long getLastTimeUsed() {
        return this.lastTimeUsed;
    }

    public void dispatch(HttpExchange t, Request request) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            lastTimeUsed = System.currentTimeMillis();

            if (!activeJobs.contains(request))
                activeJobs.add(request);

            URL remoteResourse = new URL(getRemoteURL() + "/r.html?" + t.getRequestURI().getQuery());

            URLConnection uc = remoteResourse.openConnection();
            is = uc.getInputStream();

            t.sendResponseHeaders(200, uc.getContentLength());
            os = t.getResponseBody();

            IOUtils.copy(is, os);
            is.close();
            os.close();
            activeJobs.remove(request);
        } catch (IOException e) {
            activeJobs.remove(request);
            if (is != null) is.close();
            if (os != null) os.close();
            throw e;
        }
    }

    public Load isAvailable() {
        long startTime = System.currentTimeMillis();
        String html;
        try {
            html = Jsoup.connect("http://" + address + ":" + port + "/test").get().html();
        } catch (IOException e) {
            badStateLogic();
            return new Load(state);
        }
        if (html.contains("Page OK!")) {
            System.out.println(address + ":" + port + " is OK!");
            state = State.ALIVE;
            long elapsedTime = System.currentTimeMillis() - startTime;
            BigInteger performed = new BigInteger(html.substring(html.indexOf("=")));
            System.out.println("I performed : " + performed);
            try {
                return new Load(elapsedTime, ScalerService.getInstance().retrieveEC2Statistic(myInstance, "CPUUtilization", "Average"), state, performed);
            } catch (Exception e) {
                badStateLogic();
                return new Load(state);
            }
        } else {
            badStateLogic();
        }
        return new Load(state);
    }

    private void badStateLogic() {
        if (state == State.TERMINAL) {
            System.out.println(address + ":" + port + " is dead!");
            state = State.DEAD;
            if (myInstance != null)
                ScalerService.getInstance().terminateInstances(Collections.singletonList(myInstance.getInstanceId()));
        } else if (state != State.ZOMBIE && state != State.DEAD) {
            System.out.println(address + ":" + port + " is in zombie state!");
            state = State.ZOMBIE;
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    final State proxyState = isAvailable().getState();
                    if (proxyState == State.ZOMBIE) {
                        System.out.println(address + ":" + port + " is in terminal state!");
                        state = State.TERMINAL;
                    }
                }
            }, 1000 * 20);
        }
    }

    public boolean hasActiveJobs() {
        return !activeJobs.isEmpty();
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public double getAvgRank() {
        double sumRank = 0;
        for (Request request : activeJobs) {
            sumRank += request.getRank();
        }

        return sumRank / activeJobs.size();
    }

    public double getAvgRank(Request r) {
        double sumRank = r.getRank();
        for (Request request : activeJobs) {
            sumRank += request.getRank();
        }

        return sumRank / (activeJobs.size() + 1);
    }

    @Override
    public String toString() {
        return "NODE{remoteURL:" + getRemoteURL() + ", lastTimeUsed:" + lastTimeUsed +
                ", activeJobs: " + activeJobs + "}";
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WebServerProxy wsp = (WebServerProxy) o;
        return address.equals(wsp.getAddress()) && port == wsp.getPort();
    }

    // TODO: BERNARDO PARA USAR A INSTANCIA DE EC2
    public String getRemoteURL() {
        return "http://" + ((myInstance.getPublicIpAddress() != null)?
                myInstance.getPublicIpAddress():myInstance.getPrivateIpAddress()) + ":" + port;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public List<Request> getActiveJobs() {
        return activeJobs;
    }


    public Instance getMyInstance() {
        return myInstance;
    }

    public void setMyInstance(Instance myInstance) {
        this.myInstance = myInstance;
    }


    public ServerState getServerState() {
        return serverState;
    }

    public void setServerState(ServerState serverState) {
        this.serverState = serverState;
    }

}
