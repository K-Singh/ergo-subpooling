package app

import com.google.gson.GsonBuilder
import com.google.gson.internal.GsonBuildConfig
import configs.{SubPoolApiConfig, SubPoolConfig, SubPoolNodeConfig, SubPoolParameters, SubPoolWalletConfig}
import org.ergoplatform.appkit.{BlockchainContext, ErgoClient, NetworkType, RestApiErgoClient}
import org.ergoplatform.appkit.config.{ErgoNodeConfig, ErgoToolConfig}
import test.SubPool_Test_2_Miners.{holdingBoxToConsensusTx, miner1String, miner2String}

import java.io.{FileNotFoundException, FileWriter, Writer}
import scala.io._
object SubPoolingApp {

  def main(args: Array[String]): Unit = {

    // Node configuration values
    //TODO Make sure to change back to subpool_config.json
    var conf = constructNewDefaultConfig()
    try {
      conf = SubPoolConfig.load("subpool_config.json")
    }catch {
      case err:FileNotFoundException =>
        conf = constructNewDefaultConfig()
        println(conf.getNode.getWallet.getMnemonic)
        val gson = new GsonBuilder().setPrettyPrinting().create()
        val fileWriter = new FileWriter("subpool_config.json")

        gson.toJson(conf, fileWriter)
        fileWriter.close()

    }
    val nodeConf: SubPoolNodeConfig = conf.getNode
    val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(nodeConf.getNetworkType)

    // Create ErgoClient instance (represents connection to node)
    val ergoClient: ErgoClient = RestApiErgoClient.create(nodeConf.getNodeApi.getApiUrl, nodeConf.getNetworkType, nodeConf.getNodeApi.getApiKey, explorerUrl)

    println("This is the Ergo Subpooling dApp. Please enter \"create\" to make a new subpool or \"load\" to load from the default config file.")
    println("You may also enter the load command with a custom config name to load a specific config file. Example: load config-name-here.json ")
    println("Enter \"help\" for more information:")
    println("WARNING: Subpooling can currently only hook into enigmapool.com and ergo.herominers.com! Please ensure you are mining to one of these pools.")
    println("More mining pools are planned to be added in the future.")
    Iterator.continually(scala.io.StdIn.readLine)
      .takeWhile(_ != "exit")
      .foreach{
        case "create" => AppCommands.create(ergoClient, conf)
        case msg:String if msg.contains("load ") =>
          if(msg.split(" ").length > 1) {
            val configName = msg.split(" ")(1)
            AppCommands.load(ergoClient, conf, configPath=configName)
          }else{
            println("Error: enter the name of the config file you are loading.")
          }
        case "load" => AppCommands.load(ergoClient, conf)
        case "help" => AppCommands.help
        case _      => println("Please enter a valid command, try \"help\" for more info.")
      }

  }

  def constructNewDefaultConfig(): SubPoolConfig = {
    val defaultURL = "http://213.239.193.208:9053/"
    val defaultMneumonic = "ENTER WALLET MNEMONIC HERE"
    val defaultPassword = "ENTER WALLET PASSWORD HERE"
    val api = new SubPoolApiConfig(defaultURL, "")
    val wallet = new SubPoolWalletConfig(defaultMneumonic, defaultPassword, "")
    val node = new SubPoolNodeConfig(api, wallet, NetworkType.MAINNET)
    val parameters = new SubPoolParameters("", Array(""), Array(""), "", "", 0.5)
    val config = new SubPoolConfig(node, parameters)
    config
  }

}
