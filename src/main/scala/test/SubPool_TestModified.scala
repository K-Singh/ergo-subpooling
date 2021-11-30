package test

import configs.{SubPoolConfig, SubPoolNodeConfig}
import contracts._
import org.ergoplatform.appkit._
import contracts.VotingContract._
import contracts.HoldingContract._
import contracts.MetadataContract._
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.impl.ErgoTreeContract

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

object SubPool_TestModified {
  /*
  * This file represents a test case for the SubPool contract.
  * A mining pool will send rewards to a SubPool contract P2S address.
  * The contract will go through all stages until both miner's have been paid
  * proportional to the shares they submitted.
  * */

  final val poolSendingValue = Parameters.OneErg * 2L
  final val initiationHeight = 100
  final val maxVotingHeight = 200
  final val skipProtocol = 0

  final val miningPoolString = SecretString.create("This Is The Mining Pool For Testing3")
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

  final val miner1ShareList = Array(100L, 200L)
  final val miner2ShareList = Array(200L, 400L)
  final val miner1Votes = Array(200, 0)
  final val miner2Votes = Array(300, 0)
  val p2sAddressList = ArrayBuffer.empty[Address]
  val contractList = ArrayBuffer.empty[ErgoContract]

  def generateInitialContracts(ctx: BlockchainContext) = {
    val metadataContract = generateMetadataContract(ctx, memberList)
    val metadataAddress = generateContractAddress(metadataContract, NetworkType.TESTNET)
    val votingContract = generateVotingContract(ctx, memberList, maxVotingHeight, skipProtocol, metadataAddress)
    val votingAddress = generateContractAddress(votingContract, NetworkType.TESTNET)
    val holdingContract = generateHoldingContract(ctx, memberList, maxVotingHeight, initiationHeight, votingAddress, metadataAddress)
    val holdingAddress = contracts.generateContractAddress(holdingContract, NetworkType.TESTNET)
    println("\nmetaData Addr: " + metadataAddress)
    println("\nvoting Addr: " + votingAddress)
    println("\nholding Addr: " + holdingAddress)
    println("\nminingPoolAddress: " + miningPoolAddress)
    p2sAddressList.append(metadataAddress, votingAddress, holdingAddress)
    contractList.append(metadataContract, votingContract, holdingContract)
  }
  def loadInitialAddresses = {

    val metadataAddress = Address.create("9zQwVBm97AGiNeTF521yZYWjmmZBQuT7nBcKKUrRuWvFZCxdCb9329Bcut9isrtWsay6m5rhgYEGqDGCfSMQcrSXvMF53MayyPXgJjGot4XM5kx8aWGuyn9s3kH257vw1pEaHpM9pbV1v8ApNFVMnNt3qqP1bbB3HtwpHRc1XTKKgjPLTCEzEdwzMNCjKGebKFcZEeeH3sntKxtRj9ALSLpaMzcSwXqTrW1WhgmWUQCGJD8uLi7rqqgoDRAnxLT3pSKdRBPvroPMK3u214PKR1e8Dd7zCf5fJaDuwV4PQpHmWvRYFdgcUksjZEFBb6E5QkDE8nFS5QdVU6Z8s9BrhhZY6dNh2BZHSTaEWFnxfJgRRcpAXu87Cwz5FeWU24kJw8Rxjb5RjCzNebH7ikQopBkpi7oDE3NHhp5aaTjWfSroAVeBgpBsGdby8vcaqRKrSbT9u2jfpshtVQ1fo7Z42LbqaZJJTj7VB89MkjBZmqMC35PpbzdjrKwZd4Z9citPiV6h8pC8ZqXkfqk9q3KxZ7hcZYYZk2SVgG7RhotPWE2rNSvH49Xko3W64mrgc3K48VQGLhruGpLaQk4RxeUpUyXA519TZX7dPnigB")
    val votingAddress = Address.create("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")
    val holdingAddress = Address.create("qCxCcrh1uWKnwwYLn6eUhxDz9aAnyaem1DW9uf2mCW5y58L3zPBrbtvRrpHY297sxamUSWheT7qhkb149F2YSi6qpTbsjWhLR9TvZzZ5q2oEZV6J61PH2ENu2MrnTV74jx9NWwHPaHdcAHKBmTyqoaJ7GYG9ayeB6ErdFEHPRBUeNtVo2NShgZaBBiFihdVUBxHmhy1zeBB1PR51sK8VjJ5GFnP3K3cW6fR52qCqMADMBmKpZqoamhWpT1QxP9FHDy1ZPJwrdEVd8t54q2fqBVMNwEFatuZxCFbUT4MmD4NTUMBVB464HyWnZaHFh8ZXWNwHMr5fUDocZbM6cDFUbf6uP5H4CrA4Rff2TYkT71bNzjG3ga3KgKr6Jjumvs8Z7ek2UVBJgcV2osWbMJtJH7yFPhyssgG6otojWByyoBCHu4vanDNk96wH2aoLfpHLA27eXbXS7SyKaB11JuoMNfKB6zYDoEHub5AxhKoFvKQXeVTPGdwJ3WspRdoZsCg3VqJRHgjNViUAraV9cbwppyKqWFnDLS19m7zYMXRkvJYmVSYezZfBUeeWNUwD7gAph3o7stkLvKhin1Dtdk7CcX6HoqrH1ahenCV7JLBSmb2mmTLe26NSgbskMBNEtYNLbipWJkkFMfSxJXMxcRrwtXwUf4QyxUYNBDTmRX3JYXmu4D2cXSbbbq4d3t2ezE5EobkHazsi9f4NLq8Ap6Q4xzBbYeWAmsq8QACjQyzWX5VwiqEUYmMeWLrTWapPmrYFoEQWNuNkyf1Cjn5aBJ8i6VYmKG7XkoSSPTHmP1gPMZshjbAU39QyiQRqT1mDT4dHPuPvXZY49aRfHu59BvwgLLZmt7y1sQLFem2MBaxbdGc1vQfLuTzbd442h6DDC8q1HFu55K5cbwxGAdWZ6LYeovmBiEnSZWKrjvyCcvUkyXQRhamcd6jf1PjbRiYCYg1GhS84hMxQF7e1gahi9MzymuyiXWSC5ytUUmU3buzk4k6mfVWXuBGHNGKnGKFL6sAE7XjotkwUhXtjGTHRNxDqnxFkteKEMiUN58RoFTVf8Dff9DMNwkEMyehrrYzdyh56h1EHQ9EhcKKQTpzWc7dEt8R6QASDfhX6LkyMvkpnR4xDs24nTQmLVnEyoyBr14hJdQWQeYfVc9fzGF9TUVvUgeZdNebMfyc4p21nZVQzknGD9SQ6KRdH5d8mF6yXAu7BnYDky5UmWyiHKzuD7v6YHgfRtP1DJSL8kkP4bPiDCHyU2dbetoaWtqiCojgkwrwD4ZdQq8f2PXUDo6J6PQDmrpgH7g8Ede6aQjC6b37mhT3dyTsHKgoN477XRGqivUJREouYeG4DA84BxAZ3GY3w6Sga32KYVzXLjr66PwPWZNXgELMaQmntEvvhJqxVdWnNxiaH4enX5Drweo6Y2Gc6R6VkcEtkToEqLwEMZyTtRT5H4fAxPiZm9mPT7mQWnq1kukTMDnbdK9X3BhGHd7dqaGTqTNr2amMHqf9uT7az5JRD3HzuqYPSwQ9fK4Aa7bDA2JHrrbD7UqTr64dm8MpHQwSa2gd8EJULLp23ZuMzjz2fS6fKE1DGWGwexNfYKQKFQDYhvCRvpfrsxhb6QLwwVWNgZEtQEjEE1es1aH2oYyZCSuv4omKMjNE78sSQjDh6q5aeH8z594vKj4iRfqCWff57bRt1ETitqzL9ZZfq23gd7d5f4fUsZMANjULPU2CbJm7wLmsQG45aog2cZRvEiEyz4ZBrEJUSH3AteYcrQrurBCLhgyeowSEsQDXBqStgBQVnKigeJKAFFd2PFSnMm")

    println("\nmetaData Addr: " + metadataAddress)
    println("\nvoting Addr: " + votingAddress)
    println("\nholding Addr: " + holdingAddress)
    println("\nminingPoolAddress: " + miningPoolAddress)
    val metadataContract = new ErgoTreeContract(metadataAddress.getErgoAddress.script)
    val votingContract = new ErgoTreeContract(votingAddress.getErgoAddress.script)
    val holdingContract = new ErgoTreeContract(holdingAddress.getErgoAddress.script)
    println(holdingContract.getErgoTree)
    p2sAddressList.append(metadataAddress, votingAddress, holdingAddress)
    contractList.append(metadataContract, votingContract, holdingContract)
  }

