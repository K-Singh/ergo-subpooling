package test

import configs.{SubPoolConfig, SubPoolNodeConfig}
import contracts._
import org.ergoplatform.appkit._
import contracts.VotingContract._
import contracts.HoldingContract._
import contracts.MetadataContract._
import scala.collection.JavaConverters._

object SubPool_TestModified {
  /*
  * This file represents a test case for the SubPool contract.
  * A mining pool will send rewards to a SubPool contract P2S address.
  * The contract will go through all stages until both miner's have been paid
  * proportional to the shares they submitted.
  * */

  final val poolSendingValue = Parameters.OneErg * 3L
  final val initiationHeight = 100
  final val maxVotingHeight = 200
  final val skipProtocol = 0

  final val miningPoolString = SecretString.create("ThisIsTheMiningPoolForTestingNumber1")
  final val miner1String = SecretString.create("This is Miner 1")
  final val miner2String = SecretString.create("This is Miner 2")

  final val miningPoolAddress = Address.createEip3Address(0,NetworkType.TESTNET, miningPoolString, SecretString.create(""))
  final val miner1Address = Address.createEip3Address(0, NetworkType.TESTNET, miner1String, SecretString.create(""))
  final val miner2Address = Address.createEip3Address(0, NetworkType.TESTNET, miner2String, SecretString.create(""))
  final val minerAddressList = Array(miner1Address, miner2Address)
  final val workerName1 = "Worker1"
  final val workerName2 = "Worker2"
  final val workerList = Array(workerName1, workerName2)
  final val memberList = minerAddressList.zip(workerList)


