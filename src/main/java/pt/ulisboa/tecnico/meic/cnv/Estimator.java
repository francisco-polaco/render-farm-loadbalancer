package pt.ulisboa.tecnico.meic.cnv;

import Jama.Matrix;
import Jama.QRDecomposition;

public class Estimator {
    private final Matrix beta;  // regression coefficients
    private double SSE;         // sum of squared
    private double SST;         // sum of squared

    public Estimator(double[][] x, double[] y) {
        if (x.length != y.length) throw new RuntimeException("Dimensions don't agree");
        int n = y.length;

        Matrix X = new Matrix(x);

        // create matrix from vector
        Matrix Y = new Matrix(y, n);

        // find least squares solution
        QRDecomposition qr = new QRDecomposition(X);
        beta = qr.solve(Y);


        // mean of y[] values
        double sum = 0.0;
        for (int i = 0; i < n; i++)
            sum += y[i];
        double mean = sum / n;

        // total variation to be accounted for
        for (int i = 0; i < n; i++) {
            double dev = y[i] - mean;
            SST += dev * dev;
        }

        // variation not accounted for
        Matrix residuals = X.times(beta).minus(Y);
        SSE = residuals.norm2() * residuals.norm2();
    }

    public static void main(String[] args) {
        double[][] x = {{1, 10, 20},
                {1, 20, 40},
                {1, 40, 15},
                {1, 80, 100},
                {1, 160, 23},
                {1, 200, 18}};
        double[] y = {243, 483, 508, 1503, 1764, 2129};
        Estimator regression = new Estimator(x, y);

        System.out.printf("%.2f + %.2fa + %.2fb  (R^2 = %.2f)\n",
                regression.beta(0), regression.beta(1), regression.beta(2), regression.R2());
    }

    public double beta(int j) {
        return beta.get(j, 0);
    }

    public double R2() {
        return 1.0 - SSE / SST;
    }

    public Metric getMetricEstimate(Argument argument) {
        //TODO
        return new Metric(0, 0, 0);
    }
}
