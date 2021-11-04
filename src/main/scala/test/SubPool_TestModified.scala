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

  final val miningPoolString = SecretString.create("This Is The Mining Pool For Testing2")
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
    val holdingAddress = Address.create("KFJpDCrXBQqLZxdAMjE3tdfwX76nsVraEoeNWu7AEYJhJp4899tMGRTZgTNd4QGh1v8PkfQjbtEe4M3nv7zV3Nd38w5rwM3XcAhQapUy9nQYNYFo8MBXQRvfy1pGNvoSTn2aH62QWAeJm1CvnQDJDDvFMtqzEDYXPXuGZYZPAiniuoHfswNZzQSZFZ5an23KqAkwhnyACN9GzqZUdSDHME2LKeKZYYYngV3qkVuRehMbFAtZ6HjqMqDoLU6DEP99z3yMCbdXHWQjMBVAbCG2qHTqFbsMyKPhqY6WvfaHGE1WwJpRFTR4TfsCAEBsrRGZfxJXhZwh6PbAdTh2GJCUc7p11LhB9PuPFunbVfNhXf3CxvehrvSYkSSTxsf4eucTu5jvWBNBZSste5L2US9DEpZDfg1LJTgrazLuADPTeBTR42ustyuyewKg2HXHaYhwQDaTZE63zu1dRSr8P5wtP8KiW1AUqay3FH1kr3KuTKcCo4PTzqmLnTE68sAby7R3vaGk32sYRMuh5EKUKCoZKMEvsUL4KEWmfrmpN9yRwsdH6jucf7WfqXHjs6seYnGKVQCG7ZSJeyFQpQp2P1wTBRDxwjYeM412WVWVSqPBGCbVLENYewvcc2wVo8Da7fHQbVmoiEZzshhqT5qh3MDeWtGDK2hnK2fcvAcZWSh5bSoByAjMnhK31y6bZzRgZVAYHjcu4U1jQfFUecLPQ4jt5y9cD4ntkjKqhHf5ovk4QFWga385hbAs9YknS4Ry94y3SiofTfWjqDtBNiH2CcsrkwTuju7zaHQM7fAvwMBwBQ91NefcJ9BfFkyTnZ9Rk8UK5vro4GcSj92m7EKfdvTTCwetB5nzdQWLapNiJnFNYeBiyo6xJZMPtmFUkBfyJiqrSHmt1JRNGokshBYA2mTUoyXZEnBhjhZ7o24Y3XjxUta6i8HyctzFaGkgJLCSGaZr3UmoVvYoQ7kV1DVRJkKBeA65VwfKxaWkoQFZFBL5SiA9bfFSwCDjaSYJfJfo3bUKLQEdh8RUTMxSb2LUkcS1iPAo3gip3iBYVEBwZ1n1W7RgPdbMYLjPYzYAGUAFwYpoMEng1ZABjTF")

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
    val outputBoxes = generateVotingBoxes(ctx, minerAddressList, minerShareArray, workerList, votingValue, minerAddress,
                                          minerVoteArray, metadataAddress, votingAddress)
    val redepositBox = generateRedepositHoldingBoxes(ctx, boxesToSpend, votingValue, minerAddress, holdingAddress)
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
      loadInitialAddresses
      //miningPoolToHoldingBoxTx(ctx, miningPoolString)
      VoteTx(ctx, miner1String, miner1Address, miner1ShareList, miner1Votes)
      //consensusToMinersTx(ctx, miner1String, miner2String)
    })
      //val txJson: String = sendTx("subpool_config.json")
      System.out.println(txJson)
    }

}