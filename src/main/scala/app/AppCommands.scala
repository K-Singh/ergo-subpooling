package app

import com.google.gson.GsonBuilder
import configs.{SubPoolConfig, SubPoolParameters}
import contracts.ConsensusStageHelpers.{buildConsensusFromBoxes, buildOutputsFromConsensus, findValidInputBoxes, generateConsensusContract}
import contracts.HoldingStageHelpers.generateHoldingOutputRegisterList
import contracts.{ConsensusStageHelpers, HoldingStageHelpers}
import okhttp3.{OkHttpClient, Request}
import org.ergoplatform.appkit.{Address, ErgoClient, ErgoContract, ErgoProver, ErgoToken, InputBox, NetworkType, OutBox, Parameters, SecretString, SignedTransaction, UnsignedTransaction, UnsignedTransactionBuilder}
import test.SubPool_Test_2_Miners.{consensusValue, miner1Address, workerName1}

import scala.:+
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._

object AppCommands {

  def help = {
    println("======================================================================================================")
    println("======================================================================================================")
    println("This is the Ergo Subpooling dApp. Subpools are groups of miners that pool together hashrates in order")
    println("to get quicker rewards from a larger mining pool. To create a subpool, you will need the addresses of each")
    println("miner involved in your subpool along with your unique worker name. Upon creating a pool you should receive")
    println("2 addresses, these will be the addresses of the smart contracts that let your pool function. Once your")
    println("subpool has been created, make sure to copy everything into your config file! Incorrectly copying could")
    println("lead to lost rewards. To receive rewards to your subpool, put your holdingAddress in your mining" )
    println("software. Once you've begun mining to this address, check on your pool's website to see if you've earned")
    println("any mining rewards. Once your pool has received your mining rewards, you may send a withdraw request to")
    println("your subpool. Once all members of your subpool have sent a withdraw request, the subpool will be ready")
    println("to distribute rewards to each of its members according to their share numbers. ANY member of the subpool")
    println("may sign this final transaction so that all members can get their mining rewards distributed.")
    println("Currently, subpooling only works with EngimaPool, but more pools should be on the way.")
    println("======================================================================================================")
    println("Commands:")
    println("create - Create subpool by entering in parameters")
    println("load - Load subpool from config")
    println("---- withdraw - Send withdrawal request to smart contract")
    println("---- distribute - Sign withdrawal requests and distribute rewards to members. All member's must have sent a")
    println("                  withdrawal request in order for this to work")
    println("help - Show this help message")
    println("======================================================================================================")
    println("======================================================================================================")
  }

  def create(client: ErgoClient, config: SubPoolConfig) = {
    println("Please start by entering the addresses of you and each miner in your pool, enter \"done\" when complete, or \"exit\" to stop. ")
    val addressList : ListBuffer[String] = ListBuffer.empty[String]
    val workerList: ListBuffer[String] = ListBuffer.empty[String]

    Iterator.continually(scala.io.StdIn.readLine)
      .takeWhile(_ != "done")
      .foreach{ str:String =>
        str match {
          case "exit" => sys.exit(0)
          case elem:String => addressList.append(elem).toString

        }
      }
    println("Now enter the worker names that correspond to each address in the order that they were given. Enter \"done\" when complete.")
    Iterator.continually(scala.io.StdIn.readLine)
      .takeWhile(_ != "done")
      .foreach{ str:String =>
        str match {
          case "exit" => sys.exit(0)
          case elem:String => workerList.append(elem).toString

        }
      }

    println("Please re-enter YOUR own worker name. Other members of your pool will enter their worker names into their own config.")
    println("Duplicate worker names will cause issues so make sure that all members of your pool input their names correctly!")
    val workerName = scala.io.StdIn.readLine()
    var minerAddresses = ListBuffer.empty[Address]
    try{
    minerAddresses = addressList.map{(s: String) => Address.create(s)}
    }catch {
        case err: Throwable => println("The addresses given were not valid!")
        sys.exit(0)
      }


    println("Finally, enter your mining pool's minimum payout in ERG: ")
    val minPayout: Double = scala.io.StdIn.readDouble()

    client.execute(ctx => {
      val holdingContract = HoldingStageHelpers.generateHoldingContract(ctx, minerAddresses.toList, (minPayout * Parameters.OneErg).toLong)
      val consensusContract = ConsensusStageHelpers.generateConsensusContract(ctx, minerAddresses.toList, (minPayout * Parameters.OneErg).toLong)

      val holdingAddress = contracts.generateContractAddress(holdingContract, config.getNode.getNetworkType)
      val consensusAddress = contracts.generateContractAddress(consensusContract, config.getNode.getNetworkType)
      val gson = new GsonBuilder().setPrettyPrinting().create()
      val parameters = new SubPoolParameters(workerName, addressList.toArray, workerList.toArray , holdingAddress.toString, consensusAddress.toString, minPayout)
      println("\n\n")
      println(gson.toJson(parameters))
      println("Please copy the above into your config file. The curly brackets should start next to the parameters field.")
      println("All miners in your subpool should also copy this while making sure to change their worker name.\n")
      println("")
      println("You have finished creating your subpool! Press enter to exit the program.")
      scala.io.StdIn.readLine()
      sys.exit(0)
    })
  }

