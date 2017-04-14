package pt.ulisboa.tecnico.meic.cnv;

import com.amazonaws.util.IOUtils;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Timestamp;
import java.util.UUID;

public class WebServerProxy {
    private String remoteAddress;
    private Timestamp lastTimeUsed;

    public WebServerProxy(String remoteAddress){
        this.remoteAddress = remoteAddress;
    }

    public String getRemoteAddress(){
        return this.remoteAddress;
    }

    public Timestamp getLastTimeUsed(){
        return this.lastTimeUsed;
    }

    public void dispatch(HttpExchange t, UUID uuid) throws IOException{
        lastTimeUsed = new Timestamp(System.currentTimeMillis());
        URL remoteResourse = new URL(remoteAddress + "/r.html?" +
                t.getRequestURI().getQuery() + "&clientId=" + uuid);

        URLConnection uc = remoteResourse.openConnection();
        InputStream is = uc.getInputStream();

        t.sendResponseHeaders(200, uc.getContentLength());
        OutputStream os = t.getResponseBody();

        IOUtils.copy(is,os);
        is.close();
        os.close();
    }
}
