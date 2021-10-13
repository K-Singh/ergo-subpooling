package pools;

public class HeroMinersRequests {
    public static class Stats {
        public long shares_good;
    }
    public static class Worker {
        public String name;
        public long shares_good;
    }
    public static class PoolState {
        public Stats stats;
        public Worker[] workers;
    }
}
