package pools;

public class EnigmaPoolRequests {
    public static class Worker {
        public String worker;
        public int shares;
    }
    public static class Shares {
        public int valid;
    }
    public static class WorkerRequest {
        public Worker[] workers;
    }
    public static class SharesRequest {
        public Shares shares;
    }
}
