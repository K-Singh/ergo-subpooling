package contracts

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import scalan.RType
import scorex.crypto.hash.Blake2b256
import sigmastate.basics.DLogProtocol.ProveDlog
import special.collection.Coll
import special.sigma.SigmaProp

import scala.collection.mutable.ListBuffer

object HoldingContract {

  def getHoldingScript: String = {
    /**
     * This is the modified holding contract.
     * There are two main phases, the initiation phase and the modification phase.
     *
     * +++++STANDARD:
     * The standard phase of the holding contract works similar to the old holding contract. During the standard phase,
     * money from the mining pool is sent to the holding contract. Afterwards, members of the subpool may send their votes
     * in to vote on key subpool parameters that will kick into affect during the next payout cycle. The standard phase
     * assumes that The MetaData Box has been created. This in turn means that the Initiation Phase has ended.
     * If a member does not send a vote in after a certain time, their vote will automatically be sent as a skip vote.
     * Skip votes will either:
     * a) automatically follow consensus
     * or
     * b) be evenly distributed amongst the voting members as a claim box.
     * The variable controlling this shall be called RECLAIM_SKIPS
     *
     * The height at which voting ends will be called INITIAL_AUTO_SKIP_HEIGHT and its default value shall be:
     * the height of the first vote(Stored in R6 of redeposit holding boxes) + const_max_voting_height(defined at creation
     * but by default will be around 1500 or 2 days if blocks are being mined at around 2 min per block).
     *
     * The Initiation phase will end when either:
     * a) The first subpool_cycle has been completed and the MetaData box has been created.
     * or
     * b) HEIGHT > const_init_ending_height; const_init_ending_height will be a hardcoded constant with default value current height + 3000(Around 4 days).
     *
     * The Initiation phase will have two spending paths.
     *
     * INIT Spending Path 1:
     * The first spending path assumes that R4, R5, and R6 of SELF is not defined.
     * This indicates that this box was created by a mining pool sending money to the Holding box. In this spending path,
     * OUTPUTS(0) corresponding to the first vote must be a consensus box. The value of this box will be voting_value where:
     * votingValue = TOTAL_INPUTS_VALUE / NUM_MINERS - MinTxFee
     *
     * OUTPUTS(1) must then be a box protected by this holding scripts proposition bytes. This output box will redeposit
     * money back to the holding script so that the other members may vote. R4 of this box will hold voting_value as a Long.
     * R5 of this box will hold a collection of SigmaProps corresponding to the subpool_members who have just voted(Currently just one).
     * R6 of this box will hold the current HEIGHT to be used in INITIAL_AUTO_SKIP_HEIGHT
     * The value of this box will be total_inputs_value - voting_value.
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
     * +++++STANDARD:
     * The standard phase of the holding contract works similar to the Initiation phase. During the standard phase,
     * money from the mining pool is sent to the holding contract. Afterwards, members of the subpool may send their votes
     * in to vote on key subpool parameters that will kick into affect during the next payout cycle. The standard phase
     * assumes that The MetaData Box has been created. This in turn means that the Initiation Phase has ended and that
     * there is information stored on the blockchain pertaining to key parameters for this subpool. These parameters
     * will be grabbed from the MetaData Box instead of through the constants originally entered into the subpool
     * during its creation.
     *
     * The key parameters include:
     * the miner:worker list, the pool state, and MAX_VOTING_HEIGHT, RECLAIM_SKIPS,
     */
    val script: String = s"""
    {
       val INITIATION_ENDED = HEIGHT > const_initiationEndingHeight

       // Check if first vote sent
       val VOTING_STARTED = SELF.R4[Long].isDefined && SELF.R5[Coll[SigmaProp]].isDefined && SELF.R6[Int].isDefined
       val TOTAL_INPUTS_VALUE = INPUTS.fold(0L, {(accum: Long, box: Box) => accum + box.value})
       val INITIAL_MEMBERS = const_initMembers
       // Unzips list of members(tuples of type (SigmaProp, Coll[Byte]]) into tuple of type (Coll[SigmaProp], Coll[Coll[Byte]])
       val unzipMemberList = {
        (memberMap: Coll[(SigmaProp, Coll[Byte])]) =>
          (memberMap.map{
            (memVote: (SigmaProp, Coll[Byte])) =>
              memVote._1
          },
          memberMap.map{
            (memVote: (SigmaProp, Coll[Byte])) =>
              memVote._2
          })
       }
       val INITIAL_MINERS = unzipMemberList(INITIAL_MEMBERS)._1
       val INITIAL_WORKERS = unzipMemberList(INITIAL_MEMBERS)._2

       // Checks if Metadata exists and that only 1 Metadata box is being used in this transaction using hashed prop bytes
       val METADATA_VALID = blake2b56(CONTEXT.dataInputs(0).propositionBytes) == const_metadataPropBytesHashed

       val getMetaDataBox: Option[Box] = {
         if(METADATA_VALID){
            CONTEXT.dataInputs(0)
         }else{
            None
         }
       }

       // Represents evenly divided value of inputs as value of voting box
       val votingValue: Long = {
         if(METADATA_VALID){
          (TOTAL_INPUTS_VALUE / getMetaDataBox.get.R4[Coll[(SigmaProp, Long)]].get.size) - const_MinTxFee
         }else{
          // If MetaData Box isn't defined, we are still in Initiation. Initial address size value is used instead.
          (TOTAL_INPUTS_VALUE / INITIAL_MINERS.size) - const_MinTxFee
         }
       }

       val VOTE_EXISTS = allOf(Coll(
          OUTPUTS(0).R4[Coll[(SigmaProp, Long)]].isDefined,
          OUTPUTS(0).R5[Coll[(SigmaProp, Coll[Byte])]].isDefined,
          OUTPUTS(0).R6[SigmaProp].isDefined,
          OUTPUTS(0).R7[Coll[Byte]].isDefined,
          OUTPUTS(0).R7[Coll[Byte]].get == const_metadataPropBytesHashed,
          OUTPUTS(0).R8[Coll[Int]].isDefined,
          OUTPUTS(0).R8[Coll[Int]].get.apply(0) >= 180 && OUTPUTS(0).R8[Coll[Int]].get.apply(0) <= 20160,
          OUTPUTS(0).value == votingValue,
          OUTPUTS(0).propositionBytes == const_consensusPropBytesHashed
        ))
       val REDEPOSIT_EXISTS = allOf(Coll(
        OUTPUTS.size == 3,
        INPUTS.size == 1,
        OUTPUTS(1).propositionBytes == SELF.propositionBytes,
        OUTPUTS(1).value == SELF.value - votingValue,
        OUTPUTS(1).R4[Long].isDefined && OUTPUTS(1).R4[Long].get == votingValue,
        OUTPUTS(1).R5[Coll[SigmaProp]].isDefined,
        OUTPUTS(1).R6[Int].isDefined && (OUTPUTS(1).R6[Int].get == HEIGHT || OUTPUTS(1).R6[Int].get == SELF.R6[Int].get)
       ))
       // Checks if members of pk collection are not in redeposit box's R5
       // In practice, it is compared to the collection of INITIAL_MINERS and any subset of it during the Initiation Phase
       // During standard operation the collection of miners and it's subsets is retrieved from the MetaData box.
       val redepositValid = {
        (pkList: Coll[SigmaProp]) => pkList.forall{
          (pk: SigmaProp) => OUTPUTS(1).R5[Coll[SigmaProp]].get.forall{
            (signer: SigmaProp) => signer != pk
          }
        }
       }
       // returns boolean that checks if given box is a skip vote box
       val skipExists = {
          (box: Box) =>
          allOf(Coll(
          box.R4[SigmaProp].isDefined,
          box.R5[Coll[Byte]].isDefined && box.R5[Coll[Byte]].get == const_metadataPropBytesHashed,
          box.R6[Coll[Byte]].isDefined && box.R6[Coll[Byte]].get == blake2b56(SELF.propositionBytes)
          box.value == votingValue,
          box.propositionBytes == const_consensusPropBytesHashed
        ))
       }

       val isKeyInPoolState = {
         (pk: SigmaProp) => OUTPUTS(0).R4[Coll[(SigmaProp, Long)]].get.exists{(poolStateVal: (SigmaProp, Long)) => poolStateVal._1 == pk}
       }
       val isWorkerInWorkerList = {
        (worker: Coll[Byte]) => OUTPUTS(0).R5[Coll[Coll[Byte]]].get.exists{(workerName: Coll[Byte]) => workerName == worker}
       }
       val poolStateValid = {
        (pkList: Coll[SigmaProp]) => pkList.forall(areAllKeysInPoolState)
       }
       val membersValid = {
        (memberList: Coll[(SigmaProp, Coll[Byte])]) =>
          OUTPUTS(0).R5[Coll[(SigmaProp, Coll[Byte])]].get == memberList
       }
       val signatureStored = {
        (pkList: Coll[SigmaProp]) => pkList.filter{(pk: SigmaProp) => pk == OUTPUTS(0).R6[SigmaProp].get}(0)
       }
       val signerInSubPool = {
        (pkList: Coll[SigmaProp] => atLeast(1, pkList)
       }
       val filterOutPKs = {
          (pkList: Coll[SigmaProp], filterOut: Coll[SigmaProp]) => pkList.filter{
            (pk: SigmaProp) => !filterOut.exists(pk)
          }
       }

       // First voter spending path
       // Ensures creation of voting box, and redeposit holding box with properly set values
       val constructSpendingPath1 = {
        (memberList: Coll[(SigmaProp, Coll[Byte])]) =>
         val pkList = unzipMemberList(memberList)._1

         val boolCheck = allOf(Coll(
              VOTE_EXISTS,
              REDEPOSIT_EXISTS,
              poolStateValid(pkList),
              membersValid(memberList)
            ))

            sigmaProp(boolCheck) && signerInSubPool(pkList) && signatureStored(pkList) && redepositValid(
              filterOutPKs(pkList, Coll(atLeast(1, pkList)))
            )
       }
       // Voting has initiated, so SELF has registers.
       // In 2a, these registers build a voting box and redeposit holding box with updated values
       val constructSpendingPath2a = {
        (memberList: Coll[(SigmaProp, Coll[Byte])]) =>
         val pkList = unzipMemberList(memberList)._1
         val filteredPKs = filterOutPKs(pkList, SELF.R5[Coll[SigmaProp]].get)
         val boolCheck = allOf(Coll(
           VOTE_EXISTS,
           REDEPOSIT_EXISTS,
           poolStateValid(filteredPKs),
           membersValid(memberList)
         ))
         sigmaProp(boolCheck) && signerInSubPool(filteredPKs) && signatureStored(filteredPKs) & redepositValid(
           filterOutPKs(filteredPKs, Coll(atLeast(1, filteredPKs)))
         )
       }
       // In 2b, the last miner has sent their voting box, so no redeposit box is needed.
       val constructSpendingPath2b = {
        (memberList: Coll[(SigmaProp, Coll[Byte])]) =>
        val pkList = unzipMemberList(memberList)._1
        val filteredPKs = filterOutPKs(pkList, SELF.R5[Coll[SigmaProp]].get)
        val boolCheck = allOf(Coll(
           VOTE_EXISTS,
           poolStateValid(filteredPKs),
           membersValid(memberList)
         ))

         sigmaProp(boolCheck) && signerInSubPool(filteredPKs) && signatureStored(filteredPKs)
       }
       // In 3, HEIGHT > MAX_VOTING_HEIGHT, so skip-vote boxes can be made for any miner
       val constructSpendingPath3 = {
        (memberList: Coll[(SigmaProp, Coll[Byte])]) =>
        val pkList = unzipMemberList(memberList)._1
        val filteredPKs = filterOutPKs(pkList, SELF.R5[Coll[SigmaProp]].get)
        val boolCheck = allOf(Coll(
           filteredPKs.forall(skipExists),
           filteredPKs.forall{(pk: SigmaProp) => OUTPUTS.exists{(box: Box) => box.R4[SigmaProp].get == pk}}
         ))
         // Check only that skip vote boxes have been properly made and that signer is part of original pkList
         sigmaProp(boolCheck) && signerInSubPool(pkList)
       }

       // Constructs main spending paths together by inputting parameters, used to separate Initiation and Standard operation.
       val constructSpendingBranch = {
       (memberList: Coll[(SigmaProp, Coll[Byte])], maximumVotingHeight: Int) =>

        // Checks if Holding Box Regs defined, indicates voting has begun.
         if(!VOTING_STARTED && HEIGHT < maximumVotingHeight){

         // This must be the first vote during the initiation phase. Therefore we use an unmodified instance of the parameter value: pkList
           constructSpendingPath1(memberList)

         // Not the first vote, but voting is still allowed. We may access the registers of SELF now to get information about this voting transaction.
         }else if(VOTING_STARTED && HEIGHT < maximumVotingHeight){

            // Checks to see if this is the last vote, so that no redeposit is needed
            if(SELF.value != SELF.R4[Long]){
              constructSpendingPath2a(memberList)
            }else{
              // Not the last vote, so redeposit is needed. During Initiation so we use pkList but filter
              // it so that only the pks of miners NOT in SELF.R5 are evaluated.
              constructSpendingPath2b(memberList)
            }
         }else{
            // HEIGHT > maximumVotingHeight, so any member of the subpool may create the vote boxes as long as they
            // are correctly created and their number is equal to the number of miners not in SELF.RR5
            constructSpendingPath3(memberList)
         }

       }
       // This constructs the final SigmaProp that determines whether or not the transaction is valid
       // Uses two spending branches to indicate difference between initiation and standard operation
       if(!INITIATION_ENDED){
          constructSpendingBranch(INITIAL_MEMBERS, const_maxVotingHeight)
       }else if(getMetaDataBox.isDefined){
          // R5 of MetaDataBox holds collection of members in this subpool
          // R6 of MetaDataBox holds integer voting values where index 0 corresponds to MAX_VOTING_HEIGHT
          // Might have to change registers of metadatabox and holding box to support members.
          val SUBPOOL_MEMBERS = getMetaDataBox.R5[Coll[(SigmaProp, Coll[Byte])]].get
          val MAX_VOTING_HEIGHT = getMetaDataBox.R6[Coll[Int]].get(0)
          constructSpendingBranch(SUBPOOL_MEMBERS, MAX_VOTING_HEIGHT)
       }else{
        sigmaProp(false)
       }
    }
      """.stripMargin
    script
  }

