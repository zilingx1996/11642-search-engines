public class RetrievalModelIndri extends RetrievalModel {
    private double mu;
    private double lambda;

    public RetrievalModelIndri(double mu, double lambda) {
        this.mu = mu;
        this.lambda = lambda;
    }

    public double getMu() {
        return mu;
    }

    public double getLambda() {
        return lambda;
    }

    @Override
    public String defaultQrySopName() {
        return new String ("#and");
    }
}
