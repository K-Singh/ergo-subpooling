package contracts

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import scalan.RType
import scorex.crypto.hash.Blake2b256
import sigmastate.Values.SigmaBoolean
import sigmastate.basics.DLogProtocol.ProveDlog
import special.collection.Coll
import special.sigma.{GroupElement, SigmaProp}

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
       val VOTING_STARTED = SELF.R4[Long].isDefined && SELF.R5[Coll[GroupElement]].isDefined && SELF.R6[Int].isDefined
       val TOTAL_INPUTS_VALUE = INPUTS.fold(0L, {(accum: Long, box: Box) => accum + box.value})
       val INITIAL_MEMBERS = const_initMembers.map{
            (initMembers: (GroupElement, Coll[Byte])) => (proveDlog(initMembers._1), initMembers._2)
          }
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
       val METADATA_VALID =
        if(CONTEXT.dataInputs.size == 1)
          blake2b256(CONTEXT.dataInputs(0).propositionBytes) == const_metadataPropBytesHashed
        else
          false

       val METADATA_BOX = if(METADATA_VALID){
          INPUTS.filter{(box:Box) => blake2b256(box.propositionBytes) == const_metadataPropBytesHashed}(0)
        }else{
        // Technically, because we always check if the metadata is valid before using it, this path is never used.
        // It was however included so that no error is thrown if the metadata box does not exist.
          SELF
        }
       val MAX_VOTING_HEIGHT = if(METADATA_VALID){
        METADATA_BOX.R6[Coll[Int]].get(0)
       }else{
        const_maxVotingHeight
       }
       // Represents evenly divided value of inputs as value of voting box
       val votingValue: Long = {
         if(METADATA_VALID){
          (TOTAL_INPUTS_VALUE / METADATA_BOX.R4[Coll[(SigmaProp, Long)]].get.size) - const_MinTxFee
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
          OUTPUTS(0).R8[Coll[Int]].get(0) >= 180 && OUTPUTS(0).R8[Coll[Int]].get(0) <= 20160,
          OUTPUTS(0).value == votingValue,
          blake2b256(OUTPUTS(0).propositionBytes) == const_consensusPropBytesHashed
        ))
       val REDEPOSIT_EXISTS = allOf(Coll(
        OUTPUTS.size == 3,
        INPUTS.size == 1,
        OUTPUTS(1).propositionBytes == SELF.propositionBytes,
        OUTPUTS(1).value == TOTAL_INPUTS_VALUE - votingValue - const_MinTxFee,
        OUTPUTS(1).R4[Long].isDefined && OUTPUTS(1).R4[Long].get == votingValue,
        OUTPUTS(1).R5[Coll[GroupElement]].isDefined,
        OUTPUTS(1).R6[Int].isDefined && (OUTPUTS(1).R6[Int].get == HEIGHT || OUTPUTS(1).R6[Int].get == SELF.R6[Int].getOrElse(0))
       ))
       // Checks if members of pk collection are not in redeposit box's R5
       // Essentially proves that the set of GE's in pkList is mutually exclusive from the set of GE's in OUTPUTS(1).R5[Coll[GroupElement]]
       val redepositValid = {
        (pkList: Coll[SigmaProp]) =>
          if(REDEPOSIT_EXISTS){
            pkList.forall{
              (pk: SigmaProp) => OUTPUTS(1).R5[Coll[GroupElement]].get.forall{
                (signerGE: GroupElement) => proveDlog(signerGE) != pk
              }
            }
          }else{
            false
          }
       }
       // returns boolean that checks if given box is a skip vote box
       val isBoxSkip = {
          (box: Box) =>
          allOf(Coll(
          box.value == votingValue,
          blake2b256(box.propositionBytes) == const_consensusPropBytesHashed,
          box.R4[SigmaProp].isDefined,
          box.R5[Coll[Byte]].isDefined,
          box.R6[Coll[Byte]].isDefined,
          // box.R5[Coll[Byte]].get == const_metadataPropBytesHashed
          // box.R6[Coll[Byte]].get == blake2b256(SELF.propositionBytes)

        ))
       }

       val isKeyInPoolState = {
         (pk: SigmaProp) => OUTPUTS(0).R4[Coll[(SigmaProp, Long)]].get.exists{(poolStateVal: (SigmaProp, Long)) => poolStateVal._1 == pk}
       }
       val poolStateValid = {
        (pkList: Coll[SigmaProp]) => pkList.forall(isKeyInPoolState)
       }
       val membersValid = {
        (memberList: Coll[(SigmaProp, Coll[Byte])]) =>
          OUTPUTS(0).R5[Coll[(SigmaProp, Coll[Byte])]].get == memberList
       }
       val signatureStored = {
        (pk: SigmaProp) =>
          if(OUTPUTS(0).R6[SigmaProp].isDefined){
            pk == OUTPUTS(0).R6[SigmaProp].get
          }else{
            false
          }
       }
       val signerInSubPool = {
        (pkList: Coll[SigmaProp]) => atLeast(1, pkList)
       }

       // First voter spending path
       // Ensures creation of voting box, and redeposit holding box with properly set values
       val constructSpendingPath1 = {
        (memberList: Coll[(SigmaProp, Coll[Byte])]) =>
         val pkList = unzipMemberList(memberList)._1
         val signerPK = atLeast(1, pkList)
         val filteredPKs = pkList.filter{
            (pk: SigmaProp) => pk != signerPK
          }
         val boolCheck = allOf(Coll(
              VOTE_EXISTS,
              REDEPOSIT_EXISTS,
              poolStateValid(pkList),
              // membersValid(memberList),
              // redepositValid(filteredPKs),
              signatureStored(signerPK)
            ))

            sigmaProp(boolCheck) && signerInSubPool(pkList)
       }
       // Voting has initiated, so SELF has registers.
       // In 2a, these registers build a voting box and redeposit holding box with updated values
       val constructSpendingPath2a = {
        (memberList: Coll[(SigmaProp, Coll[Byte])]) =>
         val pkList = unzipMemberList(memberList)._1
         val filteredPKs = pkList.filter{
            (pk: SigmaProp) =>
              if(VOTING_STARTED){
                !(SELF.R5[Coll[GroupElement]].get.exists{
                  (signerGE: GroupElement) =>
                    pk == proveDlog(signerGE)
                  }
                 )
              }else{
                 true
              }
          }
         val signerPK = atLeast(1, filteredPKs)
         val newFilteredPKs = filteredPKs.filter{
            (pk: SigmaProp) => pk != signerPK
         }
         val boolCheck = allOf(Coll(
           VOTE_EXISTS,
           REDEPOSIT_EXISTS,
           poolStateValid(filteredPKs),
           membersValid(memberList)
         ))
         sigmaProp(boolCheck && redepositValid(newFilteredPKs) && signatureStored(signerPK)) && signerInSubPool(filteredPKs)
       }
       // In 2b, the last miner has sent their voting box, so no redeposit box is needed.
       val constructSpendingPath2b = {
        (memberList: Coll[(SigmaProp, Coll[Byte])]) =>
          val pkList = unzipMemberList(memberList)._1
          val filteredPKs = pkList.filter{
            (pk: SigmaProp) =>
              if(VOTING_STARTED){
                !(SELF.R5[Coll[GroupElement]].get.exists{
                  (signerGE: GroupElement) =>
                    pk == proveDlog(signerGE)
                  }
                 )
              }else{
                 true
              }
          }
        val signerPK = atLeast(1, filteredPKs)
        val boolCheck = allOf(Coll(
           VOTE_EXISTS,
           poolStateValid(filteredPKs),
           membersValid(memberList)
         ))

         sigmaProp(boolCheck && signatureStored(signerPK)) && signerInSubPool(filteredPKs)
       }
       // In 3, HEIGHT > MAX_VOTING_HEIGHT, so skip-vote boxes can be made for any miner
       val constructSpendingPath3 = {
        (memberList: Coll[(SigmaProp, Coll[Byte])]) =>
        val pkList = unzipMemberList(memberList)._1
        val filteredPKs = pkList.filter{
            (pk: SigmaProp) =>
              if(VOTING_STARTED){
                !(SELF.R5[Coll[GroupElement]].get.exists{
                  (signerGE: GroupElement) =>
                    pk == proveDlog(signerGE)
                  }
                 )
              }else{
                 true
              }
          }

        val boolCheck = allOf(Coll(
           filteredPKs.forall{(pk: SigmaProp) => OUTPUTS.exists{(box: Box) => isBoxSkip(box) && box.R4[SigmaProp].get == pk}}
         ))
         // Check only that skip vote boxes have been properly made and that signer is part of original pkList
         sigmaProp(boolCheck) && signerInSubPool(pkList)
       }

       // Constructs main spending paths together by inputting parameters, used to separate Initiation and Standard operation.
       val constructSpendingBranch = {
       (memberList: Coll[(SigmaProp, Coll[Byte])]) =>

        // Checks if Holding Box Regs defined, indicates voting has begun.
         if(!VOTING_STARTED && HEIGHT < MAX_VOTING_HEIGHT){

         // This must be the first vote during the initiation phase. Therefore we use an unmodified instance of the parameter value: pkList
           constructSpendingPath1(memberList)

         // Not the first vote, but voting is still allowed. We may access the registers of SELF now to get information about this voting transaction.
         }else if(VOTING_STARTED && HEIGHT < MAX_VOTING_HEIGHT){

            // Checks to see if this is not the last vote, meaning that redeposit is needed
            if(SELF.value != SELF.R4[Long].get){
              constructSpendingPath2a(memberList)
            }else{
              // Last vote, so redeposit is not needed.
              constructSpendingPath2b(memberList)
            }
         }else if(HEIGHT >= MAX_VOTING_HEIGHT){
            // HEIGHT >= maximumVotingHeight, so any member of the subpool may create skip votes for members
            // that did not submit their votes.
            constructSpendingPath3(memberList)
         }else{
            sigmaProp(false)
         }

       }
       // This constructs the final SigmaProp that determines whether or not the transaction is valid
       // Uses two spending branches to indicate difference between initiation and standard operation
       if(!INITIATION_ENDED){
          constructSpendingPath1(INITIAL_MEMBERS)
       }else if(METADATA_VALID){
          // R5 of MetaDataBox holds collection of members in this subpool
          // Might have to change registers of metadatabox and holding box to support members.
          val SUBPOOL_MEMBERS = METADATA_BOX.R5[Coll[(SigmaProp, Coll[Byte])]].get
          // constructSpendingBranch(SUBPOOL_MEMBERS)

          constructSpendingPath1(INITIAL_MEMBERS)
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
    val publicKeyGEList = (minerList.map{(addr: Address) => addr.getPublicKeyGE})
    val workerListHashed = workerList.map{(name: String) => Blake2b256(name)}.map{
      (byteArray: Array[Byte]) => newColl(byteArray, ErgoType.byteType())
    }


    val workerColl = newColl(workerListHashed, SpType.STRING_TYPE)
    val minerColl = special.collection.Builder.DefaultCollBuilder.fromArray(publicKeyGEList)

    val consensusPropBytesHashed: Array[Byte] = Blake2b256(consensusAddress.getErgoAddress.script.bytes)
    val metadataPropBytesHashed: Array[Byte] = Blake2b256(metadataAddress.getErgoAddress.script.bytes)
    val constantsBuilder = ConstantsBuilder.create()
    println("===========Generating Holding Address=============")
    println("const_initHeight: "+ (ctx.getHeight + initiationEndingHeight))
    println("const_initMembers: " + minerColl.zip(workerColl))
    println("const_maxVotingHeight: "+ (ctx.getHeight + maxVotingHeight))
    println("const_consensusPropBytesHashed: " + newColl(consensusPropBytesHashed, ErgoType.byteType()))
    println("const_metadataPropBytesHashed: " + newColl(metadataPropBytesHashed, ErgoType.byteType()))

    val compiledContract = ctx.compileContract(constantsBuilder
      .item("const_initiationEndingHeight", ctx.getHeight + initiationEndingHeight)
      .item("const_initMembers", minerColl.zip(workerColl))
      .item("const_MinTxFee", Parameters.MinFee)
      .item("const_consensusPropBytesHashed", newColl(consensusPropBytesHashed, ErgoType.byteType()))
      .item("const_metadataPropBytesHashed", newColl(metadataPropBytesHashed, ErgoType.byteType()))
      .item("const_maxVotingHeight", ctx.getHeight + maxVotingHeight)
      .build(), getHoldingScript)
    compiledContract
  }

  /**
   * Generates a list buffer of redeposit Holding Boxes with properly assigned registers
   * @param ctx Context as Blockchain Context
   * @param holdingBoxes Array of input boxes from holding address
   * @param votingValue Value of vote boxes
   * @param signerAddress Signer address of transaction
   * @param holdingAddress Holding address
   */
  def generateRedepositHoldingBoxes(ctx: BlockchainContext, holdingBoxes: List[InputBox], votingValue: Long,
                                    signerAddress: Address, holdingAddress: Address): ListBuffer[OutBox] = {
    // First box in list, possibly a redeposit box
    val holdingBoxHead = holdingBoxes.head
    val totalValue = holdingBoxes.foldLeft(0L){(accum: Long, box: InputBox) => accum + box.getValue}
    ProveDlog
    val signerAddressList: List[GroupElement] = List(signerAddress.getPublicKeyGE)
    val outBoxList: ListBuffer[OutBox] = ListBuffer.empty[OutBox]
    // If holding box head contains registers, then must be from another redeposit holding box. Therefore values can
    // be obtained from its registers.
    if(holdingBoxes.length == 1 && isRedepositBox(holdingBoxHead)){
      val storedVotingValue = holdingBoxHead.getRegisters.get(0).getValue.asInstanceOf[Long]
      val voterList: List[GroupElement] = holdingBoxHead.getRegisters.get(1).getValue.asInstanceOf[List[GroupElement]]
      val votingStartedHeight = holdingBoxHead.getRegisters.get(2).getValue.asInstanceOf[Int]
      val newVoterList: List[GroupElement] = voterList.++(signerAddressList)
      val newVoterListAsColl = newColl(newVoterList, ErgoType.groupElementType())
      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder

      val ergoVal1 = ErgoValue.of(storedVotingValue)
      val ergoVal2 = ErgoValue.of(newVoterListAsColl, ErgoType.groupElementType())
      val ergoVal3 = ErgoValue.of(votingStartedHeight)
      val regValueList: Array[ErgoValue[_]] = Array(ergoVal1, ergoVal2, ergoVal3)
      outBoxList.append(
        txB.outBoxBuilder()
          .value(totalValue - votingValue - Parameters.MinFee)
          .contract(new ErgoTreeContract(holdingAddress.getErgoAddress.script))
          .registers(regValueList:_*)
          .build())
    }
    // If holding box registers do not exist and length is greater than 1
    else{
      val signerAddressAsColl = newColl(signerAddressList, ErgoType.groupElementType())
      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
      println(votingValue)
      signerAddressAsColl.map(println)
      println(ctx.getHeight)
      println("===========Generating Redeposit Box=============")
      println("redepositValue: "+ (totalValue - votingValue - Parameters.MinFee))
      println("signerAddressColl: " + signerAddressAsColl)
      println("HEIGHT: "+ ctx.getHeight)
      val ergoVal1 = ErgoValue.of(votingValue)
      val ergoVal2 = ErgoValue.of(signerAddressAsColl, ErgoType.groupElementType())
      val ergoVal3 = ErgoValue.of(ctx.getHeight)
      val regValueList: Array[ErgoValue[_]] = Array(ergoVal1, ergoVal2, ergoVal3)
      outBoxList.append(
        txB.outBoxBuilder()
          .value(totalValue - votingValue - Parameters.MinFee)
          .contract(new ErgoTreeContract(holdingAddress.getErgoAddress.script))
          .registers(regValueList:_*)
          .build())
    }
    outBoxList
  }

  /**
   * Generates a list buffer of Voting Boxes with valid registers from given information
   * @param ctx Blockchain context
   * @param minerList Array of miner addresses
   * @param minerShares Array of miner shares
   * @param workerList Array of worker names
   * @param votingValue Value of vote boxes
   * @param signerAddress Address of signer
   * @param metadataAddress Metadata address for subpool
   * @return Returns list voting boxes
   */
  def generateVotingBoxes(ctx: BlockchainContext, minerList: Array[Address], minerShares: Array[Long], workerList: Array[String],
                                 votingValue: Long, signerAddress: Address, voteList: Array[Int], metadataAddress: Address, consensusAddress: Address): ListBuffer[OutBox] = {
    val outBoxList: ListBuffer[OutBox] = ListBuffer.empty[OutBox]

    val sigList = minerList.map(genSigProp)
    val keysToSharesTupleMap: Array[(SigmaProp,Long)] = sigList.zip(minerShares)
    val poolStateCollection: Coll[(SigmaProp, Long)] = newColl(keysToSharesTupleMap, SpType.POOL_VAL_TYPE)
    val workerListHashed = workerList.map{(str: String) => Blake2b256(str)}.map{(byteArray: Array[Byte]) => newColl(byteArray, ErgoType.byteType())}
    val workerBytesCollection: Coll[Coll[Byte]] = newColl(workerListHashed, SpType.STRING_TYPE)
    val sigColl = newColl(sigList, ErgoType.sigmaPropType())
    val votingColl = newColl(voteList, ErgoType.integerType())
    val memberColl = sigColl.zip(workerBytesCollection)
    val metadataBytesHashed = newColl(Blake2b256(metadataAddress.getErgoAddress.script.bytes), ErgoType.byteType())
    println("===========Generating Voting Boxes=============")
    println("poolState: "+ poolStateCollection)
    println("memberColl: " + memberColl)
    println("signer: "+ signerAddress.getPublicKey)
    println("metadataBytesHashed: " + metadataBytesHashed)
    println("Votes: " + votingColl)
    val ergoVal1 = ErgoValue.of(poolStateCollection, SpType.POOL_VAL_TYPE)
    val ergoVal2 = ErgoValue.of(memberColl, SpType.MEMBER_TYPE)
    val ergoVal3 = ErgoValue.of(signerAddress.getPublicKey)
    val ergoVal4 = ErgoValue.of(metadataBytesHashed, ErgoType.byteType())
    val ergoVal5 = ErgoValue.of(votingColl, ErgoType.integerType())
    val regList = List(ergoVal1, ergoVal2, ergoVal3, ergoVal4, ergoVal5)
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    outBoxList.append(
      txB.outBoxBuilder()
        .value(votingValue)
        .contract(new ErgoTreeContract(consensusAddress.getErgoAddress.script))
        .registers(regList:_*)
        .build())
    outBoxList
  }

  /**
   * Generates list of skip votes for all public keys not found in redeposit box's R5
   * If redeposit box does not exist, then all votes are skip votes
   * @param ctx Blockchain Context
   * @param holdingBoxes List of holding boxes to use in tx, may contain redeposit box
   * @param minerList List of miner addresses in subpool
   * @param metadataAddress Address of metadata
   * @param holdingAddress Address of holding contract
   * @return Returns list of registers for skip votes
   */
  def generateAllSkipVoteBoxes(ctx: BlockchainContext, holdingBoxes: List[InputBox], minerList: List[Address],
                            metadataAddress: Address, holdingAddress: Address, consensusAddress: Address): ListBuffer[OutBox] = {
    val outBoxList: ListBuffer[OutBox] = ListBuffer.empty[OutBox]
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val metadataBytesHashed = newColl(Blake2b256(metadataAddress.getErgoAddress.script.bytes), ErgoType.byteType())
    val holdingBytesHashed = newColl(Blake2b256(metadataAddress.getErgoAddress.script.bytes), ErgoType.byteType())
    if(holdingBoxes.length == 1 && isRedepositBox(holdingBoxes.head)) {
      val redepositBox = holdingBoxes.head
      val voterList = redepositBox.getRegisters.get(1).getValue.asInstanceOf[List[SigmaProp]]
      val storedVotingValue = redepositBox.getRegisters.get(0).getValue.asInstanceOf[Long]
      val minersToSkip = minerList.filter { (addr: Address) => !(voterList.contains(genSigProp(addr))) }


      minersToSkip.foreach {
        (addr: Address) =>
          val ergoVal1 = ErgoValue.of(addr.getPublicKey)
          val ergoVal2 = ErgoValue.of(metadataBytesHashed, ErgoType.byteType())
          val ergoVal3 = ErgoValue.of(holdingBytesHashed, ErgoType.byteType())
          val regList = List(ergoVal1, ergoVal2, ergoVal3)

          outBoxList.append(
            txB.outBoxBuilder()
              .value(storedVotingValue)
              .contract(new ErgoTreeContract(consensusAddress.getErgoAddress.script))
              .registers(regList: _*)
              .build())
      }
    }else{
      // If the box list given is not a redeposit box, then we can assume that there are no redeposit boxes made
      val totalValue = holdingBoxes.foldLeft(0L){(accum: Long, box: InputBox) => accum + box.getValue}
      val votingValue = getVotingValue(totalValue, minerList.length)
      minerList.foreach {
        (addr: Address) =>
          val ergoVal1 = ErgoValue.of(addr.getPublicKey)
          val ergoVal2 = ErgoValue.of(metadataBytesHashed, ErgoType.byteType())
          val ergoVal3 = ErgoValue.of(holdingBytesHashed, ErgoType.byteType())
          val regList = List(ergoVal1, ergoVal2, ergoVal3)

          outBoxList.append(
            txB.outBoxBuilder()
              .value(votingValue)
              .contract(new ErgoTreeContract(consensusAddress.getErgoAddress.script))
              .registers(regList: _*)
              .build())
      }
    }
    outBoxList
  }



  /**
   * Returns holding boxes to be used in voting. If a redeposit box is found, it is prioritized.
   * If there are no redeposit boxes, than all holding boxes are used.
   * @param boxList List of unspent boxes from holding address
   */
  def getHoldingBoxesForVotes(boxList: List[InputBox]): List[InputBox] = {
    val redepositExists = boxList.exists(isRedepositBox)
    // If redeposit exists, we return a list containing the first redeposit box found
    if(redepositExists)
      List(boxList.filter(isRedepositBox).head)
    else
      boxList
  }

  /**
   * Checks if given box is a redeposit box or not
   * @param box box to check
   * @return boolean that checks if reg4, reg5, and reg6 are properly defined
   */
  def isRedepositBox(box: InputBox): Boolean = {
    val regs = box.getRegisters
    if(regs.size() == 3) {
      val reg4 = regs.get(0).getType == ErgoType.longType()
      val reg5 = regs.get(1).getType == ErgoType.collType(ErgoType.sigmaPropType())
      val reg6 = regs.get(2).getType == ErgoType.integerType()
      reg4 && reg5 && reg6
    }else
      false
  }

  def getVotingValue(totalVal: Long, numMembers:Int): Long = {
    (totalVal / numMembers) - Parameters.MinFee
  }


}
