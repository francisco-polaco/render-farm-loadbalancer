package pt.ulisboa.tecnico.meic.cnv;

import com.amazonaws.util.IOUtils;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Timestamp;
import java.util.ArrayList;

public class WebServerProxy {
    private String address;
    private int port;
    private Timestamp lastTimeUsed;
    private ArrayList<Request> activeJobs = new ArrayList<>();
    private ArrayList<Request> historyJobs = new ArrayList<>();

    public WebServerProxy(String remoteAddress) throws ArrayIndexOutOfBoundsException, NumberFormatException {
        String[] args = remoteAddress.split(":");
        this.address = args[0];
        this.port = Integer.valueOf(args[1]);
    }

    public WebServerProxy(String address, int port){
        this.address = address;
        this.port = port;
    }

    public Timestamp getLastTimeUsed(){
        return this.lastTimeUsed;
    }

    public void dispatch(HttpExchange t, Request request) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            lastTimeUsed = new Timestamp(System.currentTimeMillis());
            activeJobs.add(request);
            historyJobs.add(request);

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
        }
        catch(IOException e){
            activeJobs.remove(request);
            if(is != null) is.close();
            if(os != null) os.close();
            throw e;
        }
    }

    public boolean isAvailable() {
        try (Socket socket = new Socket(address, port)) {
            return true;
        } catch (IOException ex) {
        /* ignore */
        }
        return false;
    }

    public boolean hasNoActiveJobs() {
        return activeJobs.isEmpty();
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString(){
        return "NODE{remoteURL:" + getRemoteURL() + ", lastTimeUsed:" + lastTimeUsed +
                ", activeJobs: " + activeJobs + ", historyJobs: "+ historyJobs + "}";
    }

    @Override
    public boolean equals(Object o){
        if(o == null)
            return false;
        if(!this.getClass().equals(o.getClass()))
            return false;

        WebServerProxy wsp = (WebServerProxy) o;
        return address.equals(wsp.getAddress()) && port == wsp.getPort();
    }

    public String getRemoteURL(){
        return "http://" + address + ":" + port;
    }
}
