package configs

import com.google.gson.GsonBuilder
import org.ergoplatform.appkit.NetworkType

import java.io.FileWriter

object ConfigBuilder {
  final val defaultURL = "http://213.239.193.208:9053/"
  final val defaultWalletSignerName = "ENTER WALLET/SIGNER NAME HERE"
  final val defaultConfigName = "subpool_config.json"

  def newDefaultConfig(): SubPoolConfig = {
    val api = new SubPoolApiConfig(defaultURL, "")
    val wallet = new SubPoolWalletConfig(defaultWalletSignerName)
    val node = new SubPoolNodeConfig(api, wallet, NetworkType.MAINNET)
    val parameters = new SubPoolParameters("", Array(""), Array(""), "", "", 0.5)
    val config = new SubPoolConfig(node, parameters)
    config
  }

  def newCustomConfig(walletName: String, params:SubPoolParameters): SubPoolConfig = {
    val api = new SubPoolApiConfig(defaultURL, "")
    val wallet = new SubPoolWalletConfig(walletName)
    val node = new SubPoolNodeConfig(api, wallet, NetworkType.MAINNET)
    val config = new SubPoolConfig(node, params)
    config
  }

  def writeConfig(fileName: String, conf: SubPoolConfig): SubPoolConfig = {
    val gson = new GsonBuilder().setPrettyPrinting().create()
    val fileWriter = new FileWriter(fileName)
    gson.toJson(conf, fileWriter)
    fileWriter.close()
    conf
  }
}
