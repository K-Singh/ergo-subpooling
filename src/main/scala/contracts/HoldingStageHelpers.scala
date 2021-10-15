package contracts

import org.ergoplatform.appkit.{Address, BlockchainContext, ConstantsBuilder, ErgoContract, ErgoType, ErgoValue, NetworkType, Parameters}
import scalan.RType
import scorex.crypto.hash.Blake2b256
import sigmastate.basics.DLogProtocol.ProveDlog
import special.collection.Coll
import special.sigma.SigmaProp

object HoldingStageHelpers {

  def getHoldingScript(scriptVar: String): String = {
    val script: String = s"""
       {
       val RegsDefined = allOf(Coll(
       OUTPUTS(0).R4[Coll[(SigmaProp, Long)]].isDefined,
       OUTPUTS(0).R5[Long].isDefined,
       OUTPUTS(0).R6[SigmaProp].isDefined,
       OUTPUTS(0).R7[Coll[Byte]].isDefined
       ))
       val MinerPKs = Coll(${scriptVar})
       val consensusBytes = consensusPropBytes
       def areAllKeysInPoolState(pk: SigmaProp):Boolean = OUTPUTS(0).R4[Coll[(SigmaProp, Long)]].get.exists{(poolStateVal: (SigmaProp, Long)) => poolStateVal._1 == pk}

       val isPoolStateValid = RegsDefined && MinerPKs.forall(areAllKeysInPoolState)
       val isTotalSharesValid = RegsDefined && OUTPUTS(0).R4[Coll[(SigmaProp, Long)]].get.fold(0L, {(accum:Long, poolStateVal: (SigmaProp, Long)) => accum + poolStateVal._2}) == OUTPUTS(0).R5[Long].get

       val isSignatureValid = sigmaProp(RegsDefined) && MinerPKs.filter{(pk: SigmaProp) => pk == OUTPUTS(0).R6[SigmaProp].get}(0)

       val doesOutputReDeposit = OUTPUTS.size == 3 && OUTPUTS.exists{(box: Box) => box.propositionBytes == SELF.propositionBytes}
       val isSignerInMinerList = atLeast(1, MinerPKs)
       val isOutput1ValueValid = OUTPUTS(0).value == (sentConsensusValue - minFee) && OUTPUTS(0).propositionBytes == consensusBytes
       val isRedepositNeeded = if(SELF.value >= sentConsensusValue*2){doesOutputReDeposit}else{true}

       (isSignerInMinerList && isSignatureValid && (sigmaProp(isPoolStateValid && isTotalSharesValid && isRedepositNeeded)))
       }
      """.stripMargin
    script
  }
  // Include workerTupleMap
  def generateHoldingContract(ctx: BlockchainContext, minerList: List[Address], miningPoolPayout: Long, consensusAddress: Address): ErgoContract = {
    val publicKeyList = (minerList.map{(addr: Address) => addr.getPublicKey})
    val consensusVal = miningPoolPayout / minerList.size

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
    //System.out.println(scriptVarString)
    val compiledContract = ctx.compileContract(constantsBuilder
      .item("sentConsensusValue", consensusVal)
      .item("minFee", Parameters.MinFee)
      .item("consensusPropBytes", consensusAddress.getErgoAddress.contentBytes)
      .build(), getHoldingScript(scriptVarString))
    compiledContract
  }

  // Include workerTupleMap
  def generateHoldingOutputRegisterList(minerList: List[Address], minerShares: List[Long], totalShares: Long, signerAddress: Address, workerName: String): Seq[ErgoValue[_]] = {
    val sigList = minerList.map(genSigProp)
    val keysToSharesTupleMap: List[(SigmaProp,Long)] = sigList.zip(minerShares)
    val poolStateCollection: Coll[(SigmaProp, Long)] = special.collection.Builder.DefaultCollBuilder
      .fromItems(keysToSharesTupleMap: _*)(RType.pairRType(special.sigma.SigmaPropRType, RType.LongType))

    val ergoVal1 = ErgoValue.of[(SigmaProp, Long)](poolStateCollection, ErgoType.pairType[SigmaProp, Long](ErgoType.sigmaPropType(), ErgoType.longType()))
    val ergoVal2 = ErgoValue.of(totalShares)
    val ergoVal3 = ErgoValue.of(signerAddress.getPublicKey)
    val ergoVal4 = ErgoValue.of(Blake2b256(workerName))
   
    List(ergoVal1, ergoVal2, ergoVal3, ergoVal4)
  }


}
