package pt.ulisboa.tecnico.meic.cnv;

import java.sql.Timestamp;
import java.util.UUID;

public class Request {
    private UUID id;
    private String model;
    private int sceneColumns;
    private int sceneRows;
    private int windowColumns;
    private int windowRows;
    private int columnOffset;
    private int rowOffset;
    private Timestamp timestamp;

    public Request(String model, int sceneColumns, int sceneRows, int windowColumns, int windowRows, int columnOffset, int rowOffset) {
        id = UUID.randomUUID();
        this.model = model;
        this.sceneColumns = sceneColumns;
        this.sceneRows = sceneRows;
        this.windowColumns = windowColumns;
        this.windowRows = windowRows;
        this.columnOffset = columnOffset;
        this.rowOffset = rowOffset;
        timestamp = new Timestamp(System.currentTimeMillis());
    }

    public UUID getId() {
        return id;
    }

    public String getModel() {
        return model;
    }

    public int getSceneColumns() {
        return sceneColumns;
    }

    public int getSceneRows() {
        return sceneRows;
    }

    public int getWindowColumns() {
        return windowColumns;
    }

    public int getWindowRows() {
        return windowRows;
    }

    public int getColumnOffset() {
        return columnOffset;
    }

    public int getRowOffset() {
        return rowOffset;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString(){
        return "REQUEST{id:" + id + ", f:" + model + ", sc:" + sceneColumns + ", sr;" + sceneRows + ", wc:" +
                windowColumns + ", wr:" + windowRows + ", coff:" + columnOffset + ", roff:" + rowOffset + "}";
    }
}
