package pt.ulisboa.tecnico.meic.cnv;

import java.util.Map;

public class Argument {

    private String model;
    private int sceneColumns;
    private int sceneRows;
    private int windowColumns;
    private int windowRows;
    private int columnOffset;
    private int rowOffset;

    public Argument(String model, int sceneColumns, int sceneRows, int windowColumns, int windowRows, int columnOffset,
                    int rowOffset) {
        this.model = model;
        this.sceneColumns = sceneColumns;
        this.sceneRows = sceneRows;
        this.windowColumns = windowColumns;
        this.windowRows = windowRows;
        this.columnOffset = columnOffset;
        this.rowOffset = rowOffset;
    }

    public Argument(Map<String, String> a) {
        this(a.get("f"), Integer.valueOf(a.get("sc")), Integer.valueOf(a.get("sr")),
                Integer.valueOf(a.get("wc")), Integer.valueOf(a.get("wr")), Integer.valueOf(a.get("coff")),
                Integer.valueOf(a.get("roff")));
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Argument argument = (Argument) o;

        if (sceneColumns != argument.sceneColumns) return false;
        if (sceneRows != argument.sceneRows) return false;
        if (windowColumns != argument.windowColumns) return false;
        if (windowRows != argument.windowRows) return false;
        if (columnOffset != argument.columnOffset) return false;
        if (rowOffset != argument.rowOffset) return false;
        return model.equals(argument.model);
    }

    @Override
    public int hashCode() {
        int result = model.hashCode();
        result = 31 * result + sceneColumns;
        result = 31 * result + sceneRows;
        result = 31 * result + windowColumns;
        result = 31 * result + windowRows;
        result = 31 * result + columnOffset;
        result = 31 * result + rowOffset;
        return result;
    }

    @Override
    public String toString() {
        return "Argument{" +
                "model='" + model + '\'' +
                ", sceneColumns=" + sceneColumns +
                ", sceneRows=" + sceneRows +
                ", windowColumns=" + windowColumns +
                ", windowRows=" + windowRows +
                ", columnOffset=" + columnOffset +
                ", rowOffset=" + rowOffset +
                '}';
    }
}
