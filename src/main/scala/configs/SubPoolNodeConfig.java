package configs;

import org.ergoplatform.appkit.NetworkType;
import org.ergoplatform.appkit.config.ApiConfig;
import org.ergoplatform.appkit.config.ErgoNodeConfig;
import org.ergoplatform.appkit.config.WalletConfig;

public class SubPoolNodeConfig {
    private SubPoolApiConfig nodeApi;
    private SubPoolWalletConfig wallet;
    private NetworkType networkType;

    public SubPoolNodeConfig(SubPoolApiConfig api, SubPoolWalletConfig wall, NetworkType network){
        nodeApi = api;
        wallet = wall;
        networkType = network;
    }

    public SubPoolApiConfig getNodeApi() {
        return nodeApi;
    }

    public SubPoolWalletConfig getWallet() {
        return wallet;
    }

    public NetworkType getNetworkType() {
        return networkType;
    }
}
