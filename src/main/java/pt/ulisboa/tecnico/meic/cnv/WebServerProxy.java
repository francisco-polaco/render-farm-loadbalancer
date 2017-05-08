package pt.ulisboa.tecnico.meic.cnv;

import com.amazonaws.util.IOUtils;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WebServerProxy {
    private String address;
    private int port;
    private long lastTimeUsed;
    private List<Request> activeJobs;

    public WebServerProxy(String remoteAddress) throws ArrayIndexOutOfBoundsException, NumberFormatException {
        String[] args = remoteAddress.split(":");
        this.address = args[0];
        this.port = Integer.valueOf(args[1]);
        activeJobs = Collections.synchronizedList(new ArrayList<Request>());
    }

    public WebServerProxy(String address, int port) {
        this.address = address;
        this.port = port;
        activeJobs = Collections.synchronizedList(new ArrayList<Request>());
    }

    public long getLastTimeUsed() {
        return this.lastTimeUsed;
    }

    public void dispatch(HttpExchange t, Request request) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            lastTimeUsed = System.currentTimeMillis();
            activeJobs.add(request);

            URL remoteResourse = new URL(getRemoteURL() + "/r.html?" +
                    t.getRequestURI().getQuery() + "&requestid=" + request.getId());

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

    public boolean isAvailable() {
        //We should be checking against ping page!
        //TODO
        try (Socket socket = new Socket(address, port)) {
            return true;
        } catch (IOException ex) {
        /* ignore */
        }
        return false;
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

    public String getRemoteURL() {
        return "http://" + address + ":" + port;
    }
}