  def miningPoolToHoldingBoxTx(ctx: BlockchainContext, miningPool: SecretString): String = {
    System.out.println(miningPoolAddress)
    //Initialize Provers
    val poolProver: ErgoProver = ctx.newProverBuilder.withMnemonic(miningPool, SecretString.create("")).withEip3Secret(0).build()
    // Get input boxes of mining pool
    val tokenList = List.empty[ErgoToken].asJava
    val miningPoolBoxes: java.util.List[InputBox] = ctx.getCoveringBoxesFor(miningPoolAddress, poolSendingValue + Parameters.MinFee, tokenList).getBoxes
    // Protection contract for holding box
    val metadataContract = generateMetadataContract(ctx, memberList)
    val metadataAddress = generateContractAddress(metadataContract, NetworkType.TESTNET)
    val consensusContract = generateVotingContract(ctx, memberList, maxVotingHeight, skipProtocol, metadataAddress)
    val consensusAddress = generateContractAddress(consensusContract, NetworkType.TESTNET)
    val holdingContract = generateHoldingContract(ctx, memberList, maxVotingHeight, initiationHeight, consensusAddress, metadataAddress)

    // Create address for holding box, important so that change can be sent back to box.
    val holdingAddress = contracts.generateContractAddress(holdingContract, NetworkType.TESTNET)

    System.out.println(holdingAddress)

    // Create unsigned transaction builder
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    // Create output holding box
    val holdingBox: OutBox = txB.outBoxBuilder
      .value(poolSendingValue)
      .contract(holdingContract)
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

//  def holdingBoxToConsensusTx(ctx: BlockchainContext, miner1: SecretString, miner2: SecretString): String = {
//    //Initialize Provers
//    val miner1Prover: ErgoProver = ctx.newProverBuilder.withMnemonic(miner1, SecretString.create("")).withEip3Secret(0).build()
//    val miner2Prover: ErgoProver = ctx.newProverBuilder.withMnemonic(miner2, SecretString.create("")).withEip3Secret(0).build()
//
//
//    val amountToSend: Long = consensusValue - (Parameters.MinFee)
//    // Protection contract for consensus box
//    val consensusContract = generateConsensusContract(ctx, minerAddressList, consensusValue)
//    val consensusAddress = generateContractAddress(consensusContract, NetworkType.TESTNET)
//    val holdingContract = generateHoldingContract(ctx, minerAddressList, holdingContractConstant, consensusAddress)
//    // Create address for holding box, important so that change can be sent back to box.
//    val holdingAddress = contracts.generateContractAddress(holdingContract, NetworkType.TESTNET)
//    System.out.println(holdingAddress)
//
//
//    // Create unsigned transaction builder
//    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
//    val txB2: UnsignedTransactionBuilder = ctx.newTxBuilder
//
//    val tokenList = List.empty[ErgoToken].asJava
//    val holdingBoxList: java.util.List[InputBox] = ctx.getCoveringBoxesFor(holdingAddress, consensusValue + Parameters.MinFee, tokenList).getBoxes
//    holdingBoxList.forEach({(box:InputBox) => println(box.toJson(true))})
//
//    //Sample values
//    val sampleShareList1 = List(200L, 100L)
//    val sampleShareList2 = List(300L, 130L)
//    val registerList1 = generateHoldingOutputRegisterList(minerAddressList, sampleShareList1, 300L, miner1Address, workerName1)
//    val registerList2 = generateHoldingOutputRegisterList(minerAddressList, sampleShareList2, 430L, miner2Address, workerName2)
//
//    val holdingOutBox1: OutBox = txB.outBoxBuilder
//      .value(amountToSend)
//      .contract(consensusContract)
//      .registers(registerList1: _*)
//      .build()
//
//    val holdingOutBox2: OutBox = txB2.outBoxBuilder
//      .value(amountToSend)
//      .contract(consensusContract)
//      .registers(registerList2: _*)
//      .build()
//
//
//    val tx: UnsignedTransaction = txB
//      .boxesToSpend(holdingBoxList)
//      .outputs(holdingOutBox1)
//      .fee(Parameters.MinFee)
//      .sendChangeTo(holdingAddress.getErgoAddress)
//      .build()
//
//    val tx2: UnsignedTransaction = txB2
//      .boxesToSpend(holdingBoxList)
//      .outputs(holdingOutBox2)
//      .fee(Parameters.MinFee)
//
//      .sendChangeTo(holdingAddress.getErgoAddress)
//      .build()
//
//    txB.getInputBoxes.forEach(x => System.out.println("Hi! \n" + x.toJson(true)))
//    // Sign transaction of mining pool to holding box
//    //val signed: SignedTransaction = miner1Prover.sign(tx)
//    val signed: SignedTransaction = miner2Prover.sign(tx2)
//    // Submit transaction to node
//    val txId: String = ctx.sendTransaction(signed)
//
//    // Return transaction as JSON string
//    signed.toJson(true)
//
//  }

//  def consensusToMinersTx(ctx: BlockchainContext, miner1: SecretString, miner2: SecretString): String = {
//
//    //Initialize Provers
//    val miner1Prover: ErgoProver = ctx.newProverBuilder.withMnemonic(miner1, SecretString.create("")).withEip3Secret(0).build()
//    val miner2Prover: ErgoProver = ctx.newProverBuilder.withMnemonic(miner2, SecretString.create("")).withEip3Secret(0).build()
//
//    // Protection contract for consensus box
//    val consensusContract = generateConsensusContract(ctx, minerAddressList, consensusValue)
//    val consensusAddress = contracts.generateContractAddress(consensusContract, NetworkType.TESTNET)
//
//    System.out.println(consensusAddress)
//    // Create unsigned transaction builder
//    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
//    val tokenList = List.empty[ErgoToken].asJava
//    val consensusBoxList =  ctx.getUnspentBoxesFor(consensusAddress, 0, 20)
//    val consensusInputBoxes = findValidInputBoxes(ctx, minerAddressList, consensusBoxList.asScala.toList)
//
//    consensusInputBoxes.foreach((box: InputBox) => println(box.toJson(true)))
//
//    val totalValue: Long = consensusInputBoxes.foldLeft(0L){(accum:Long, box: InputBox) => accum + box.getValue}
//    val avgTotalShares : Long = consensusInputBoxes.foldLeft(0L){(accum : Long, box: InputBox) => accum + box.getRegisters.get(1).getValue.asInstanceOf[Long]} / (consensusInputBoxes.size * 1L)
//    val minerPKs =  minerAddressList.map(contracts.genSigProp)
//    println(totalValue)
//    println(avgTotalShares)
//    println(minerPKs)
//    val consensusResult = buildConsensusFromBoxes(consensusInputBoxes.asJava, minerPKs)
//    val consensusOutBoxList = buildOutputsFromConsensus(ctx, consensusResult, minerAddressList, avgTotalShares, totalValue)
//    System.out.println(consensusResult)
//    System.out.println(consensusOutBoxList)
//    val tx: UnsignedTransaction = txB
//      .boxesToSpend(consensusInputBoxes.asJava)
//      .outputs(consensusOutBoxList: _*)
//      .fee(Parameters.MinFee)
//      .sendChangeTo(consensusAddress.getErgoAddress)
//      .build()
//
//    txB.getInputBoxes.forEach(x => System.out.println("Hi! \n" + x.toJson(true)))
//    // Sign transaction of mining pool to holding box
//    val signed: SignedTransaction = miner1Prover.sign(tx)
//
//    // Submit transaction to node
//    val txId: String = ctx.sendTransaction(signed)
//
//    // Return transaction as JSON string
//    signed.toJson(true)
//
//  }


  def main(args: Array[String]): Unit = {

    // Node configuration values
    val conf: SubPoolConfig = SubPoolConfig.load("test_config.json")

    val nodeConf: SubPoolNodeConfig = conf.getNode
    val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(NetworkType.TESTNET)

    // Create ErgoClient instance (represents connection to node)
    val ergoClient: ErgoClient = RestApiErgoClient.create(nodeConf.getNodeApi.getApiUrl, nodeConf.getNetworkType, nodeConf.getNodeApi.getApiKey, explorerUrl)

    // Execute transaction
    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {
      miningPoolToHoldingBoxTx(ctx, miningPoolString)
      //holdingBoxToConsensusTx(ctx, miner1String, miner2String)
      //consensusToMinersTx(ctx, miner1String, miner2String)
    })
      //val txJson: String = sendTx("subpool_config.json")
      System.out.println(txJson)
    }

}