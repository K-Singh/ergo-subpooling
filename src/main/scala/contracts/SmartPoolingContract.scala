package contracts

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import scorex.crypto.hash.Blake2b256
import special.collection.Coll
import special.sigma.{GroupElement, SigmaProp}

import scala.collection.mutable.ListBuffer

object SmartPoolingContract {

  def getSmartPoolingScript: String = {
    /**
     * SmartPool Holding script
     * - This script requires 1 constant:
     * - Metadata Box Proposition Bytes
     *    -- Used to verify input 0 as metadata box
     *    -- Also used to verify input 1 is a box protected by a pool operator(who are stored in R8 of the metadata)
     *
     * The SmartPool holding script takes information from the metadata box and command box to distribute
     * rewards to members of the smart pool.
     *
     * SmartPool holding boxes may only be spent in transactions that have both a metadata box and a command box
     * in inputs 0 and 1 of the transaction. The holding box verifies these boxes to ensure that it is only spent
     * in a valid transaction. The holding boxes' main job is to verify the validity of the distributed outputs. The
     * holding box ensures that the output boxes of the transaction it is spent in follow the consensus supplied
     * by the command box.
     *
     * During consensus, pool fees are retrieved from the metadata box(Not the command box, so as to ensure pool fees
     * cannot change until the next epoch). Pool fees are represented by an integer. The minimum value of this
     * integer is expected to be 1 and this is checked by the metadata box. A value of 1 represents 1/1000 of the
     * total value of the consensus transaction. Therefore the minimum pool fee must be 0.1% or 0.001 * the total inputs value.
     *
     */
    val script: String =
      s"""
      {
        val VALID_INPUTS_SIZE = INPUTS.size > 2
        val TOTAL_INPUTS_VALUE = INPUTS.fold(0L, {(accum: Long, box:Box) => accum + box.value})

        val metadataExists =
          if(VALID_INPUTS_SIZE){
            INPUTS(0).propositionBytes == const_metadataPropBytes
          }else{
            false
          }
        val metadataValid =
          if(metadataExists){
            allOf(Coll(
              INPUTS(0).R4[Coll[(Coll[Byte], Long)]].isDefined,       // Last consensus
              INPUTS(0).R5[Coll[(Coll[Byte], Coll[Byte])]].isDefined, // Current members
              INPUTS(0).R6[Coll[(Coll[Byte], Int)]].isDefined,        // Pool fees
              INPUTS(0).R7[Coll[Int]].isDefined,                      // Pool Information
              INPUTS(0).R8[Coll[(Coll[Byte], Coll[Byte])]].isDefined  // Pool operators
            ))
          }else{
            false
          }
        val commandExists =
          if(metadataValid){
            val POOL_OPERATORS = INPUTS(0).R8[Coll[(Coll[Byte], Coll[Byte])]].get
            val COMMAND_BYTES = INPUTS(1).propositionBytes
            // Verifies that the command boxes proposition bytes exists in the pool operators
            val commandOwnedByOperators = POOL_OPERATORS.exists{
              (op: (Coll[Byte], Coll[Byte]) =>
                op._1 == COMMAND_BYTES
            }
            commandOwnedByOperators
          }else{
            false
          }
        val commandValid =
          if(commandExists){
            allOf(Coll(
              INPUTS(1).R4[Coll[(Coll[Byte], Long)]].isDefined,       // New consensus
              INPUTS(1).R5[Coll[(Coll[Byte], Coll[Byte])]].isDefined, // New members list
              INPUTS(1).R6[Coll[(Coll[Byte], Int)]].isDefined,        // New Pool fees
              INPUTS(1).R7[Coll[Int]].isDefined,                      // New Pool Information
              INPUTS(1).R8[Coll[(Coll[Byte], Coll[Byte])]].isDefined  // New Pool operators
            ))
          }else{
            false
          }
        val consensusValid =
          if(commandValid){
            val currentConsensus = INPUTS(1).R4[Coll[(Coll[Byte], Long)]].get // New consensus grabbed from current command
            val currentPoolFees = INPUTS(0).R6[Coll[(Coll[Byte], Int)]].get // Pool fees grabbed from current metadata

            val feeList: Coll[(Coll[Byte], Long)] = currentPoolFees.map{
              // Pool fee is defined as x/1000 of total inputs value.
              (poolFee: (Coll[Byte], Int)) => ( poolFee._1 , ((poolFee._2 * TOTAL_INPUTS_VALUE)/1000) )
            }

            // Total amount in holding after fees are taken out
            val totalValAfterFees = ((feeList.fold(TOTAL_INPUTS_VALUE, {
              (accum: Long, poolFeeVal: (Coll[Byte], Long)) => accum - poolFeeVal._2
            })) - const_MinTxFee)

            val totalShares = currentConsensus.fold(0L, {(accum: Long, consVal: (Coll[Byte], Long)) => accum + consVal._2})

            // Returns some value that is a percentage of the total rewards after the fees.
            // The percentage used is the proportion of the share number passed in over the total number of shares.
            def getValueFromShare(shareNum: Long) = {
              val newBoxValue = (((totalValAfterFees) * (shareNum)) / (totalShares))
              newBoxValue
            }

            // Maps each propositionByte stored in the consensus to a value obtained from the shares.
            val boxValueMap = currentConsensus.map{
              (consVal: (Coll[Byte], Long)) =>
                (consVal._1, getValueFromShare(consVal._2)
            }

            // This verifies that each member of the consensus has some output box
            // protected by their script and that the value of each box is the
            // value obtained from consensus.
            // This boolean value is returned and represents the main sigma proposition of the smartpool holding
            // contract.
            // This boolean value also verifies that poolFees are paid and go to the correct boxes.
            boxValueMap.forall{
              (boxVal: (Coll[Byte], Long)) =>
                OUTPUTS.exists{
                  (box: Box) => box.propositionBytes == boxVal._1 && box.value == boxVal._2
                }
            } && feeList.forall{
              (poolFeeVal: (Coll[Byte], Long)) =>
                OUTPUTS.exists{
                  (box: Box) => box.propositionBytes == poolFeeVal._1 && box.value == poolFeeVal._2
                }
            }
          }else{
            false
          }
        sigmaProp(consensusValid)
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
      .build(), getSmartPoolingScript)
    compiledContract
  }

  /**
   * Generates a list buffer of redeposit Holding Boxes with properly assigned registers
   * @param ctx Context as Blockchain Context
   * @param holdingBoxes Array of input boxes from holding address
   * @param votingValue Value of vote boxes
   * @param signerAddress Signer address of transaction
   * @param minerList Array of miner addresses
   * @param holdingAddress Holding address
   */
  def generateRedepositHoldingBoxes(ctx: BlockchainContext, holdingBoxes: List[InputBox], signerAddress: Address,
                                    minerList: Array[Address], holdingAddress: Address): ListBuffer[OutBox] = {
    // First box in list, possibly a redeposit box
    val holdingBoxHead = holdingBoxes.head

    val totalValue = holdingBoxes.foldLeft(0L){(accum: Long, box: InputBox) => accum + box.getValue}
    val votingValue = getVotingValue(totalValue, minerList.length)
    val signerAddressList: List[GroupElement] = List(signerAddress.getPublicKeyGE)
    val signerAddressColl = newColl(signerAddressList, ErgoType.groupElementType())
    val outBoxList: ListBuffer[OutBox] = ListBuffer.empty[OutBox]
    // If holding box head contains registers, then must be from another redeposit holding box. Therefore values can
    // be obtained from its registers.
    if(holdingBoxes.length == 1 && isRedepositBox(holdingBoxHead)){
      val storedVotingValue = holdingBoxHead.getRegisters.get(0).getValue.asInstanceOf[Long]
      val voterList: Coll[GroupElement] = holdingBoxHead.getRegisters.get(1).getValue.asInstanceOf[Coll[GroupElement]]
      val votingStartedHeight = holdingBoxHead.getRegisters.get(2).getValue.asInstanceOf[Int]
      val newVoterList: Coll[GroupElement] = voterList.append(signerAddressColl)
      if(storedVotingValue == holdingBoxHead.getValue - Parameters.MinFee){
        println("Last Member Is Putting In Vote, No Redeposit Needed!")
        return outBoxList
      }
      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
      println("===========Generating Next Redeposit Box=============")
      println("redepositValue: "+ (totalValue - votingValue - Parameters.MinFee))
      println("newVoterListAsColl: " + newVoterList)
      println("HEIGHT: "+ ctx.getHeight)
      val ergoVal1 = ErgoValue.of(storedVotingValue)
      val ergoVal2 = ErgoValue.of(newVoterList, ErgoType.groupElementType())
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
      println("===========Generating Initial Redeposit Box=============")
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
  def generateVotingBoxes(ctx: BlockchainContext, holdingBoxes: List[InputBox], minerList: Array[Address], minerShares: Array[Long], workerList: Array[String],
                          signerAddress: Address, voteList: Array[Int], metadataAddress: Address, consensusAddress: Address): ListBuffer[OutBox] = {
    val outBoxList: ListBuffer[OutBox] = ListBuffer.empty[OutBox]

    val holdingBoxHead = holdingBoxes.head
    val totalValue = holdingBoxes.foldLeft(0L){(accum: Long, box: InputBox) => accum + box.getValue}
    var votingValue = getVotingValue(totalValue, minerList.length)

    if(holdingBoxHead.getRegisters.size() > 0 && holdingBoxHead.getRegisters.get(0).getType == ErgoType.longType()){
      votingValue = holdingBoxHead.getRegisters.get(0).getValue.asInstanceOf[Long]
    }

    val geList = minerList.map((addr: Address) => addr.getPublicKeyGE)
    val sigList = minerList.map(genSigProp)
    val keysToSharesTupleMap: Array[(SigmaProp,Long)] = sigList.zip(minerShares)
    val poolStateCollection: Coll[(SigmaProp, Long)] = newColl(keysToSharesTupleMap, SpType.POOL_VAL_TYPE)
    val workerListHashed = workerList.map{(str: String) => Blake2b256(str)}.map{(byteArray: Array[Byte]) => newColl(byteArray, ErgoType.byteType())}
    val workerBytesCollection: Coll[Coll[Byte]] = newColl(workerListHashed, SpType.STRING_TYPE)
    val geColl = newColl(geList, ErgoType.groupElementType())
    val votingColl = newColl(voteList, ErgoType.integerType())
    val memberColl = geColl.zip(workerBytesCollection)
    val metadataBytesHashed = newColl(Blake2b256(metadataAddress.getErgoAddress.script.bytes), ErgoType.byteType())
    println("===========Generating Voting Boxes=============")
    println("poolState: "+ poolStateCollection)
    println("memberColl: " + memberColl)
    println("signer: "+ signerAddress.getPublicKeyGE)
    println("metadataBytesHashed: " + metadataBytesHashed)
    println("Votes: " + votingColl)
    val ergoVal1 = ErgoValue.of(poolStateCollection, SpType.POOL_VAL_TYPE)
    val ergoVal2 = ErgoValue.of(memberColl, ErgoType.pairType(ErgoType.groupElementType(), ErgoType.collType(ErgoType.byteType())))
    val ergoVal3 = ErgoValue.of(signerAddress.getPublicKeyGE)
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
      val voterList = redepositBox.getRegisters.get(1).getValue.asInstanceOf[List[GroupElement]]
      val storedVotingValue = redepositBox.getRegisters.get(0).getValue.asInstanceOf[Long]
      val minersToSkip = minerList.filter { (addr: Address) => !(voterList.contains(addr.getPublicKeyGE)) }


      minersToSkip.foreach {
        (addr: Address) =>
          val ergoVal1 = ErgoValue.of(addr.getPublicKeyGE)
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
          val ergoVal1 = ErgoValue.of(addr.getPublicKeyGE)
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
      val reg5 = regs.get(1).getType == ErgoType.collType(ErgoType.groupElementType())
      val reg6 = regs.get(2).getType == ErgoType.integerType()
      reg4 && reg5 && reg6
    }else
      false
  }

  def getVotingValue(totalVal: Long, numMembers:Int): Long = {
    (totalVal / numMembers) - Parameters.MinFee
  }


}
