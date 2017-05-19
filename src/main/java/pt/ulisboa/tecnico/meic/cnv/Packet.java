package pt.ulisboa.tecnico.meic.cnv;

import com.sun.net.httpserver.HttpExchange;

/**
 * Created by diogo on 19-05-2017.
 */
public class Packet {

    private HttpExchange httpExchange;
    private Request request;

    public Packet(HttpExchange httpExchange, Request request) {
        this.httpExchange = httpExchange;
        this.request = request;
    }

    public HttpExchange getHttpExchange() {
        return httpExchange;
    }

    public void setHttpExchange(HttpExchange httpExchange) {
        this.httpExchange = httpExchange;
    }

    public Request getRequest() {
        return request;
    }

    public void setRequest(Request request) {
        this.request = request;
    }
}
