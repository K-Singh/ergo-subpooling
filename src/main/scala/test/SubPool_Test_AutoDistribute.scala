package test

import configs.{SubPoolConfig, SubPoolNodeConfig}
import contracts.AutomaticDistributionHelpers._
import contracts.HoldingStageHelpers._
import contracts._
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoNodeConfig

import scala.collection.JavaConverters._

object SubPool_Test_AutoDistribute {
  /*
  * This file represents a test case for the SubPool contract.
  * A mining pool will send rewards to a SubPool contract P2S address.
  * The contract will go through all stages until both miner's have been paid
  * proportional to the shares they submitted.
  * */

  private final val poolSendingValue = Parameters.OneErg * 3L
  private final val hrList = Array(120L, 150L, 200L)

  private final val miningPoolString = SecretString.create("ThisIsTheMiningPoolForTestingNumber1")
  private final val miner1String = SecretString.create("This is Miner 1")
  private final val miner2String = SecretString.create("This is Miner 2")
  private final val miner3String = SecretString.create("This is Miner 2")

  private final val miningPoolAddress = Address.createEip3Address(0,NetworkType.TESTNET, miningPoolString, SecretString.create(""))
  private final val miner1Address = Address.createEip3Address(0, NetworkType.TESTNET, miner1String, SecretString.create(""))
  private final val miner2Address = Address.createEip3Address(0, NetworkType.TESTNET, miner2String, SecretString.create(""))
  private final val miner3Address = Address.createEip3Address(0, NetworkType.TESTNET, miner3String, SecretString.create(""))
  private final val minerAddressList = List(miner1Address, miner2Address, miner3Address)



  def miningPoolToADBoxTx(ctx: BlockchainContext, miningPool: SecretString): String = {
    System.out.println(miningPoolAddress)
    //Initialize Provers
    val poolProver: ErgoProver = ctx.newProverBuilder.withMnemonic(miningPool, SecretString.create("")).withEip3Secret(0).build()
    // Get input boxes of mining pool
    val tokenList = List.empty[ErgoToken].asJava
    val miningPoolBoxes: java.util.List[InputBox] = ctx.getCoveringBoxesFor(miningPoolAddress, poolSendingValue + Parameters.MinFee, tokenList).getBoxes
    // Protection contract for AD box
    val autoDistContract = generateADContract(ctx, minerAddressList, hrList)
    val autoDistAddress = generateContractAddress(autoDistContract, NetworkType.TESTNET)

    System.out.println(autoDistAddress)

    // Create unsigned transaction builder
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    // Create output holding box
    val holdingBox: OutBox = txB.outBoxBuilder
      .value(poolSendingValue)
      .contract(autoDistContract)
      .build()
    // Create unsigned transaction
    val tx: UnsignedTransaction = txB
      .boxesToSpend(miningPoolBoxes)
      .outputs(holdingBox)
      .fee(Parameters.MinFee)
      .sendChangeTo(miningPoolAddress.getErgoAddress)
      .build()

    // Sign transaction of mining pool to holding box
    val signed: SignedTransaction = poolProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed)
    // Return transaction as JSON string
    signed.toJson(true)
  }

  def autoDistToMinersTx(ctx: BlockchainContext, miner1: SecretString, miner2: SecretString): String = {

    //Initialize Provers
    val miner1Prover: ErgoProver = ctx.newProverBuilder.withMnemonic(miner1, SecretString.create("")).withEip3Secret(0).build()
    val miner2Prover: ErgoProver = ctx.newProverBuilder.withMnemonic(miner2, SecretString.create("")).withEip3Secret(0).build()

    // Protection contract for consensus box
    val autoDistContract = generateADContract(ctx, minerAddressList, hrList)
    val autoDistAddress = contracts.generateContractAddress(autoDistContract, NetworkType.TESTNET)

    System.out.println(autoDistAddress)
    // Create unsigned transaction builder
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val tokenList = List.empty[ErgoToken].asJava
    val autoDistInputBoxList =  ctx.getUnspentBoxesFor(autoDistAddress, 0, 20)

    autoDistInputBoxList.asScala.foreach((box: InputBox) => println(box.toJson(true)))

    val autoDistOutBoxList = buildOutputsForAD(ctx, autoDistInputBoxList, minerAddressList, hrList)

    System.out.println(autoDistInputBoxList)
    val tx: UnsignedTransaction = txB
      .boxesToSpend(autoDistInputBoxList)
      .outputs(autoDistOutBoxList: _*)
      .fee(Parameters.MinFee)
      .sendChangeTo(autoDistAddress.getErgoAddress)
      .build()

    txB.getInputBoxes.forEach(x => System.out.println("Hi! \n" + x.toJson(true)))
    // Sign transaction of mining pool to holding box
    val signed: SignedTransaction = miner1Prover.sign(tx)
    //val signed: SignedTransaction = miner2Prover.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed)

    // Return transaction as JSON string
    signed.toJson(true)

  }


  def main(args: Array[String]): Unit = {

    // Node configuration values
    val conf: SubPoolConfig = SubPoolConfig.load("test_config.json")

    val nodeConf: SubPoolNodeConfig = conf.getNode
    val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(NetworkType.TESTNET)

    // Create ErgoClient instance (represents connection to node)
    val ergoClient: ErgoClient = RestApiErgoClient.create(nodeConf.getNodeApi.getApiUrl, nodeConf.getNetworkType, nodeConf.getNodeApi.getApiKey, explorerUrl)

    // Execute transaction
    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {
      //miningPoolToADBoxTx(ctx, miningPoolString)
      autoDistToMinersTx(ctx, miner1String, miner2String)
    })
      //val txJson: String = sendTx("subpool_config.json")
      System.out.println(txJson)
    }

}