  /**
   * Generates Holding Contract with given constants
   * @param ctx Blockchain context used to generate contract
   * @param memberList Array of tuples mapping Addresses to Worker Names.
   * @param maxVotingHeight maximum height until voting is stopped set during creation
   * @param initiationEndingHeight end of initiation height set during creation
   * @param consensusAddress address of consensus
   * @param metadataAddress address of metadata
   * @return Compiled ErgoContract of Holding Smart Contract
   */
  def generateHoldingContract(ctx: BlockchainContext, memberList: Array[(Address, String)], maxVotingHeight: Int, initiationEndingHeight: Int, consensusAddress: Address, metadataAddress: Address): ErgoContract = {
    val membersUnzipped: (Array[Address], Array[String]) = memberList.unzip
    val minerList = membersUnzipped._1
    val workerList = membersUnzipped._2
    val publicKeyList = (minerList.map{(addr: Address) => addr.getPublicKey})
    val workerListHashed = workerList.map{(name: String) => Blake2b256(name)}
    val membersRezipped = publicKeyList.zip(workerListHashed)
    val consensusPropBytesHashed = Blake2b256(consensusAddress.getErgoAddress.contentBytes)
    val metadataPropBytesHashed = Blake2b256(metadataAddress.getErgoAddress.contentBytes)
    val constantsBuilder = ConstantsBuilder.create()

    val compiledContract = ctx.compileContract(constantsBuilder
      .item("const_initiationEndingHeight", ctx.getHeight + initiationEndingHeight)
      .item("const_initMembers", membersRezipped)
      .item("const_MinTxFee", Parameters.MinFee)
      .item("const_consensusPropBytesHashed", consensusPropBytesHashed)
      .item("const_metadataPropBytesHashed", metadataPropBytesHashed)
      .item("const_maxVotingHeight", ctx.getHeight + maxVotingHeight)
      .build(), getHoldingScript)
    compiledContract
  }

