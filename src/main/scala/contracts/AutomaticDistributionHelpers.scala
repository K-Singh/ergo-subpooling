package contracts

import contracts.HoldingStageHelpers.getHoldingScript
import org.ergoplatform.ErgoBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, BlockchainContext, BoxOperations, ConstantsBuilder, ErgoContract, ErgoType, ErgoValue, InputBox, OutBox, Parameters, UnsignedTransactionBuilder}
import scalan.RType
import sigmastate.basics.DLogProtocol.ProveDlog
import special.collection.Coll
import special.sigma.{BoxRType, SigmaProp}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.mutable.ListBuffer

/**
 * Helper object for Automatic Distribution Smart Contract
 *
 * This smart contract works by using a constant list of hashrates that correspond to each mining address.
 * These hashrates are then used to calculate the value awarded to each miner in the subpool.
 * AutoDist Contracts are easier to use as there is no withdrawal request needed. Any miner may sign
 * the transaction that allows Automatic Distribution to occur. However, because AutoDist Contracts
 * do not check with the mining pool for verification, there is the potential for abuse to occur.
 * Malicious actors in a subpool could claim to be mining, without actually contributing.
 *
 * Despite this problem, AutoDist contracts have been added because any member of the subpool can verify
 * that miners are contributing by using their mining pool's website. Still, it seems that
 * these contracts are best left for close groups of friends.
 */
object AutomaticDistributionHelpers {
  /**
   * Method to return AutoDist script
   * @param scriptVar Manually input miner addresses into script. Is there a cleaner way around this?
   * @return String containing an AutoDist script with scriptVar inputted.
   */
  def getADScript(scriptVar: String) : String = {
    val script: String = s"""
      {
       val MinerPKs = Coll(${scriptVar})
       val totalHashrate = hashrateList.fold(0L, {(accum: Long, hr: Long) => accum + hr})
       val totalValue = INPUTS.fold(0L, {(accum: Long, box: Box) => accum + box.value})

       val outputValueList = hashrateList.map{(hr: Long) => ((hr * totalValue)/totalHashrate) - (minFee/(MinerPKs.size * 1L))}
       val poolState = MinerPKs.zip(outputValueList)

       def getValueFromPoolState(pk: SigmaProp) : Long = {
        val filteredPoolState = poolState.filter{(poolStateVal: (SigmaProp, Long)) => poolStateVal._1 == pk}
        filteredPoolState(0)._2
        }

       val doOutputsFollowPoolState = MinerPKs.forall{
       (pk: SigmaProp) => OUTPUTS.exists{
        (box: Box) => getValueFromPoolState(pk) == box.value && box.propositionBytes == pk.propBytes
          }
        }

       val isSignerInMinerList = atLeast(1, MinerPKs)

       isSignerInMinerList && sigmaProp(doOutputsFollowPoolState)
       }
      """.stripMargin
    script
  }

  /**
   * Generates AutoDist contract from given parameters.
   * @param ctx
   * Blockchain context used for transactions.
   * @param minerList
   * List of miner addresses.
   * @param hashrateList
   * List of miner hashrates.
   * @return
   * ErgoContract corresponding to some unique AutoDist contract.
   */
  def generateADContract(ctx: BlockchainContext, minerList: List[Address], hashrateList: Array[Long]): ErgoContract = {
    val publicKeyList = minerList.map{(addr: Address) => addr.getPublicKey}

    val constantsBuilder = ConstantsBuilder.create()
    var numMiners = 1
    var scriptVarString = "MinerPK0"
    constantsBuilder.item("MinerPK0", publicKeyList.head)

    def buildMinerConstants(dlog: ProveDlog): Unit = {
      scriptVarString += ", "
      constantsBuilder.item(s"MinerPK${numMiners}", dlog)
      scriptVarString += s"MinerPK${numMiners}"
      numMiners += 1
    }

    publicKeyList.drop(1).foreach{(dlog: ProveDlog) => buildMinerConstants(dlog)}
    val compiledContract = ctx.compileContract(constantsBuilder
      .item("minFee", Parameters.MinFee)
      .item("hashrateList", hashrateList)
      .build(), getADScript(scriptVarString))
    compiledContract
  }

  /**
   * Builds output boxes needed for a proper AutoDist transaction.
   * @param ctx
   * Blockchain context used for transactions.
   * @param inputBoxList
   * List of input boxes to be used in transaction.
   * @param minerList
   * List of miner addresses that correspond to this AutoDist contract.
   * @param hashrateList
   * List of hashrates that correspond to each miner.
   * @return
   * Output Box list with boxes under each miner's address. Box values correspond to that miner's hashrate in proportion to the total hashrate.
   */
  def buildOutputsForAD(ctx: BlockchainContext, inputBoxList: java.util.List[InputBox], minerList: List[Address], hashrateList: Array[Long]) = {
    val totalHashrate = hashrateList.fold(0L){(accum: Long, hr: Long) => accum + hr}
    val totalValue = inputBoxList.asScala.toList.foldLeft(0L){(accum: Long, box: InputBox) => accum + box.getValue}

    val outputValueList = hashrateList.map{(hr: Long) => ((hr * totalValue) / totalHashrate) - (Parameters.MinFee/(minerList.size * 1L))}
    val poolState = minerList.zip(outputValueList)

    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val outBoxList: ListBuffer[OutBox] = ListBuffer.empty[OutBox]
    poolState.foreach{(addrToBoxVal: (Address, Long)) =>
      outBoxList.append(txB.outBoxBuilder().value(addrToBoxVal._2).contract(new ErgoTreeContract(addrToBoxVal._1.getErgoAddress.script)).build())
    }
    outBoxList
  }


}