  def metadataAddress = p2sAddressList(0)
  def votingAddress = p2sAddressList(1)
  def holdingAddress = p2sAddressList(2)
  def metadataContract = contractList(0)
  def votingContract = contractList(1)
  def holdingContract = contractList(2)


  def miningPoolToHoldingBoxTx(ctx: BlockchainContext, miningPool: SecretString): String = {
    //System.out.println(miningPoolAddress)
    //Initialize Provers
    val poolProver: ErgoProver = ctx.newProverBuilder.withMnemonic(miningPool, SecretString.create("")).withEip3Secret(0).build()
    // Get input boxes of mining pool
    val tokenList = List.empty[ErgoToken].asJava
    val miningPoolBoxes: java.util.List[InputBox] = ctx.getCoveringBoxesFor(miningPoolAddress, poolSendingValue + Parameters.MinFee, tokenList).getBoxes
    // Protection contract for holding box

   // System.out.println(holdingAddress)

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
  // Send a test voting transaction
  def VoteTx(ctx: BlockchainContext, minerString: SecretString, minerAddress: Address, minerShareArray: Array[Long], minerVoteArray: Array[Int]): String = {
    //Initialize Provers
    val minerProver: ErgoProver = ctx.newProverBuilder.withMnemonic(minerString, SecretString.create("")).withEip3Secret(0).build()

    // Create address for holding box, important so that change can be sent back to box.
    //println(holdingAddress)
    val unspentHoldingBoxes = ctx.getUnspentBoxesFor(holdingAddress, 0, 20)
    unspentHoldingBoxes.forEach{
      (box: InputBox) =>
        println("UTXO: " + box.getValue)
    }
    // Gets boxes to use for tx, may be either 1 redeposit or any number of normal holding boxes
    val boxesToSpend = getHoldingBoxesForVotes(unspentHoldingBoxes.asScala.toList)

    val totalValue: Long = boxesToSpend.foldLeft(0L){
      (accum: Long, box:InputBox) => accum + box.getValue
    }
    val votingValue = getVotingValue(totalValue, memberList.length)
    val outputBoxes = generateVotingBoxes(ctx, boxesToSpend, minerAddressList, minerShareArray, workerList, minerAddress,
                                          minerVoteArray, metadataAddress, votingAddress)
    val redepositBox = generateRedepositHoldingBoxes(ctx, boxesToSpend, minerAddress, minerAddressList, holdingAddress)
    outputBoxes.append(redepositBox:_*)

    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val tx: UnsignedTransaction = txB
          .boxesToSpend(boxesToSpend.asJava)
          .outputs(outputBoxes:_*)
          .fee(Parameters.MinFee)
          .sendChangeTo(holdingAddress.getErgoAddress)
          .build()
    val signed: SignedTransaction = minerProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed)

    // Return transaction as JSON string
    signed.toJson(true)

  }

