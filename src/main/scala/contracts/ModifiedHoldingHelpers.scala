package contracts

import org.ergoplatform.appkit._
import scalan.RType
import scorex.crypto.hash.Blake2b256
import sigmastate.basics.DLogProtocol.ProveDlog
import special.collection.Coll
import special.sigma.SigmaProp

object ModifiedHoldingHelpers {

  def getHoldingScript(scriptVar: String): String = {
    /**
     * This is the modified holding contract.
     * There are two main phases, the initiation phase and the modification phase.
     *
     * +++++INITIATION:
     * Initiation works similar to the standard holding contract. During initiation, money from the mining pool
     * is sent to the holding contract. Afterwards, members of the subpool may send their votes in to vote on key
     * subpool parameters that will kick into affect during the next payout cycle. If a member does not send a vote in
     * after a certain time, their payout will automatically be evenly distributed amongst the voting members.
     * This constant will be called MAX_VOTING_HEIGHT and its default value shall be INITIAL_VOTING_HEIGHT + 1500(Around 2 days if blocks are being
     * mined at around 2 min per block).
     *
     * The Initiation phase will end when either:
     * a) The first subpool_cycle has been completed and the MetaData box has been created.
     * or
     * b) HEIGHT > const_init_ending_height; const_init_ending_height will be a hardcoded constant with default value current height + 3000(Around 4 days).
     *
     * The Initiation phase will have two spending paths.
     *
     * INIT Spending Path 1:
     * The first spending path assumes that R4 and R5 of self is not defined.
     * This indicates that this box was created by a mining pool sending money to the Holding box. In this spending path,
     * OUTPUTS(0) corresponding to the first vote must be a consensus box. The value of this box will be voting_value where:
     * voting_value = SELF.value / NUM_MINERS - MinTxFee
     *
     * OUTPUTS(1) must then be a box protected by this holding scripts proposition bytes. This output box will redeposit
     * money back to the holding script so that the other members may vote. R4 of this box will hold voting_value as a Long.
     * R5 of this box will hold a collection of SigmaProps corresponding to the subpool_members who have just voted(Currently just one).
     * The value of this box will be SELF.value - voting_value
     *
     * If HEIGHT > const_init_ending_height
     * and spending path 2 cannot be taken(Nobody has sent a withdraw request), the lost rewards may be evenly distributed
     * using claim boxes.
     *
     * INIT Spending Path 2:
     * This spending path will be taken when SELF.R4[Long].isDefined and SELF.R5[ Coll[SigmaProp] ].isDefined
     * Malicious attempts to send false info shall not work as SELF is protected by this holding script.
     *
     * This spending path indicates that voting has begun. If HEIGHT is less than MAX_VOTING_HEIGHT, than members may
     * continue to send in votes as done in spending path 1. The value of OUTPUTS(0) will continue to be voting_value
     * which shall now be obtained from R4. If SELF.value > voting_value, The value of OUTPUTS(1) will be SELF.value - voting_value.
     *
     * If this is not the case, then OUTPUTS.size == 2 and OUTPUTS(1) corresponds to the MinTxFee.
     * If not all members have voted (SELF.value > voting_value) and HEIGHT > MAX_VOTING_HEIGHT, then any member of the
     * subpool may sign the claim_missing_votes transaction. This will produce N claim boxes where N = NUM_MINERS - SELF.R5[Coll [SigmaProp] ].size.
     * Each claim box will have value voting_value. R4 of the claim box will hold a SigmaProp corresponding to the public key of the miner who did not vote.
     *
     * At the end of consensus, miners will get their payouts. In addition to this, some amount of money will be sent
     * to the Subpool Metadata Address. The Subpool Metadata address will hold a number of boxes that hold the results of the last consensus phase.
     * The Subpool Metadata Box will hold a pool state representing the last consensus in R4. Any members added during voting will
     * be given a share number of 0. Each member must have a corresponding worker name. The worker list will be stored in R5.
     * R6 Will hold the the height at which the last consensus occurred.
     *
     * +++++MODIFICATION
     *
     * Modification works similar to Initiation. The difference is that key constants and parameters such as
     * the miner address list, miner:worker list, the pool state, and MAX_VOTING_HEIGHT are all obtained from the last consensus stored in the Metadata boxes.
     * There is no INITIATION_ENDING_HEIGHT or anything like it, modification is the normal phase that a holding box
     * will be in after its first vote.
     */
    val script: String = s"""
       {
       // Initiation ended
       val INITIATION_ENDED = HEIGHT > const_init_ending_height
       // Check if first vote sent
       val VOTING_INITIATED = SELF.R4[Long].isDefined && SELF.R5[Coll[SigmaProp]].isDefined

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
