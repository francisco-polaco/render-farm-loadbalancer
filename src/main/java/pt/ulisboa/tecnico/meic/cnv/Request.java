package pt.ulisboa.tecnico.meic.cnv;

import java.util.Map;
import java.util.UUID;

public class Request {

    private UUID id;
    private Metric metric;
    private Argument argument;
    private long timestamp;

    public Request(String model, int sceneColumns, int sceneRows, int windowColumns, int windowRows, int columnOffset,
                   int rowOffset) throws RuntimeException {
        id = UUID.randomUUID();
        argument = new Argument(model, sceneColumns, sceneRows, windowColumns, windowRows, columnOffset, rowOffset);
        timestamp = System.currentTimeMillis();
    }


    public Request(String model, String sceneColumns, String sceneRows, String windowColumns, String windowRows,
                   String columnOffset, String rowOffset) throws RuntimeException {
        id = UUID.randomUUID();
        argument = new Argument(model, Integer.valueOf(sceneColumns), Integer.valueOf(sceneRows),
                Integer.valueOf(windowColumns), Integer.valueOf(windowRows), Integer.valueOf(columnOffset),
                Integer.valueOf(rowOffset));
        timestamp = System.currentTimeMillis();
    }

    public Request(Map<String, String> arguments) throws RuntimeException {
        id = UUID.randomUUID();
        argument = new Argument(arguments);
        timestamp = System.currentTimeMillis();
    }


    public UUID getId() {
        return id;
    }

    public Argument getArgument() {
        return argument;
    }

    public double getRank() {
        return metric.getRank();
    }

    public Metric getMetric() {
        return metric;
    }

    public void setMetric(Metric metric) {
        this.metric = metric;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Request{" +
                "id=" + id +
                ", argument=" + argument +
                ", metric=" + metric +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Request request = (Request) o;

        return this.id.equals(request.getId());
    }

}
