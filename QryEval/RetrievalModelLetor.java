public class RetrievalModelLetor extends RetrievalModel {
    private double k_1;
    private double b;
    private double k_3;
    private double mu;
    private double lambda;

    public RetrievalModelLetor(double k_1, double b, double k_3,double mu, double lambda) {
        this.k_1 = k_1;
        this.b = b;
        this.k_3 = k_3;
        this.mu = mu;
        this.lambda = lambda;
    }

    public double getK_1() {
        return k_1;
    }
    public double getB() {
        return b;
    }
    public double getK_3() {
        return k_3;
    }
    public double getMu() {
        return mu;
    }
    public double getLambda() {
        return lambda;
    }

    @Override
    public String defaultQrySopName() {
        return null;
    }
}