  def load(ergoClient: ErgoClient, config: configs.SubPoolConfig) = {
    println("Config file loaded.")
    println("Please ensure your wallet details in the config are correct before performing any commands!")

    println("Enter \"withdraw\", \"distribute\", or \"exit\":")
    Iterator.continually(scala.io.StdIn.readLine)
      .takeWhile(_ != "done")
      .foreach{ str:String =>
        str match {
          case "withdraw" => try{withdraw(ergoClient, config)}catch {
            case err: Throwable => println("Error: There was an issue trying to withdraw! Have you been paid out yet?")
              println(" ErrorVal: " + err.getMessage)
              println(" StackTrace: " + err.printStackTrace())
          }
          case "distribute" => try{distribute(ergoClient, config)}catch {
            case err: Throwable => println("Error: There was an issue trying to sign the distribution transaction. Have all miner's sent their withdrawal request?")
              println(" ErrorVal: " + err.getMessage)
              println(" StackTrace: " + err.printStackTrace())
          }
          case "exit" => sys.exit(0)
          case _: String =>
        }
      }
  }

  def withdraw(ergoClient: ErgoClient, config: configs.SubPoolConfig) = {
    var workerShareList = Array.empty[Long]
    var totalShares = 0L

    // Add options for different pool requests later
    try {
      val httpReq = requestFromEnigmaPool(config)
      workerShareList = httpReq._1
      totalShares = httpReq._2
    }catch {
      case err:ArrayIndexOutOfBoundsException => println("There was an error accessing information from your mining pool! Are your worker names correct?")
        sys.exit(1)
      case err:Throwable => println("There was an unknown error while accessing information from your mining pool!")
        sys.exit(1)
    }

    ergoClient.execute(ctx => {
      val prover: ErgoProver = ctx.newProverBuilder
        .withMnemonic(
          SecretString.create(config.getNode.getWallet.getMnemonic),
          SecretString.create(config.getNode.getWallet.getPassword))
        .build()
      val holdingAddress = Address.create(config.getParameters.getHoldingAddress)

      val minerAddressList = config.getParameters.getMinerAddressList.map(Address.create)
      val consensusAmnt: Long = ((config.getParameters.getMinimumPayout*Parameters.OneErg)/minerAddressList.size).toLong
      val consensusContract = ConsensusStageHelpers.generateConsensusContract(ctx, minerAddressList.toList, (config.getParameters.getMinimumPayout * Parameters.OneErg).toLong)

      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
      val tokenList = List.empty[ErgoToken].asJava
      val holdingBoxList: java.util.List[InputBox] = ctx.getCoveringBoxesFor(holdingAddress, consensusAmnt + Parameters.MinFee, tokenList).getBoxes
      val registerList = generateHoldingOutputRegisterList(minerAddressList.toList, workerShareList.toList, totalShares, prover.getAddress, config.getParameters.getWorkerName)

      val holdingOutBox: OutBox = txB.outBoxBuilder
        .value(consensusAmnt)
        .contract(consensusContract)
        .registers(registerList: _*)
        .build()

      val tx: UnsignedTransaction = txB
        .boxesToSpend(holdingBoxList)
        .outputs(holdingOutBox)
        .fee(Parameters.MinFee)
        .sendChangeTo(holdingAddress.getErgoAddress)
        .build()
      val signed: SignedTransaction = prover.sign(tx)
      val txId: String = ctx.sendTransaction(signed)
    })
  }

