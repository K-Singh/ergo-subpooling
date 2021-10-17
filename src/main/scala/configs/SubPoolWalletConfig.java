package configs;

import org.ergoplatform.appkit.config.ErgoNodeConfig;
import org.ergoplatform.appkit.config.WalletConfig;

public class SubPoolWalletConfig{
    private String walletName;

    public SubPoolWalletConfig(String name){
        walletName = name;
    }
    public String getWalletName(){
        return walletName;
    }
}
