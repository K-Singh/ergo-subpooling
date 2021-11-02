package contracts

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import scalan.RType
import scalan.RType.asType
import scorex.crypto.hash.Blake2b256
import sigmastate.SType.SigmaBooleanRType
import sigmastate.Values
import sigmastate.Values.SigmaBoolean
import sigmastate.eval.SigmaDsl
import special.collection.Coll
import special.sigma.{SigmaProp, SigmaPropRType}

import scala.collection.mutable.ListBuffer

object MetadataContract {

  def getMetadataScript: String = {
    /**
     *This is the metadata contract, representing the metadata box associated with some pair of holding and consensus
     * boxes.
     *
     * R4 Of the metadata box holds a modified version of the last share consensus for this subpool.
     * R5 Of the metadata box holds the list of members associated with this subpool. Any vote-based adding or removing
     * of members will be reflected here. R5 therefore holds the member consensus for this subpool.
     * R6 Of the metadata box holds the voting consensus, a collection of integers representing important parameters
     * for the subpool
     * R7 Of the metadata box holds the height at which this metadata box was made.
     * R8 Of the metadata box holds the current epoch of this subpool.
     *
     * The metadata box may only be spent to create another metadata box of the same value and with an epoch equal
     * to this metadata box's epoch + 1
     *
     * A metadata box is only considered valid if it holds proper values inside its registers.
     */
    val script: String = s"""
    {
     val MEMBER_LIST = SELF.R5[Coll[(SigmaProp, Coll[Byte])]].get
     val INITIAL_MEMBERS: Coll[(SigmaProp, Coll[Byte])] = const_initMembers.map{
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
     val MINER_LIST = unzipMemberList(MEMBER_LIST)._1
     val INITIAL_MINERS = unzipMemberList(INITIAL_MEMBERS)._1
     val NON_METADATA_INPUTS = INPUTS.filter{(box:Box) => box.propositionBytes != SELF.propositionBytes}
     // Check if non metadata inputs are spent by valid voting box
     val inputsValid = NON_METADATA_INPUTS.forall{(box:Box) => allOf(Coll(
      box.R4[Coll[(SigmaProp, Long)]].isDefined || box.R4[SigmaProp].isDefined,
      box.R5[Coll[(SigmaProp, Coll[Byte])]].get == MEMBER_LIST || box.R5[Coll[Byte]].get == blake2b256(SELF.propositionBytes),
      (box.R6[Coll[Int]].isDefined && box.R7[Coll[Byte]].get == blake2b256(SELF.propositionBytes)) || box.R6[Coll[Byte]].isDefined,
      SELF.R9[Coll[Byte]].get == blake2b256(box.propositionBytes)
      ))}
     // Check if outputs creates a new metadata box with proper registers defined. Voting address may not change between
     // epochs.
     val outputsValid = OUTPUTS.size == INPUTS.size && OUTPUTS.exists{
      (box: Box) => allOf(Coll(
        box.R4[Coll[(SigmaProp, Long)]].isDefined,
        box.R5[Coll[(SigmaProp, Coll[Byte])]].isDefined,
        box.R6[Coll[Int]].isDefined,
        box.R7[Int].isDefined,
        box.R7[Int].get == HEIGHT,
        box.R8[Int].isDefined,
        box.R8[Int].get == SELF.R8[Int].get + 1,
        box.R9[Coll[Byte]].get == SELF.R9[Coll[Byte]].get,
        box.value == SELF.value
        ))}
     // Either a new metadata box is created by one of the miners in the subpool, or the metadata box may be spent by one of the initial members to delete it
     (atLeast(1, MINER_LIST) && sigmaProp(inputsValid && outputsValid)) || (atLeast(1, INITIAL_MINERS) && sigmaProp(!inputsValid && !outputsValid))
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
