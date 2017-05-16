package pt.ulisboa.tecnico.meic.cnv;

public class Metric {
    private static final double COUNT_RATIO = 1d / 3d;
    private static final double TAKEN_RATIO = 1d / 3d;
    private static final double NOT_TAKEN_RATIO = 1d / 3d;

    private long mCount;
    private long taken;
    private long notTaken;

    public Metric(long mCount, long taken, long notTaken) {
        this.mCount = mCount;
        this.taken = taken;
        this.notTaken = notTaken;
    }

    public double getRank() {
        //Implement
        //TODO
        return COUNT_RATIO * mCount + TAKEN_RATIO * taken + NOT_TAKEN_RATIO * notTaken;
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