  def ConsensusTx(ctx: BlockchainContext, minerString: SecretString): String = {
    //Initialize Provers
    val minerProver: ErgoProver = ctx.newProverBuilder.withMnemonic(minerString, SecretString.create("")).withEip3Secret(0).build()

    // Create address for holding box, important so that change can be sent back to box.
    //println(holdingAddress)
    val unspentVotingBoxes = ctx.getUnspentBoxesFor(votingAddress, 0, 20)
    val boxesToSpend = findValidInputBoxes(ctx, minerAddressList.toList, unspentVotingBoxes.asScala.toList)
    val minerBoxes = buildConsensusOutputs(ctx, boxesToSpend, memberList.toList, metadataAddress, maxVotingHeight, skipProtocol)

    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val tx: UnsignedTransaction = txB
      .boxesToSpend(boxesToSpend.asJava)
      .outputs(minerBoxes:_*)
      .fee(Parameters.MinFee)
      .sendChangeTo(holdingAddress.getErgoAddress)
      .build()
    val signed: SignedTransaction = minerProver.sign(tx)
    // Submit transaction to node
    val txId: String = ctx.sendTransaction(signed)

    // Return transaction as JSON string
    signed.toJson(true)

  }
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
      //generateInitialContracts(ctx)
      //miningPoolToHoldingBoxTx(ctx, miningPoolString)
      //loadInitialAddresses
      //VoteTx(ctx, miner1String, miner1Address, miner1ShareList, miner1Votes)
      //loadInitialAddresses
      //VoteTx(ctx, miner2String, miner2Address, miner2ShareList, miner2Votes)
      loadInitialAddresses
      ConsensusTx(ctx, miner1String)

    })
      //val txJson: String = sendTx("subpool_config.json")
      System.out.println(txJson)
    }

}