  /**
   * Generates a redeposit Holding Box with properly assigned registers
   * @param ctx Context as Blockchain Context
   * @param holdingBoxes Array of input boxes from holding address
   * @param minerAddressLength Length of miner address list
   * @param signerAddress Signer address of transaction
   * @param holdingAddress Holding address
   */
  def generateRedepositHoldingBox(ctx: BlockchainContext, holdingBoxes: Array[InputBox], minerAddressLength: Int, signerAddress: Address, holdingAddress: Address): Unit = {

    val holdingBoxHead = holdingBoxes.head
    val totalValue = holdingBoxes.foldLeft(0L){(accum: Long, box: InputBox) => accum + box.getValue}
    val votingValue = (totalValue / minerAddressLength) - Parameters.MinFee
    val signerAddressAsColl = special.collection.Builder.DefaultCollBuilder.fromItems(genSigProp(signerAddress))
    val reg4 = holdingBoxHead.getRegisters.get(0).getValue
    val reg5 = holdingBoxHead.getRegisters.get(1).getValue
    val reg6 = holdingBoxHead.getRegisters.get(2).getValue
    val regList = Array(reg4, reg5, reg6)
    // If holding box head contains registers, then must be from another redeposit holding box. Therefore values can
    // be obtained from its registers.
    if(!regList.contains(None) && holdingBoxes.length == 1){
      val storedVotingValue = reg4.asInstanceOf[Long]
      val voterList = reg5.asInstanceOf[Coll[SigmaProp]]
      val votingStartedHeight = reg6.asInstanceOf[Int]
      val newVoterList = voterList.append(signerAddressAsColl)
      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
      val outBoxList: ListBuffer[OutBox] = ListBuffer.empty[OutBox]
      val ergoVal1 = ErgoValue.of(storedVotingValue)
      val ergoVal2 = ErgoValue.of(newVoterList, ErgoType.sigmaPropType())
      val ergoVal3 = ErgoValue.of(votingStartedHeight)
      val regValueList: Array[ErgoValue[_]] = Array(ergoVal1, ergoVal2, ergoVal3)
      outBoxList.append(
        txB.outBoxBuilder()
          .value(votingValue)
          .contract(new ErgoTreeContract(holdingAddress.getErgoAddress.script))
          .registers(regValueList:_*)
          .build())
    }
    // If holding box registers do not exist and length is greater than 1
    else if(holdingBoxes.length >= 1){

      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
      val outBoxList: ListBuffer[OutBox] = ListBuffer.empty[OutBox]
      val ergoVal1 = ErgoValue.of(votingValue)
      val ergoVal2 = ErgoValue.of(signerAddressAsColl, ErgoType.sigmaPropType())
      val ergoVal3 = ErgoValue.of(ctx.getHeight)
      val regValueList: Array[ErgoValue[_]] = Array(ergoVal1, ergoVal2, ergoVal3)
      outBoxList.append(
        txB.outBoxBuilder()
          .value(votingValue)
          .contract(new ErgoTreeContract(holdingAddress.getErgoAddress.script))
          .registers(regValueList:_*)
          .build())
    }
  }

