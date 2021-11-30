package contracts

import org.ergoplatform.appkit._
import scorex.crypto.hash.Blake2b256
import special.sigma.SigmaProp

object StratumMetadataContract {

  def getMetadataScript: String = {
    /**
     * This is the metadata contract, representing the metadata box associated with the stratum holding contract.
     *
     *
     * R4 Of the metadata box holds a modified version of the last share consensus for this subpool.
     * -- A share consensus is some Coll[(Coll[Byte], Long)] that maps propBytes to share numbers.
     * R5 Of the metadata box holds the list of members associated with this subpool.
     *  -- In the Stratum Smart Pool, A member is defined as some (Coll[Byte], Coll[Byte])
     *  ---- member._1 are the proposition bytes for some box. This may be a box protected by a subpool or a box protected.
     *  ---- member._2 represents the name of the member. This may be displayed
     *  -- This flexibility allows us to add any address to the smart pool, whether that be a normal P2PK or a P2S.
     *  -- we can therefore make subpools under the smart pool to allow multiple levels of distribution.
     *  -- this solution may be brought to subpools in the future to allow multiple levels of subpooling.
     *
     * R6 Is a collection of pool fees.
     * -- Each pool fee is some (Coll[Byte], Int) representing some boxes propBytes that receives the Integer value
     * -- divided by 1000 and multiplied by the total transaction value.
     *
     * R7 Is a Coll[Int] representing Pool Information.
     * -- Element 0 of the collection represents the current Pool Epoch. This value must increase in the spending Tx.
     * -- Element 1 of the collection represents the height that this Epoch started.
     * -- Element 2 of the collection represents the height the subpool was created.
     * -- * Element 3 of the collection represents the pool min voting threshold. This is an optional value that
     * -- * represents some variable x such that atLeast((x/100)*memberCollection.size(), memberCollection). This
     * -- * allows a transaction to spend the metadata box(and therefore create a new one or destroy the smart pool).
     * -- * Essentially, if enough members can be gathered, the members may control their own subpool.
     * -- Element 3+ are not looked at. This information may be stored and parsed according to the smart pool owner.
     *
     * -- * R8 is a collection members representing the pool operators. Each pool operator may send commands
     * -- to the pool
     *
     * A metadata box is only considered valid if it holds proper values inside its registers. Invalid metadata boxes
     * may be spent by anybody.
     *
     * If a metadata box is valid, it may spent in a transaction to create a new metadata box. Only the metadata
     * box itself verifies that is is valid and that the transaction has a valid layout according to the smart pool
     * protocol. This is the metadata boxes' unique job during any consensus transaction. It verifies that
     * input 0 is SELF and has valid registers, input 1 is some command box belonging to a pool operator, and inputs
     * 2 and onwards are all SmartPool Holding contracts. The metadata box also verifies that a new metadata box with
     * proper registers is created in the outputs.
     *
     * The metadata box does not perform consensus and does not verify any outputs other than there being another
     * valid metadata box in the output. A new valid metadata box is created using information from the old metadata box
     * and information from the command box. The validity of the command box must therefore also be checked by the metadata
     * box.
     */
    val script: String = s"""
    {
      val selfValid = allOf(Coll(
        SELF.R4[Coll[(Coll[Byte], Long)]].isDefined,        // Last consensus
        SELF.R5[Coll[(Coll[Byte], Coll[Byte])]].isDefined,  // Current members
        SELF.R6[Coll[(Coll[Byte], Int)]].isDefined,         // Pool fees
        SELF.R7[Coll[Int]].isDefined,                       // Pool Information
        SELF.R8[Coll[(Coll[Byte], Coll[Byte])]].isDefined,  // Pool operators
        INPUTS(0) == SELF
      ))
      val commandExists =
        if(selfValid){
          val POOL_OPERATORS = SELF.R8[Coll[(Coll[Byte], Coll[Byte])]].get
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

      val newMetadataExists = OUTPUTS(0).propositionBytes == SELF.propositionBytes
      val newMetadataValid =
        if(newMetadataExists){
          allOf(Coll(
            OUTPUTS(0).R4[Coll[(Coll[Byte], Long)]].isDefined,
            OUTPUTS(0).R5[Coll[(Coll[Byte], Coll[Byte])]].isDefined,
            OUTPUTS(0).R6[Coll[(Coll[Byte], Int)]].isDefined,
            OUTPUTS(0).R7[Coll[Int]].isDefined,
            OUTPUTS(0).R8[Coll[(Coll[Byte], Coll[Byte])]].isDefined,
            OUTPUTS(0).value == SELF.value
          ))
        }else{
          false
        }
      // This boolean verifies that important metadata is preserved
      // during the creation of the new metadata box.
      val metadataIsPreserved =
        if(newMetadataValid){
          val currentPoolInfo = SELF.R7[Coll[Int]].get
          val newPoolInfo = OUTPUTS(0).R7[Coll[Int]].get

          // verifies that epoch is increased by 1
          val epochPreserved = newPoolInfo(0) == currentPoolInfo(0) + 1

          // New epoch height is stored and is greater than last height
          val epochHeightStored = newPoolInfo(1) <= HEIGHT && newPoolInfo(1) > currentPoolInfo(1)

          // creation epoch height stays same between spending tx
          val creationHeightPreserved = newPoolInfo(2) == currentPoolInfo(2)

          epochPreserved && epochHeightStored && creationHeightPreserved
        }else{
          false
        }

      // This boolean verifies that the new member list
      // is consistent with consensus. That is, no new members are added
      // unless they also exist in the consensus that occurred during this epoch.
      // This ensures that every member of the subpool has a verifiable amount of shares they
      // received a payout for. Even if that verifiable amount is 0.
      val membersInConsensus =
        if(commandValid){
          val newConsensus = INPUTS(1).R4[Coll[(Coll[Byte], Long)]].get
          val newMembers = INPUTS(1).R5[Coll[(Coll[Byte], Coll[Byte])]].get
          newMembers.forall{
            (member: (Coll[Byte], Coll[Byte])) =>
              newConsensus.exists{
                (consVal: (Coll[Byte], Long)) =>
                  consVal._1 == member._1
              }
          }
        }else{
          false
        }

      // Verify that the registers in the command box are stored in the new metadata box
      val newMetadataFromCommand =
        if(membersInConsensus && metadataIsPreserved){
          allOf(Coll(
            OUTPUTS(0).R4[Coll[(Coll[Byte], Long)]].get == INPUTS(1).R4[Coll[(Coll[Byte], Long)]].get,
            OUTPUTS(0).R5[Coll[(Coll[Byte], Coll[Byte])]].get == INPUTS(1).R5[Coll[(Coll[Byte], Coll[Byte])]].get,
            OUTPUTS(0).R6[Coll[(Coll[Byte], Int)]].get == INPUTS(1).R6[Coll[(Coll[Byte], Int)]].get,
            OUTPUTS(0).R7[Coll[Int]].get == INPUTS(1).R7[Coll[Int]].get,
            OUTPUTS(0).R8[Coll[(Coll[Byte], Coll[Byte])]].get == INPUTS(1).R8[Coll[(Coll[Byte], Coll[Byte])]].get
        }else{
          false
        }

      if(selfValid){
        // We verify that the metadata box follows the proper consensus
        sigmaProp(newMetadataFromCommand)
      }else{
        sigmaProp(true)
      }
    }
      """.stripMargin
    script
  }

