package app

import com.google.gson.GsonBuilder
import com.google.gson.internal.GsonBuildConfig
import configs.SubPoolConfig
import org.ergoplatform.appkit.{BlockchainContext, ErgoClient, NetworkType, RestApiErgoClient}
import org.ergoplatform.appkit.config.{ErgoNodeConfig, ErgoToolConfig}
import test.SubPool_Test_2_Miners.{holdingBoxToConsensusTx, miner1String, miner2String}

import scala.io._
object SubPoolingApp {
  def main(args: Array[String]): Unit = {

    // Node configuration values
    //TODO Make sure to change back to subpool_config.json
    val conf: SubPoolConfig = SubPoolConfig.load("subpool_config.json")
    val nodeConf: ErgoNodeConfig = conf.getNode
    val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(conf.getNode.getNetworkType)

    // Create ErgoClient instance (represents connection to node)
    val ergoClient: ErgoClient = RestApiErgoClient.create(nodeConf, explorerUrl)

    println("This is the Ergo Subpooling dApp. Please enter \"create\" to make a new subpool or \"load\" to load from config file.")
    println("Enter \"help\" for more information:")
    println("WARNING: Subpooling can currently only hook into enigmapool.com! Please ensure you are mining to this pool.")
    println("More mining pools are planned to be added in the future.")
    Iterator.continually(scala.io.StdIn.readLine)
      .takeWhile(_ != "exit")
      .foreach{
        case "create" => AppCommands.create(ergoClient, conf)
        case "load" => AppCommands.load(ergoClient, conf)
        case "help" => AppCommands.help
        case _      => println("Please enter a valid command, try \"help\" for more info.")
      }

    //val txJson: String = sendTx("subpool_config.json")

  }

}
