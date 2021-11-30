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

object ConsensusStageHelpers {

  def getConsensusScript(scriptVar: String) : String = {
    val script: String = s"""
      {
       val MinerPKs = Coll(${scriptVar})
       val regsDefined: Boolean = {
        (box : Box) => box.R4[Coll[(SigmaProp, Long)]].isDefined && box.R5[Long].isDefined && box.R6[SigmaProp].isDefined && box.R7[Coll[Byte]].isDefined
        }
       val areInputsValid = MinerPKs.forall{
        (pk : SigmaProp) => INPUTS.exists{
          (box: Box) =>
            box.propositionBytes == SELF.propositionBytes && regsDefined(box) && (if(regsDefined(box)){
                                                                                    box.R6[SigmaProp].get == pk
                                                                                  }else{
                                                                                    true
                                                                                  })
          }
        }

       val uniqueWorkerName = INPUTS.filter{(box: Box) => box.id != SELF.id}.forall{(box: Box) => SELF.R7[Coll[Byte]].get != box.R7[Coll[Byte]].get}

       def buildPoolState(box: Box): Coll[(SigmaProp, Long)] = {
        box.R4[Coll[(SigmaProp, Long)]].get
       }

       val poolStateList: Coll[Coll[(SigmaProp,Long)]] = INPUTS.map(buildPoolState)

       val totalValue = INPUTS.fold(0L, {(accum: Long, box: Box) => accum + box.value})
       val avgTotalShares = INPUTS.fold(0L, {(accum: Long, box: Box) => accum + box.R5[Long].get}) / (MinerPKs.size * 1L)

       def getBoxValue(shareNum: Long) : Long = {
        val boxVal = ((shareNum * totalValue) / avgTotalShares) - ((minFee * 2 * MinerPKs.size)/(MinerPKs.size * 1L))
        boxVal
       }

       def buildConsensus(pk: SigmaProp) : (SigmaProp, Long) = {
        val filteredPoolState = poolStateList.map{(poolState: Coll[(SigmaProp, Long)]) => poolState.filter{(poolStateVal: (SigmaProp, Long)) => poolStateVal._1 == pk}(0)}
        val generatedAvg = (filteredPoolState.fold(0L, {(accum:Long, poolStateVal: (SigmaProp, Long)) => accum + poolStateVal._2})) / (MinerPKs.size * 1L)
        val boxValFromAvg = getBoxValue(generatedAvg)
        val minerBoxVal =
          if(boxValFromAvg < 0){
            0L
          }else{
            boxValFromAvg
          }

        (pk, minerBoxVal)
        }
       val consensusPoolState = MinerPKs.map(buildConsensus)

       def getValueFromConsensus(pk: SigmaProp) : Long = {
        val filteredConsensus = consensusPoolState.filter{(poolStateVal: (SigmaProp, Long)) => poolStateVal._1 == pk}
        filteredConsensus(0)._2
        }

       val doOutputsFollowConsensus = MinerPKs.forall{
       (pk: SigmaProp) =>
         val pkConsensusValue = getValueFromConsensus(pk)
         if(pkConsensusValue != 0){
           OUTPUTS.exists{
            (box: Box) =>
              pkConsensusValue == box.value && box.propositionBytes == pk.propBytes
            }
         }else{
          true
         }
       }

       val isSignerInMinerList = atLeast(1, MinerPKs)
       val areOutputsValid =  doOutputsFollowConsensus

       isSignerInMinerList && sigmaProp(areInputsValid && areOutputsValid && uniqueWorkerName)
       }
      """.stripMargin
    script
  }
  //Removed areOutputsValid in return line and removed Outputs.size check in areOutputsValid
  // Include workerTupleMap
  def generateConsensusContract(ctx: BlockchainContext, minerList: List[Address], miningPoolPayout: Long): ErgoContract = {
    val publicKeyList = minerList.map{(addr: Address) => addr.getPublicKey}
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
      .item("minFee", Parameters.MinFee)

      .build(), getConsensusScript(scriptVarString))
    compiledContract
  }

  def buildConsensusFromBoxes(inputBoxList: java.util.List[InputBox], minerPKs: List[SigmaProp]): List[(SigmaProp, Long)] = {
    /*val initPoolStateList: Coll[(SigmaProp, Long)] = initialInputBox.getRegisters.get(0).getValue.asInstanceOf[Coll[(SigmaProp, Long)]]
    val uniqueInputs = inputBoxList.asScala.filter{(box: InputBox) => box.getId != initialInputBox.getId}
    val poolStateList: Coll[(SigmaProp, Long)] = uniqueInputs.foldLeft(initPoolStateList){(accum: Coll[(SigmaProp, Long)], box: InputBox) =>
      accum.append(box.getRegisters.get(0).getValue.asInstanceOf[Coll[(SigmaProp,Long)]])}*/

    def buildPoolState(box: InputBox) = {
      box.getRegisters.get(0).getValue.asInstanceOf[Coll[(SigmaProp, Long)]].toArray.toList
    }

    def getPoolVal(poolState: List[(SigmaProp, Long)], pk: SigmaProp) = {
      poolState.filter{(poolStateVal: (SigmaProp, Long)) => poolStateVal._1 == pk}(0)
    }

    val poolStateList = inputBoxList.asScala.toList.map(buildPoolState)
    def buildConsensus(pk: SigmaProp) : (SigmaProp, Long) = {
      val filteredPoolState: List[(SigmaProp, Long)] = poolStateList.map{(poolState: List[(SigmaProp, Long)]) => getPoolVal(poolState, pk)}
      val generatedAvg: Long = filteredPoolState.toArray.foldLeft(0L){(accum: Long, poolStateVal:(SigmaProp, Long)) => accum + poolStateVal._2} / minerPKs.size
      (pk, generatedAvg)
    }

    val consensusPoolState = minerPKs.map(buildConsensus)
    consensusPoolState
  }

  def buildOutputsFromConsensus(ctx: BlockchainContext, consensusPoolState: List[(SigmaProp, Long)], minerList: List[Address], totalShares: Long, totalValue: Long): ListBuffer[OutBox] = {

    val keyToBoxValue = consensusPoolState.map{(poolStateVal: (SigmaProp, Long)) => (poolStateVal._1, ((poolStateVal._2 * totalValue) / totalShares) - ((Parameters.MinFee * 2 * minerList.size)/minerList.size))}
    val addressToBoxValueList: List[(Address, Long)] = minerList.map{
      (addr: Address) => (addr, keyToBoxValue.filter{
        (poolStateVal: (SigmaProp, Long)) => poolStateVal._1 == genSigProp(addr)
      }.head._2)
    }
    //println(addressToBoxValueList)
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val outBoxList: ListBuffer[OutBox] = ListBuffer.empty[OutBox]
    addressToBoxValueList.foreach{(addrToBoxVal: (Address, Long)) =>
      if(addrToBoxVal._2 > 0)
        outBoxList.append(txB.outBoxBuilder().value(addrToBoxVal._2).contract(new ErgoTreeContract(addrToBoxVal._1.getErgoAddress.script)).build())
    }
  outBoxList
  }

  def findValidInputBoxes(ctx: BlockchainContext, minerList: List[Address], inputBoxes:List[InputBox]): List[InputBox] = {
    minerList.map{(addr: Address) => inputBoxes.filter {
      (box: InputBox) => genSigProp(addr) == box.getRegisters.get(2).getValue.asInstanceOf[SigmaProp]}.head
    }
  }

}