  /**
   * Generates Metadata contract
   * @param ctx Blockchain context used to generate contract
   * @param memberList Array of tuples mapping Addresses to Worker Names.
   * @param maxVotingHeight maximum height until voting is stopped set during creation
   * @param initiationEndingHeight end of initiation height set during creation
   * @param consensusAddress address of consensus
   * @param metadataAddress address of metadata
   * @return Compiled ErgoContract of Holding Smart Contract
   */
  def generateMetadataContract(ctx: BlockchainContext, memberList: Array[(Address, String)]): ErgoContract = {
    val membersUnzipped: (Array[Address], Array[String]) = memberList.unzip
    val minerList = membersUnzipped._1
    val workerList = membersUnzipped._2
    val publicKeyList = (minerList.map{(addr: Address) => addr.getPublicKeyGE})
    val workerListHashed = workerList.map{(name: String) => Blake2b256(name)}.map{
      (byteArray: Array[Byte]) => special.collection.Builder.DefaultCollBuilder.fromArray(byteArray)
    }

    val workerColl = special.collection.Builder.DefaultCollBuilder.fromArray(workerListHashed)
    val minerColl = special.collection.Builder.DefaultCollBuilder.fromArray(publicKeyList)
    val membersRezipped = minerList.zip(workerListHashed)
    val constantsBuilder = ConstantsBuilder.create().item("const_initMembers", minerColl.zip(workerColl))
      .build()

    val compiledContract = ctx.compileContract(constantsBuilder, getMetadataScript)

    compiledContract
  }

  /**
   * Generates a redeposit Holding Box with properly assigned registers
   * @param ctx Context as Blockchain Context
   * @param metaDataBox MetadataBox to obtain info from.
   */
  def getMetaDataInfo(ctx: BlockchainContext, metaDataBox: InputBox): (List[(SigmaProp, Long)], List[(SigmaProp, Array[Byte])], List[Int], Int, Int) = {

    val shareConsensus = metaDataBox.getRegisters.get(0).getValue.asInstanceOf[List[(SigmaProp, Long)]]
    val memberConsensus = metaDataBox.getRegisters.get(1).getValue.asInstanceOf[List[(SigmaProp, Array[Byte])]]
    val votingConsensus = metaDataBox.getRegisters.get(2).getValue.asInstanceOf[List[Int]]
    val heightCreated = metaDataBox.getRegisters.get(3).getValue.asInstanceOf[Int]
    val currentEpoch = metaDataBox.getRegisters.get(4).getValue.asInstanceOf[Int]
    (shareConsensus, memberConsensus, votingConsensus, heightCreated, currentEpoch)
    }


}
