package configs;

public class SubPoolParameters {
    private String workerName;
    private String[] minerAddressList;
    private String[] workerList;
    private String holdingAddress;
    private String consensusAddress;
    private Double minimumPayout;

    public SubPoolParameters(String worker, String[] addrList, String[] workers, String holdingAddr, String consensusAddr, double minPay){
        minerAddressList = addrList;
        workerName = worker;
        workerList = workers;
        holdingAddress = holdingAddr;
        consensusAddress = consensusAddr;
        minimumPayout = minPay;
    }

    public String[] getMinerAddressList() {
        return minerAddressList;
    }

    public String getWorkerName() {
        return workerName;
    }

    public String[] getWorkerList() {
        return workerList;
    }

    public String getHoldingAddress() {
        return holdingAddress;
    }

    public String getConsensusAddress() {
        return consensusAddress;
    }

    public double getMinimumPayout(){   return minimumPayout;  }
}
