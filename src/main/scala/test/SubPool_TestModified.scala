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
    val holdingAddress = Address.create("qCxCcrh1uWKnwwYLn6eUhxDz9aAnyaem1DW9uf2mCW5y58L3zPBrbtvRrpHY297sxzz3ERcJebYBAMGUomecv6a9whhGaf6gpSg7aNCcAXY7hFqSdN7u3beUMyceDcKZuW25f6UDTqAEQNzCAYhtpUwehpJE8KeVK8miocm8CTgP8dDdj7YsVp7vhkjCVFR8LocA2EF3m7X5uihiqKRZCeEjtdigunnBE6uTymRQZA8GCuH9Rpy5UFQQNxALN6CGsV5uTkQ4HsoYKTNpx3uZA9huDyvAsq7Byo6q8dNHMu11qt9joZR7C7CF1P3pkJfYJMdXxynKkWUwv9zMFsyyR3vZYyZ8ipwxLRBSMtexdRdUhcBXgbaC7FoHcFVGfbiAKPXbTS16rXR6PbTrJEQHUDjPDckudgYXcLmxGec4a3AjQbofBjmPk3a5LEDEeo2c1ZaMjwxHUoX4X32VMvMjkBQ99jhnucnJgdX61RaVfGKyJqGRA21F4uFpDnLT4juh9pqKjssujJuvgpz1EfCtx5cAX9vnGm4jcRW6wq9tm5nPu1vt7c4aV1R7dYqQ7BTbYVqKFYURQvssxfRS7m6Z1FMda7twTwZ3CuXXPHFSFNDAMbCR9knBjMWD5p4xFrtGFxatS1aZVE62MLo7Eu6TDUtzjnwrQKdAm4q6vVEiiZ9gSbPnfLnh74PA1KKJVygyvW3DkJZqknsgsRqGaQBWj95MqK2LQmPBwVGvS6pF7JPrGw9n325tC1hu7wGEBMvV6PHQnHN6hdwCm2vch8L3tM3HLVCEdYBQWdFDebaC5xDCSBX1tr7BeAKE9hwG9p8hbSZhjFdN1ZQyAFLB4uugM7itzXqzCKyBfgRuHtuciiYWtUyxFsE1dvFdzQLcn9avzG7atPxHRTvCHMAvLb64TqjFaLtNBFqouHE7qNMMVxK15ZgmVqj3PRKgrS8btUYUc3qcHcCiR3hxDHPy5QjJDNZqUAULw1gDXoSphdb7FDg3gW5hvszkJiepJhcuo6KD5RyBHH3zkfqTGYiRx1rB8vBoAe2SkCqEPrHQE251GKbab7AUw56HMxU7DjKH6ALd9RMyn61crqFjf7EUrNbxHzX6BL6NukTvoXpBjh84Vqotoet5GrreQEM74mFXDo5XjRJLGNoY1X9Td24SyiXTjW3gEQ3nYMJSVvws6MCjgkgAwj1Q1CGbXJW87ckYZdVhD8acaNcXPhtbtg9eaDxTtWZrqeBVQEPV46BvfJQig8B4kMnSigfMMqSi2N3AAXz5pmiccarj1M8CJeWdEwnoXg4mS7aapaTLVC1MtdtaYNe81Zbp4ZTsuF2JFnRN6f2knDQ6ZR7qT9t3RYNdgxTLEMxAek8eNYJTQUgFrauFLAVPbLiuL2bsw1FLghxfSjCRpV615Bxq9meMRFLt1kncHwzhVNHdsLpDzKNFUkEebd9LqMwD2Rt8K2n8iSqvHhQAmubrYjUwoENXn7B2Vnwy4NgDZGnWQsBhT61vwdMzMk2TiYTLMjy2rpqN2RNwzxG2CmRKUF7gbSmS3UpNQfL7PuBXfwbku7TJYknk8XwVnstpvVHSLraS7jng91XtdJDyoZCJCtb1ZF7K2rA91CuTX9vzp8GNgcXDGtafMNvEYQccMLihhLVNvuB1YC5jeXn5V9RBEswyzWTqZjvN3nbJGT2Ga4Fskjn3Ep9miCzRZaxs5HfHnmu4Ahg65ZKStrdhrWAqgDQwRSzyeL5Ar9wRDaa7BqyuYVcv2mYdw69Q1F1pesCGuCQHCm299EBc84WCMorsJFg1Z3wj3r6gTFQ44")

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