  /**
   * Generates Voting Register for outputted voting box list
   * @param minerList Array of miner addresses
   * @param minerShares Array of miner shares
   * @param workerList Array of worker names
   * @param signerAddress Address of signer
   * @param metadataAddress Metadata address for subpool
   * @return Returns list of registers for voting
   */
  def generateVotingRegisterList(minerList: Array[Address], minerShares: Array[Long], workerList: Array[String], signerAddress: Address, metadataAddress: Address): Seq[ErgoValue[_]] = {
    val sigList = minerList.map(genSigProp)
    val keysToSharesTupleMap: Array[(SigmaProp,Long)] = sigList.zip(minerShares)
    val poolStateCollection: Coll[(SigmaProp, Long)] = special.collection.Builder.DefaultCollBuilder
      .fromItems(keysToSharesTupleMap: _*)(RType.pairRType(special.sigma.SigmaPropRType, RType.LongType))
    val workerListHashed = workerList.map{(str: String) => Blake2b256(str)}.map{(byteArray: Array[Byte]) => special.collection.Builder.DefaultCollBuilder.fromArray(byteArray)}
    val workerBytesCollection: Coll[Coll[Byte]] = special.collection.Builder.DefaultCollBuilder
      .fromItems(workerListHashed: _*)
    val ergoVal1 = ErgoValue.of[(SigmaProp, Long)](poolStateCollection, ErgoType.pairType[SigmaProp, Long](ErgoType.sigmaPropType(), ErgoType.longType()))
    val ergoVal2 = ErgoValue.of(workerBytesCollection, ErgoType.collType(ErgoType.byteType()))
    val ergoVal3 = ErgoValue.of(signerAddress.getPublicKey)
    val ergoVal4 = ErgoValue.of(Blake2b256(metadataAddress.getErgoAddress.contentBytes))
    List(ergoVal1, ergoVal2, ergoVal3, ergoVal4)
  }

  /**
   * Generates register list for skip votes
   * @param signerAddress Address of signer
   * @param metadataAddress Address of metadata
   * @return Returns list of registers for skip votes
   */
  def generateSkipVoteRegisterList(signerAddress: Address, metadataAddress: Address): Seq[ErgoValue[_]] = {
    val ergoVal1 = ErgoValue.of(signerAddress.getPublicKey)
    val ergoVal2 = ErgoValue.of(Blake2b256(metadataAddress.getErgoAddress.contentBytes))
    List(ergoVal1, ergoVal2)
  }


}