  def distribute(ergoClient: ErgoClient, config: SubPoolConfig) = {

    ergoClient.execute(ctx => {
      val prover: ErgoProver = ctx.newProverBuilder
        .withMnemonic(
          SecretString.create(config.getNode.getWallet.getMnemonic),
          SecretString.create(config.getNode.getWallet.getPassword))
        .build()

      val minerAddressList = config.getParameters.getMinerAddressList.map(Address.create)
      val consensusAmnt: Long = ((config.getParameters.getMinimumPayout*Parameters.OneErg)/minerAddressList.size).toLong

      // Protection contract for consensus box
      val consensusContract = generateConsensusContract(ctx, minerAddressList.toList, consensusAmnt)
      val consensusAddress = contracts.generateContractAddress(consensusContract, config.getNode.getNetworkType)

      // Create unsigned transaction builder
      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
      val consensusBoxList = ctx.getUnspentBoxesFor(consensusAddress, 0, 20)
      val consensusInputBoxes = findValidInputBoxes(ctx, minerAddressList.toList, consensusBoxList.asScala.toList)


      val totalValue: Long = consensusInputBoxes.foldLeft(0L) { (accum: Long, box: InputBox) => accum + box.getValue }
      val avgTotalShares: Long = consensusInputBoxes.foldLeft(0L) { (accum: Long, box: InputBox) => accum + box.getRegisters.get(1).getValue.asInstanceOf[Long] } / (consensusInputBoxes.size * 1L)
      val minerPKs = minerAddressList.map(contracts.genSigProp)

      val consensusResult = buildConsensusFromBoxes(consensusInputBoxes.asJava, minerPKs.toList)
      val consensusOutBoxList = buildOutputsFromConsensus(ctx, consensusResult, minerAddressList.toList, avgTotalShares, totalValue)

      val tx: UnsignedTransaction = txB
        .boxesToSpend(consensusInputBoxes.asJava)
        .outputs(consensusOutBoxList: _*)
        .fee(Parameters.MinFee)
        .sendChangeTo(consensusAddress.getErgoAddress)
        .build()

      // Sign transaction of mining pool to holding box
      val signed: SignedTransaction = prover.sign(tx)
      val txId: String = ctx.sendTransaction(signed)
    })
  }

  // Send Get Request To Enigma Pool, format responses into useable values
  def requestFromEnigmaPool(config: SubPoolConfig) = {

    val addrStr = config.getParameters.getHoldingAddress
    val httpClient = new OkHttpClient()

    val reqTotalShares = new Request.Builder().url(s"https://api.enigmapool.com/shares/${addrStr}").build()
    val respTotalShares = httpClient.newCall(reqTotalShares).execute()
    val respString1 = respTotalShares.body().string()
    //println(respString1)
    val totalShares = respString1.split(":")(2).split(",")(0).toLong


    val reqWorkerShares = new Request.Builder().url(s"https://api.enigmapool.com/workers/${addrStr}").build()
    val respWorkerShares = httpClient.newCall(reqWorkerShares).execute()
    val respString2 = respWorkerShares.body().string()
    //println(respString2)
    def getWorkerShareNumber(workerName: String) = {
      val searchString = workerName + "\",\"shares\":"
      val startingIdx = respString2.indexOf(searchString) + searchString.length
      val slicedStr = respString2.substring(startingIdx).split(",")(0).split("}")(0)
      slicedStr.toLong
    }
    //val workerShareList = getWorkerShareNumber("testWorker")
    val workerShareList = config.getParameters.getWorkerList.map(getWorkerShareNumber)
    println(totalShares)
    // println(workerShareList.mkString("Array(", ", ", ")"))
    (workerShareList, totalShares)
  }
}
