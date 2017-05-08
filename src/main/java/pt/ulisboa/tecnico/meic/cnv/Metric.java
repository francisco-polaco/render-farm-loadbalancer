package pt.ulisboa.tecnico.meic.cnv;

public class Metric {
    private double mCount;
    private double taken;
    private double notTaken;

    public Metric(double mCount, double taken, double notTaken) {
        this.mCount = mCount;
        this.taken = taken;
        this.notTaken = notTaken;
    }

    public double getRank() {
        //Implement
        //TODO
        return (1d / 3d) * mCount + (1d / 3d) * taken + (1d / 3d) * notTaken;
    }

    @Override
    public String toString() {
        return "Metric{" +
                "mCount=" + mCount +
                ", taken=" + taken +
                ", notTaken=" + notTaken +
                '}';
    }
}
