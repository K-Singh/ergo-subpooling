package app

import app.ShellHelper.{ShellState, ShellStates, shellInput}
import com.google.gson.GsonBuilder
import com.google.gson.internal.GsonBuildConfig
import configs.{ConfigBuilder, SubPoolApiConfig, SubPoolConfig, SubPoolNodeConfig, SubPoolParameters, SubPoolWalletConfig}
import org.ergoplatform.appkit.{BlockchainContext, ErgoClient, NetworkType, RestApiErgoClient}
import org.ergoplatform.appkit.config.{ErgoNodeConfig, ErgoToolConfig}
import test.SubPool_Test_2_Miners.{holdingBoxToConsensusTx, miner1String, miner2String}

import java.io.{FileNotFoundException, FileWriter, Writer}
import scala.io._
object SubPoolingApp {

  def main(args: Array[String]): Unit = {

    // Node configuration values

    var conf = ConfigBuilder.newDefaultConfig()
    try {
      conf = SubPoolConfig.load(ConfigBuilder.defaultConfigName)
    }catch {
      case err:FileNotFoundException =>
        conf = ConfigBuilder.writeConfig(ConfigBuilder.defaultConfigName, ConfigBuilder.newDefaultConfig())
    }
    val nodeConf: SubPoolNodeConfig = conf.getNode
    val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(nodeConf.getNetworkType)

    // Create ErgoClient instance (represents connection to node)
    val ergoClient: ErgoClient = RestApiErgoClient.create(nodeConf.getNodeApi.getApiUrl, nodeConf.getNetworkType, nodeConf.getNodeApi.getApiKey, explorerUrl)

    println("======================================================================================================")
    println("                                      Ergo Subpooling dApp                                            ")
    println("======================================================================================================")
    println("Please enter \"create\" to make a new subpool, \"load\" to load from the default config file, or \"wallets\" to see wallet/signer commands.")
    println("You may also enter the load command with a custom config name to load a specific config file. Example: load config-name-here.json ")
    println("Enter \"help\" for more information:")
    println("\nWARNING: Subpooling can currently only hook into enigmapool.com and ergo.herominers.com! Please ensure you are mining to one of these pools.")
    println("More mining pools are planned to be added in the future.")
    implicit val shellState: ShellState = ShellStates.mainState
    Iterator.continually(shellInput)
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
        case "wallets" => AppCommands.wallets(ergoClient)
        case "help" => AppCommands.help
        case _      => println("Please enter a valid command, try \"help\" for more info.")

      }

  }
}
