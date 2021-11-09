package contracts

import contracts.SpType.{MEMBER_LIST_TYPE, MEMBER_POLL_TYPE, MEMBER_TYPE, POOL_STATE_TYPE, POOL_VAL_TYPE, STRING_TYPE}
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit._
import scalan.RType
import scorex.crypto.hash.Blake2b256
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.eval.CostingSigmaDslBuilder.proveDlog
import special.collection.Coll
import special.sigma.{GroupElement, SigmaProp, SigmaPropRType}

import scala.collection.JavaConverters.iterableAsScalaIterableConverter
import scala.collection.mutable.ListBuffer
import special.collection.Builder._
object VotingContract {

  def getVotingScript : String = {
    /**
     * When all members have sent votes, consensus will begin. If a member does not send a vote, then any member of the
     * subpool may send the missing member's payout as a loss box. During consensus, a loss box will not be considered
     * a vote. The value of the loss box will be added to each members payout evenly.
     */
    val script: String = s"""
      {
       // ================= This area defines important constants used throughout the smart contract =================
       val TOTAL_INPUTS_VALUE = INPUTS.fold(0L, {(accum: Long, box: Box) => accum + box.value})

       // Checks if Metadata exists and that only 1 Metadata box is being used in this transaction using hashed prop bytes
       val METADATA_VALID = INPUTS.filter{(box:Box) => blake2b256(box.propositionBytes) == const_metadataPropBytesHashed}.size == 1

       val METADATA_BOX = if(METADATA_VALID){
          INPUTS.filter{(box:Box) => blake2b256(box.propositionBytes) == const_metadataPropBytesHashed}(0)
        }else{
        // Technically, because we always check if the metadata is valid before using it, this path is never used.
        // It was however included so that no error is thrown if the metadata box does not exist.
          SELF
        }

       // Unzips list of members(tuples of type (SigmaProp, Coll[Byte]]) into tuple of type (Coll[SigmaProp], Coll[Coll[Byte]])
       val unzipMemberList = {
        (memberMap: Coll[(GroupElement, Coll[Byte])]) =>
          (memberMap.map{
            (memVote: (GroupElement, Coll[Byte])) =>
              memVote._1
          },
          memberMap.map{
            (memVote: (GroupElement, Coll[Byte])) =>
              memVote._2
          })
       }
       val MEMBER_LIST = if(METADATA_VALID){
          METADATA_BOX.R5[Coll[(GroupElement, Coll[Byte])]].get
       }else{
          const_initMembers
       }
       val MINER_LIST = unzipMemberList(MEMBER_LIST)._1

       val WORKER_LIST = unzipMemberList(MEMBER_LIST)._2

       val SKIP_PROTOCOL = if(METADATA_VALID){
          METADATA_BOX.R6[Coll[Int]].get(1)
       }else{
          const_skipProtocol
       }
       val MAX_VOTING_HEIGHT = if(METADATA_VALID){
          METADATA_BOX.R6[Coll[Int]].get(0)
       }else{
          const_maxVotingHeight
       }


       val ALL_VOTES_FROM_TX = INPUTS.filter{(box:Box) => blake2b256(box.propositionBytes) != const_metadataPropBytesHashed}
       val SKIP_VOTES_FROM_TX = ALL_VOTES_FROM_TX.filter{(box:Box) => box.R4[GroupElement].isDefined && box.R5[Coll[Byte]].isDefined}
       val NORMAL_VOTES_FROM_TX = ALL_VOTES_FROM_TX.filter{(box:Box) => box.R4[Coll[(SigmaProp, Long)]].isDefined && box.R5[Coll[(GroupElement, Coll[Byte])]].isDefined}
       // Votes From Tx that add or remove members. A member is some (SigmaProp, Coll[Byte]) that maps a PublicKey to a hashed worker name
       val MEMBER_VOTES_FROM_TX = NORMAL_VOTES_FROM_TX.filter{(box:Box) => box.R9[(Coll[(GroupElement, Coll[Byte])], Coll[(GroupElement, Coll[Byte])])].isDefined}

       // ================= This area is for member consensus, determining which members must be added and removed by vote =================

       // The Member Poll represents a pair of voting maps that will map public keys to worker names for members that will be added or removed to this subpool.
       // Element 1 of the MemberPoll is the list of all members that the given vote wishes to add to this subpool,
       // Element 2 of the MemberPoll is the list of all members that the given vote wishes to remove from this subpool
       val getMemberPoll = {
        (voteBox: Box) => voteBox.R9[(Coll[(GroupElement, Coll[Byte])], Coll[(GroupElement, Coll[Byte])])].get
       }

       val getAddedMembers = {
        (memPoll: (Coll[(GroupElement, Coll[Byte])], Coll[(GroupElement, Coll[Byte])])) =>
          memPoll._1
       }
       val getRemovedMembers = {
        (memPoll: (Coll[(GroupElement, Coll[Byte])], Coll[(GroupElement, Coll[Byte])])) =>
          memPoll._2
       }


       // Takes box, removes all invalid members from its R9 MemberPoll. Then returns a new MemberPoll from the box.
       val removeInvalidMembers = {
        (box: Box) =>
          val filteredAdditions = getMemberPoll(box)._1.filter{
            (memVote: (GroupElement, Coll[Byte])) => !(MINER_LIST.exists{(pk: GroupElement) => pk == memVote._1})
          }.filter{
            (memVote: (GroupElement, Coll[Byte])) => !(WORKER_LIST.exists{(worker: Coll[Byte]) => worker == memVote._2})
          }
          val filteredRemovals = getMemberPoll(box)._2.filter{
            (memVote: (GroupElement, Coll[Byte])) => MINER_LIST.exists{(pk: GroupElement) => pk == memVote._1}
          }.filter{
            (memVote: (GroupElement, Coll[Byte])) => WORKER_LIST.exists{(worker: Coll[Byte]) => worker == memVote._2}
          }
          (filteredAdditions, filteredRemovals)
        }


      // List of all MemberPolls with invalid members removed.
      val TOTAL_MEMPOLLS: Coll[(Coll[(GroupElement, Coll[Byte])], Coll[(GroupElement, Coll[Byte])])] = MEMBER_VOTES_FROM_TX.map(removeInvalidMembers)
      // Unflattened list of Memvotes
      val ADDITION_MEMVOTES = TOTAL_MEMPOLLS.map(getAddedMembers)
      val REMOVAL_MEMVOTES = TOTAL_MEMPOLLS.map(getRemovedMembers)

      // Custom flatten function using flatMap and coll.indices property in order to get around flatMap restrictions.
      val flattenMemvotes = {
        (nestedMemvotes: Coll[Coll[(GroupElement, Coll[Byte])]]) =>
        val flatIndices = nestedMemvotes.flatMap{
          (memVoteList: Coll[(GroupElement, Coll[Byte])]) =>
            memVoteList.indices
        }
        val sizeColl = nestedMemvotes.map{
          (memVoteList: Coll[(GroupElement, Coll[Byte])]) =>
            memVoteList.size
        }
        // These indices represent index of parent collection. For example if we wished to access some element by doing
        // Coll(x)(y) this would represent x
        val parentIndices = sizeColl.indices

        // and this would represent y
        val childIndices = flatIndices.indices

        // Create lower and upper bounds for indices. These bounds defined where a certain element of the parent collection
        // is accessed.

        val rangeCollUpperBound = parentIndices.map{
          (idx: Int) =>
            // Take slices of sizeColl from (0,1) (0,2) ... (0, sizeColl.size-1)
            val sliceColl = sizeColl.slice(0, idx + 1)
            // Fold each sizeColl slice. If we had a sizeColl containing 4,4,4 rangeCollUpperBound would contain
            // 4,8,12
            val foldedSlice = sliceColl.fold(0, {(accum: Int, otrIdx: Int) => accum + otrIdx})
            foldedSlice
        }

        val rangeCollLowerBound = parentIndices.map{
          (idx: Int) =>
            if(idx == 0){
              0
            }else{
              val newIdx = idx - 1
              // Take slices of sizeColls from (0,1) (0,2) ... (0, sizeColl.size-1)
              val sliceColl = sizeColl.slice(0, newIdx + 1)
              // Fold each sizeColl slice. If we had a sizeColl containing 4,4,4 rangeCollLowerBound would contain
              // 0,4,8
              val foldedSlice = sliceColl.fold(0, {(accum: Int, otrIdx: Int) => accum + otrIdx})
              foldedSlice
            }
        }
        // Produces some collection of pairs representing lower and upper index of some child collection inside
        // parent collection
        val rangeColl: Coll[(Int, Int)] = rangeCollLowerBound.zip(rangeCollUpperBound)

        // Creates a Coll[((Int, Int), Int)] such that if we have an example element ((0, 2), 0), (0, 2) would represent
        // the range of indices in childIndices that maps the elements with those indices to value 0
        val indexRangeMap: Coll[((Int, Int), Int)] = rangeColl.zip(parentIndices)

        // convert indices from child collections into their corresponding parent indices using the indexRangeMap
        val indicesFromRangeMap: Coll[Int] = childIndices.map{
          (idx: Int) =>
            val rangeToIndexVal = indexRangeMap.filter{
              (idxRngElem: ((Int, Int), Int)) =>
                idx >= idxRngElem._1._1 && idx < idxRngElem._1._2
            }
            if(rangeToIndexVal.size > 0){
              rangeToIndexVal(0)._2
            }else{
              // Throw -1 to indicate error
              -1
            }
        }
        // This will create a Coll[(Int, Int)] such that each pair will represent some element of nestedMemvotes
        val originalIndices: Coll[(Int, Int)] = indicesFromRangeMap.zip(flatIndices)
        val flattenedCollection: Coll[(GroupElement, Coll[Byte])] = originalIndices.map{
          (idxPair: (Int, Int)) =>
            nestedMemvotes(idxPair._1)(idxPair._2)
        }
        flattenedCollection
      }
      // Returns distinct memvotes from list so that no memvotes repeat
      val distinctMemvotes = {
        (memVoteColl: Coll[(GroupElement, Coll[Byte])]) =>
        val memVoteIndices = memVoteColl.indices
        val distinctIndices = memVoteIndices.filter{
          (idx: Int) =>
            // If this is the first index with this value that appears in memVoteColl, then it must be some distinct value
            if(memVoteColl.indexOf(memVoteColl(idx), 0) == idx){
              true
            }else{
              false
            }
        }
        val uniqueColl = distinctIndices.map{
          (idx: Int) => memVoteColl(idx)
        }
        uniqueColl
      }
      val ALL_MEMVOTE_ADDITIONS: Coll[(GroupElement, Coll[Byte])] = flattenMemvotes(ADDITION_MEMVOTES)

      // Flattened collection of all memVotes that attempt to remove from the subpool
      val ALL_MEMVOTE_REMOVALS = flattenMemvotes(REMOVAL_MEMVOTES)


      // Returns the number of times the given memVote appears in the given MemberList, essentially giving us a tally
      // for the number of people who voted for this member.
      val additionsTally: Int = {
        (memVote: (GroupElement, Coll[Byte])) =>
          val filterList = ALL_MEMVOTE_ADDITIONS.filter{
            (otherMemVote: (GroupElement, Coll[Byte])) =>
              memVote == otherMemVote
          }
          val tally = filterList.size
          tally
      }
      val removalsTally: Int = {
        (memVote: (GroupElement, Coll[Byte])) =>
          val filterList = ALL_MEMVOTE_REMOVALS.filter{
            (otherMemVote: (GroupElement, Coll[Byte])) =>
              memVote == otherMemVote
          }
          val tally = filterList.size
          tally
      }

      // Returns true if this memVote has a majority in the addition list, false if not
      val addVoteHasMajority = {
        (memVote: (GroupElement, Coll[Byte])) =>
          if(additionsTally(memVote) > (MINER_LIST.size / 2)){
            true
          }else{
            false
          }
      }
      // Returns true if this memVote has a majority in the removals list, false if not
      val remVoteHasMajority = {
        (memVote: (GroupElement, Coll[Byte])) =>
          if(removalsTally(memVote) > (MINER_LIST.size / 2)){
            true
          }else{
            false
          }
      }

      // These lists represent all the addition and removal memVotes that were voted on by a
      // a majority of the subpool. Contains duplicate values.
      val SUBPOOL_ADDITIONS = ALL_MEMVOTE_ADDITIONS.filter{
        (memVote: (GroupElement, Coll[Byte])) => addVoteHasMajority(memVote)
      }
      val SUBPOOL_REMOVALS = ALL_MEMVOTE_REMOVALS.filter{
        (memVote: (GroupElement, Coll[Byte])) => remVoteHasMajority(memVote)
      }
      // Additions list folded so that duplicate values are removed.
      val UNIQUE_ADDITIONS = distinctMemvotes(SUBPOOL_ADDITIONS)


      // A new member list with removals filtered out of the old list
      // and additions appended to the resulting list.
      // If there are enough skip votes, then default MEMBER_LIST(Either from metadata or from constants) is returned
      val MEMBER_CONSENSUS =
        if(SKIP_VOTES_FROM_TX.size <= NORMAL_VOTES_FROM_TX.size){
          MEMBER_LIST.filter{
            (member: (GroupElement, Coll[Byte])) =>
              !(SUBPOOL_REMOVALS.exists{
                (memVote: (GroupElement, Coll[Byte])) =>
                  memVote == member
            })
          }.append(UNIQUE_ADDITIONS)
        }else{
          MEMBER_LIST
        }

      // ================= This area is for value consensus, which determines the correct shares and values for each miner =================
       val VOTES_VALID = allOf(Coll(
          NORMAL_VOTES_FROM_TX.forall{(box: Box) => box.R6[GroupElement].isDefined && box.R7[Coll[Byte]].isDefined},
          SKIP_VOTES_FROM_TX.forall{(box: Box) => box.R5[Coll[Byte]].get == const_metadataPropBytesHashed},
          NORMAL_VOTES_FROM_TX.forall{(box: Box) => box.R7[Coll[Byte]].get == const_metadataPropBytesHashed},
          ALL_VOTES_FROM_TX.forall{(box: Box) => box.value == SELF.value},
          ALL_VOTES_FROM_TX.forall{(box: Box) => box.propositionBytes == SELF.propositionBytes}
        ))

       val voteSignersAreUnique = {
        (pkList: Coll[SigmaProp]) => pkList.forall{
         (pk: SigmaProp) => NORMAL_VOTES_FROM_TX.filter{(box: Box) => proveDlog(box.R6[GroupElement].get) == pk}.size == 1
        }
       }
       val buildVoterPoolState = {
        (box: Box) => box.R4[Coll[(SigmaProp, Long)]].get
       }
       val buildTotalShares = {
        (poolState: Coll[(SigmaProp, Long)]) =>
          poolState.fold(0L, {
            (accum: Long, stateVal: (SigmaProp, Long)) => accum + stateVal._2
          })
       }

       // Total collection of all pool states
       val VOTER_POOL_STATES: Coll[Coll[(SigmaProp, Long)]] = NORMAL_VOTES_FROM_TX.map{
        (box: Box) => buildVoterPoolState(box)
       }
       val TOTAL_SHARES_COLL: Coll[Long] = VOTER_POOL_STATES.map(buildTotalShares)
       // Avg total shares from list of voter pool states
       val AVG_TOTAL_SHARES = TOTAL_SHARES_COLL.fold(0L, {
        (accum: Long, shareTotal: Long) => accum + shareTotal
       }) / VOTER_POOL_STATES.size

       // Calculate box value from share using constants.
       def getBoxValue(shareNum: Long) : Long = {
        val boxVal = ((shareNum * TOTAL_INPUTS_VALUE) / AVG_TOTAL_SHARES) - (const_MinTxFee/(ALL_VOTES_FROM_TX.size))
        boxVal
       }
       // Build consensus on box value/share nums for certain pk using data from all available pool states.
      def buildValueConsensus(pk: SigmaProp) : (SigmaProp, Long) = {
        val filteredPoolState = VOTER_POOL_STATES.map{(poolState: Coll[(SigmaProp, Long)]) => poolState.filter{(poolStateVal: (SigmaProp, Long)) => poolStateVal._1 == pk}(0)}
        val generatedAvg = (filteredPoolState.fold(0L, {(accum:Long, poolStateVal: (SigmaProp, Long)) => accum + poolStateVal._2})) / (MINER_LIST.size * 1L)
        if(METADATA_VALID){
          val lastConsensus = METADATA_BOX.R4[Coll[(SigmaProp, Long)]].get

          // Gets shares for this pk from last consensus
          val sharesFromConsensus = lastConsensus.filter{
            (poolValue: (SigmaProp, Long)) => poolValue._1 == pk
          }(0)._2
          // If your miner reset or your share numbers increased, consensus will look normal
          if(generatedAvg >= sharesFromConsensus || generatedAvg < (sharesFromConsensus / 2)){
            (pk, generatedAvg)
          }else{
              // If your miner reset, you may only get a maximum of 50% of your last consensus value.
              // This is to discourage false share numbers.
              (pk, sharesFromConsensus / 2)
          }
        }else{
          // Normal consensus(share values are 0 since metadatabox isnt defined)
          (pk, generatedAvg)
        }
      }


       val valueConsensusFromPKList = {
        (pkList: Coll[SigmaProp]) => pkList.map(buildValueConsensus)
       }
       val VALUE_CONSENSUS =
        if(SKIP_VOTES_FROM_TX.size <= NORMAL_VOTES_FROM_TX.size){
          valueConsensusFromPKList(
            MINER_LIST.map{
              (ge: GroupElement) => proveDlog(ge)
            }
          )
        }else{
          if(METADATA_VALID){
            METADATA_BOX.R4[Coll[(SigmaProp, Long)]].get
          }else{
            // Metadata box isnt defined, and there are more skip votes than real votes so we will make a consensus
            // from 0.
            val newConsensus = MINER_LIST.map{(ge:GroupElement) => (proveDlog(ge), 0L)}
            newConsensus
          }
        }
       def getValueFromSharesConsensus(pk: SigmaProp) : Long = {
        val currentConsensusShares = VALUE_CONSENSUS.filter{
            (poolValue: (SigmaProp, Long)) => poolValue._1 == pk
          }(0)._2
        if(METADATA_VALID){
          val lastConsensus = METADATA_BOX.R4[Coll[(SigmaProp, Long)]].get
          val sharesFromConsensus = lastConsensus.filter{
            (poolValue: (SigmaProp, Long)) => poolValue._1 == pk
          }(0)._2
          if(currentConsensusShares >= sharesFromConsensus){
            // Zero out last consensus to evaluate just your rate of shares per this payout
            getBoxValue(currentConsensusShares - sharesFromConsensus)
          }else{
            // If your current consensus is less than the old one due to a reset, than you will simply use your
            // consensus share value. Since the maximum value of this can only be 50% of your last,
            // trying to abuse the system is discouraged if not worthless for most.
            getBoxValue(currentConsensusShares)
          }
        }else{
          // No metadata, so just do consensus normally because of initiation phase.
          getBoxValue(currentConsensusShares)
        }
       }
       val outputsFollowConsensus = NORMAL_VOTES_FROM_TX.forall{
        (voteBox: Box) => OUTPUTS.exists{
            (box: Box) =>
              getValueFromSharesConsensus(proveDlog(voteBox.R6[GroupElement].get)) == box.value && box.propositionBytes == proveDlog(voteBox.R6[GroupElement].get).propBytes
          }
        }
        val skipsValid = SKIP_VOTES_FROM_TX.forall{
          (skipBox: Box) => OUTPUTS.exists{
            (box: Box) =>
            if(SKIP_PROTOCOL <= 0){
              // Skip protocol less than 1, so handle skip values like normal votes using the consensus
              getValueFromSharesConsensus(proveDlog(skipBox.R4[GroupElement].get)) == box.value && box.propositionBytes == proveDlog(skipBox.R4[GroupElement].get).propBytes
            }else{
              // Skip protocol is >= 1, so skip values are sent back to holding box.
              // Holding box must have same hashed bytes as bytes in R6.
              skipBox.value == box.value && blake2b256(box.propositionBytes) == skipBox.R6[Coll[Byte]].get
            }
          }
        }
        // Called when number of skips are greater than number of votes i.e all votes are handled as skips
        val normalVotesAreSkips = NORMAL_VOTES_FROM_TX.forall{
          (voteBox: Box) => OUTPUTS.exists{
            (box: Box) =>
            if(SKIP_PROTOCOL <= 0){
              // Skip protocol less than 1, so handle skip values like normal votes using the consensus
              getValueFromSharesConsensus(proveDlog(voteBox.R6[GroupElement].get)) == box.value && box.propositionBytes == proveDlog(voteBox.R6[GroupElement].get).propBytes
            }else{
              // Skip protocol is >= 1, so skip values are sent back to holding box.
              // Holding box must have same hashed bytes as bytes in R6.
              voteBox.value == box.value && blake2b256(box.propositionBytes) == voteBox.R6[Coll[Byte]].get
            }
          }
        }
        // ================= This area is for voting consensus, which determines the correct values for subpool parameters =================
        val VOTING_HEIGHT_CONSENSUS =
          if(SKIP_VOTES_FROM_TX.size <= NORMAL_VOTES_FROM_TX.size){
            val maxHeightVoteList = NORMAL_VOTES_FROM_TX.map{
              (voteBox: Box) => voteBox.R8[Coll[Int]].get(0)
            }
            val generatedAvg = (maxHeightVoteList.fold(0, {(accum:Int, voteHeight: Int) => accum + voteHeight})) / (NORMAL_VOTES_FROM_TX.size)
            generatedAvg
          }else{
            MAX_VOTING_HEIGHT
          }
        val SKIP_PROTOCOL_CONSENSUS =
          if(SKIP_VOTES_FROM_TX.size <= NORMAL_VOTES_FROM_TX.size){
            val skipProtocolVoteList = NORMAL_VOTES_FROM_TX.map{
              (voteBox: Box) =>
                if(voteBox.R8[Coll[Int]].get(1) >= 1){
                  1
                }else{
                  0
                }
            }
            if(skipProtocolVoteList.filter{(v: Int) => v == 1}.size > (skipProtocolVoteList.size / 2)){
              1
            }else{
              0
            }
          }else{
            SKIP_PROTOCOL
          }

        val VOTING_CONSENSUS = Coll(VOTING_HEIGHT_CONSENSUS, SKIP_PROTOCOL_CONSENSUS)
        // ================= This area is for determining if the output boxes created are valid =================
        val outputsValid =
          if(SKIP_VOTES_FROM_TX.size <= NORMAL_VOTES_FROM_TX.size){
            allOf(Coll(
              OUTPUTS.size == ALL_VOTES_FROM_TX.size + 1,
              outputsFollowConsensus,
              skipsValid
            ))
          }else{
            allOf(Coll(
              OUTPUTS.size == ALL_VOTES_FROM_TX.size + 1,
              skipsValid,
              normalVotesAreSkips
            ))
          }

        // ================= This area is for determining the new metadataBox and the values it will hold in its register =================
        // Remove public keys(and their associated shares) from the value consensus that have been removed from the subpool.
        // This ensures that if a skip vote is called, the value consensus does not have members from the old consensus
        // that did not exist.
        val valueConsensusWithRemovals = VALUE_CONSENSUS.filter{
          (consVal: (SigmaProp, Long)) =>
            !(SUBPOOL_REMOVALS.exists{
              (member: (GroupElement, Coll[Byte])) =>
                proveDlog(member._1) == consVal._1
            })
        }
        // Add new public keys to the value consensus with a share number of 0.
        // Ensures skip votes work properly when using last consensus.
        val newMembersConsensus = UNIQUE_ADDITIONS.map{
          (member: (GroupElement, Coll[Byte])) =>
            (proveDlog(member._1), 0L)
        }
        // Modified value consensus for metadata box
        val METADATA_VALUE_CONSENSUS = valueConsensusWithRemovals.append(newMembersConsensus)
        val metadataOutputValid =
          // This checks if the metadataBox has been created yet
          if(INPUTS.size == ALL_VOTES_FROM_TX.size){
            // If metadatabox has not been created, create a new metadata box
            OUTPUTS.exists{
              (box: Box) =>
                allOf(Coll(
                  box.value == const_MinTxFee,
                  box.R4[Coll[(SigmaProp, Long)]].get == METADATA_VALUE_CONSENSUS,
                  box.R5[Coll[(GroupElement, Coll[Byte])]].get == MEMBER_CONSENSUS,
                  box.R6[Coll[Int]].get == VOTING_CONSENSUS,
                  box.R7[Int].get == HEIGHT,
                  box.R8[Int].get == 0,
                  blake2b256(box.propositionBytes) == const_metadataPropBytesHashed,
                  box.R9[Coll[Byte]].get == blake2b256(SELF.propositionBytes)
                  ))
            }
          }else{
            // Metadata Box has been created before, so use epoch obtained from the old metadata box.
            OUTPUTS.exists{
              (box: Box) =>
                allOf(Coll(
                  box.value == const_MinTxFee,
                  box.R4[Coll[(SigmaProp, Long)]].get == METADATA_VALUE_CONSENSUS,
                  box.R5[Coll[(GroupElement, Coll[Byte])]].get == MEMBER_CONSENSUS,
                  box.R6[Coll[Int]].get == VOTING_CONSENSUS,
                  box.R7[Int].get == HEIGHT,
                  box.R8[Int].get == METADATA_BOX.R8[Int].get + 1,
                  blake2b256(box.propositionBytes) == const_metadataPropBytesHashed,
                  box.R9[Coll[Byte]].get == blake2b256(SELF.propositionBytes)
                  ))
            }
          }
        // Any miner in the member list may sign this transaction, so long as outputs and metadata are valid
        atLeast(1, MINER_LIST.map{(ge:GroupElement) => proveDlog(ge)}) && sigmaProp(outputsValid && metadataOutputValid)
       }
      """.stripMargin
    script
  }
  //Removed areOutputsValid in return line and removed Outputs.size check in areOutputsValid
  // Include workerTupleMap
  def generateVotingContract(ctx: BlockchainContext, memberList: Array[(Address, String)], maxVotingHeight: Int, skipProtocol: Int, metadataAddress: Address): ErgoContract = {
    val membersUnzipped: (Array[Address], Array[String]) = memberList.unzip
    val minerList = membersUnzipped._1
    val workerList = membersUnzipped._2
    val publicKeyGEList = minerList.map{(addr: Address) => addr.getPublicKeyGE}
    val workerListHashed = workerList.map{(name: String) => Blake2b256(name)}.map{
      (byteArray: Array[Byte]) => special.collection.Builder.DefaultCollBuilder.fromArray(byteArray)
    }

    val metadataPropBytesHashed = Blake2b256(metadataAddress.getErgoAddress.contentBytes)
    val workerColl = special.collection.Builder.DefaultCollBuilder.fromArray(workerListHashed)
    val minerColl = special.collection.Builder.DefaultCollBuilder.fromArray(publicKeyGEList)
    val constantsBuilder = ConstantsBuilder.create()
    //System.out.println(scriptVarString)
    val compiledContract = ctx.compileContract(constantsBuilder
      .item("const_initMembers", minerColl.zip(workerColl))
      .item("const_MinTxFee", Parameters.MinFee)
      .item("const_metadataPropBytesHashed", metadataPropBytesHashed)
      .item("const_maxVotingHeight", ctx.getHeight + maxVotingHeight)
      .item("const_skipProtocol", skipProtocol)
      .build(), getVotingScript)
    compiledContract
  }

  /**
   * Performs the same consensus done in the smart contract so that output boxes may be built properly
   * This function was copied from the voting smart contract and modified to work in normal scala. As few changes
   * as possible were made so as to prevent any problems from occurring.
   * @param INPUTS Voting boxes to be used in consensus
   * @param memberList List of members obtained from parameters, is converted and rezipped so that it may be used
   *                   in consensus
   * @return ListBuffer of Output boxes to be used in consensus transaction
   */
  def buildConsensusOutputs(ctx: BlockchainContext, INPUTS: List[InputBox], memberList: List[(Address, String)], metadataAddress: Address, maximumVotingHeight:Int, skipVoteProtocol:Int): ListBuffer[OutBox] = {
    val membersUnzipped: (List[Address], List[String]) = memberList.unzip
    val addrList = membersUnzipped._1
    val nameList = membersUnzipped._2
    // Constants to be used during consensus
    val TOTAL_INPUTS_VALUE = INPUTS.foldLeft(0L){(accum: Long, box: InputBox) => accum + box.getValue}
    val MINER_LIST = addrList.map{(addr: Address) => addr.getPublicKeyGE}
    val WORKER_LIST = nameList.map{(name: String) => Blake2b256(name)}
    val MEMBER_LIST = MINER_LIST.zip(WORKER_LIST)
    val metadataPropBytesHashed = Blake2b256(metadataAddress.getErgoAddress.contentBytes)
    // Checks if Metadata exists and that only 1 Metadata box is being used in this transaction using hashed prop bytes
    val METADATA_VALID = INPUTS.exists{(box:InputBox) => Blake2b256(box.getErgoTree.bytes) == metadataPropBytesHashed}

    val getMetaDataBox: Option[InputBox]= {
      if(METADATA_VALID){
        Option(INPUTS.filter{(box:InputBox) => Blake2b256(box.getErgoTree.bytes) == metadataPropBytesHashed}(0))
      }else{
        None
      }
    }
    val SKIP_PROTOCOL = if(getMetaDataBox.isDefined){
      getMetaDataBox.get.getRegisters.get(2).getValue.asInstanceOf[List[Int]](1)
    }else{
      skipVoteProtocol
    }
    val MAX_VOTING_HEIGHT = if(getMetaDataBox.isDefined){
      getMetaDataBox.get.getRegisters.get(2).getValue.asInstanceOf[List[Int]](0)
    }else{
      maximumVotingHeight
    }


    val ALL_VOTES_FROM_TX = INPUTS.filter{(box:InputBox) => Blake2b256(box.getErgoTree.bytes) != metadataPropBytesHashed}
    val SKIP_VOTES_FROM_TX = ALL_VOTES_FROM_TX.filter{
      (box:InputBox) => box.getRegisters.get(0).getType == ErgoType.sigmaPropType() && box.getRegisters.get(1).getType == STRING_TYPE
    }
    val NORMAL_VOTES_FROM_TX = ALL_VOTES_FROM_TX.filter{
      (box:InputBox) => box.getRegisters.get(0).getType == POOL_STATE_TYPE && box.getRegisters.get(1).getType == MEMBER_LIST_TYPE
    }
    // Votes From Tx that add or remove members. A member is some (SigmaProp, Coll[Byte]) that maps a PublicKey to a hashed worker name
    val MEMBER_VOTES_FROM_TX = NORMAL_VOTES_FROM_TX.filter{
      (box:InputBox) =>
        if(box.getRegisters.size() > 5)
          box.getRegisters.get(5).getType == MEMBER_POLL_TYPE
        else
          false
    }

    // ================= This area is for member consensus, determining which members must be added and removed by vote =================

    // The Member Poll represents a pair of voting maps that will map public keys to worker names for members that will be added or removed to this subpool.
    // Element 1 of the MemberPoll is the list of all members that the given vote wishes to add to this subpool,
    // Element 2 of the MemberPoll is the list of all members that the given vote wishes to remove from this subpool
    val getMemberPoll = {
      (voteBox: InputBox) => voteBox.getRegisters.get(5).getValue.asInstanceOf[(List[(GroupElement, Array[Byte])], List[(GroupElement, Array[Byte])])]
    }
    val getAddedMembers = {
      (memPoll: (List[(GroupElement, Array[Byte])], List[(GroupElement, Array[Byte])])) =>
        memPoll._1
    }
    val getRemovedMembers = {
      (memPoll: (List[(GroupElement, Array[Byte])], List[(GroupElement, Array[Byte])])) =>
        memPoll._2
    }


    // Takes box, removes all invalid members from its R9 MemberPoll. Then returns a new MemberPoll from the box.
    val removeInvalidMembers = {
      (box: InputBox) =>
        val filteredAdditions = getMemberPoll(box)._1.filter{
          (memVote: (GroupElement, Array[Byte])) => !MINER_LIST.contains(memVote._1)
        }.filter{
          (memVote: (GroupElement, Array[Byte])) => !WORKER_LIST.contains(memVote._2.toArray)
        }
        val filteredRemovals = getMemberPoll(box)._2.filter{
          (memVote: (GroupElement, Array[Byte])) => MINER_LIST.contains(memVote._1)
        }.filter{
          (memVote: (GroupElement, Array[Byte])) => WORKER_LIST.contains(memVote._2.toArray)
        }
        (filteredAdditions, filteredRemovals)
    }
    // Returns the number of times the given memVote appears in the given MemberList, essentially giving us a tally
    // for the number of people who voted for this member.
    val getMemVoteTally = {
      (memVote: (GroupElement, Array[Byte]), memVoteList: List[(GroupElement, Array[Byte])]) =>
        memVoteList.count { (otherMemVote: (GroupElement, Array[Byte])) =>
          memVote == otherMemVote
        }
    }

    // List of all MemberPolls with invalid members removed.
    val TOTAL_MEMBER_CHANGES = MEMBER_VOTES_FROM_TX.map(removeInvalidMembers)


    // Flattened collection of all memVotes that attempt to add to the subpool
    // Essentially just a Coll[(SigmaProp, Coll[Byte])]
    val ALL_MEMVOTE_ADDITIONS = TOTAL_MEMBER_CHANGES.flatMap(getAddedMembers)
    // Flattened collection all of memVotes that attempt to remove from the subpool
    val ALL_MEMVOTE_REMOVALS = TOTAL_MEMBER_CHANGES.flatMap(getRemovedMembers)

    // Returns true if this memVote has a majority in the given list, false if not
    val memVoteHasMajority = {
      (memVote: (GroupElement, Array[Byte]), memVoteList: List[(GroupElement, Array[Byte])]) =>
      if(getMemVoteTally(memVote, memVoteList) > (MINER_LIST.size / 2)){
        true
      }else{
        false
      }
    }
    // These lists represent all the addition and removal memVotes that were voted on by a
    // a majority of the subpool. Contains duplicate values.
    val SUBPOOL_ADDITIONS = ALL_MEMVOTE_ADDITIONS.filter{
      (memVote: (GroupElement, Array[Byte])) => memVoteHasMajority(memVote, ALL_MEMVOTE_ADDITIONS)
    }
    val SUBPOOL_REMOVALS = ALL_MEMVOTE_REMOVALS.filter{
      (memVote: (GroupElement, Array[Byte])) => memVoteHasMajority(memVote, ALL_MEMVOTE_REMOVALS)
    }
    // Additions list folded so that duplicate values are removed.
    val UNIQUE_ADDITIONS = SUBPOOL_ADDITIONS.foldLeft(List[(GroupElement, Array[Byte])]()){
      (memVoteAccum: List[(GroupElement, Array[Byte])], memVote: (GroupElement, Array[Byte])) =>
        if (!memVoteAccum.contains(memVote)) {
          memVoteAccum.++(List(memVote))
        } else {
          memVoteAccum
        }
      }


    // A new member list with removals filtered out of the old list
    // and additions appended to the resulting list.
    // If there are enough skip votes, then default MEMBER_LIST(Either from metadata or from constants) is returned
    val MEMBER_CONSENSUS =
    if(SKIP_VOTES_FROM_TX.size <= NORMAL_VOTES_FROM_TX.size){
      MEMBER_LIST.filter{
        (member: (GroupElement, Array[Byte])) =>
          !SUBPOOL_REMOVALS.contains(member)
      }.++(UNIQUE_ADDITIONS)
    }else{
      MEMBER_LIST
    }

    // ================= This area is for value consensus, which determines the correct shares and values for each miner =================

//    val voteSignersAreUnique = {
//      (pkList: Coll[SigmaProp]) => pkList.forall{
//        (pk: SigmaProp) => NORMAL_VOTES_FROM_TX.filter{(box: Box) => box.R6[SigmaProp].get == pk}.size == 1
//      }
//    }
//    val workerListsAreSame = {
//      (workerList: Coll[Coll[Byte]] => ALL_VOTES_FROM_TX.forall{
//        (box: Box) => box.R5[Coll[Coll[Byte]]].get == workerList
//      }
//    }
    val buildVoterPoolState = {
      (box: InputBox) => box.getRegisters.get(0).getValue.asInstanceOf[Coll[(SigmaProp, Long)]].toArray.toList
    }
    val buildTotalShares = {
      (poolState: List[(SigmaProp, Long)]) =>
        poolState.foldLeft(0L){
          (accum: Long, stateVal: (SigmaProp, Long)) => accum + stateVal._2
        }
    }
    val getSharesFromPoolState = {
      (pk: SigmaProp, poolState: List[(SigmaProp, Long)]) =>
        poolState.filter{
          (poolValue: (SigmaProp, Long)) => poolValue._1 == pk
        }(0)._2
    }
    // Total collection of all pool states
    val VOTER_POOL_STATES = NORMAL_VOTES_FROM_TX.map(buildVoterPoolState)

    // Avg total shares from list of voter pool states
    val AVG_TOTAL_SHARES = VOTER_POOL_STATES.map(buildTotalShares).foldLeft(0L){
      (accum: Long, shareTotal: Long) => accum + shareTotal
    } / MINER_LIST.size // TODO CHANGE THIS LATER

    // Value used in boxValue calculation to check if Metadata box is defined and if more money is needed to create the box.
    val metadataModifier = if(getMetaDataBox.isDefined){
      1
    }else{
      2
    }
    // Calculate box value from share using constants.
    def getBoxValue(shareNum: Long) : Long = {
      val boxVal = ((shareNum * TOTAL_INPUTS_VALUE) / AVG_TOTAL_SHARES) - ((Parameters.MinFee * metadataModifier)/(ALL_VOTES_FROM_TX.size))
      boxVal
    }
    // Build consensus on box value for certain pk using data from all available pool states.
    def buildValueConsensus(pk: SigmaProp) : (SigmaProp, Long) = {
      val filteredPoolState = VOTER_POOL_STATES.map{(poolState: List[(SigmaProp, Long)]) => poolState.filter{(poolStateVal: (SigmaProp, Long)) => poolStateVal._1 == pk}(0)}
      val generatedAvg = (filteredPoolState.foldLeft(0L){(accum:Long, poolStateVal: (SigmaProp, Long)) => accum + poolStateVal._2}) / (MINER_LIST.size * 1L)
      if(getMetaDataBox.isDefined){
        val metaDataBox = getMetaDataBox.get
        val lastConsensus = metaDataBox.getRegisters.get(0).getValue.asInstanceOf[List[(SigmaProp, Long)]]
        val sharesFromConsensus = getSharesFromPoolState(pk, lastConsensus)
        // If your miner reset or your share numbers increased, consensus will look normal
        if(generatedAvg >= sharesFromConsensus || generatedAvg < (sharesFromConsensus / 2)){
          (pk, generatedAvg)
        }else{
          // If your miner reset, you may only get a maximum of 50% of your last consensus value.
          // This is to discourage false share numbers.
          (pk, sharesFromConsensus / 2)
        }
      }else{
        // Normal consensus(share values are 0 since metadatabox isnt defined)
        (pk, generatedAvg)
      }
    }


    val valueConsensusFromPKList = {
      (pkList: List[SigmaProp]) => pkList.map(buildValueConsensus)
    }
    val VALUE_CONSENSUS  =
      if(SKIP_VOTES_FROM_TX.size <= NORMAL_VOTES_FROM_TX.size){
        valueConsensusFromPKList(MINER_LIST.map(proveDlog))
      }else{
        if(getMetaDataBox.isDefined){
          getMetaDataBox.get.getRegisters.get(0).getValue.asInstanceOf[List[(SigmaProp, Long)]]
        }else{
          // Metadata box isnt defined, and there are more skip votes than real votes so we will make a consensus
          // from 0.
          val newConsensus = MINER_LIST.map{(ge:GroupElement) => (proveDlog(ge), 0L)}
          newConsensus
        }
      }
    def getValueFromSharesConsensus(pk: SigmaProp) : Long = {
      val currentConsensusShares = getSharesFromPoolState(pk, VALUE_CONSENSUS)
      if(getMetaDataBox.isDefined){
        val metaDataBox = getMetaDataBox.get
        val lastConsensus = metaDataBox.getRegisters.get(0).asInstanceOf[List[(SigmaProp, Long)]]
        val sharesFromConsensus = getSharesFromPoolState(pk, lastConsensus)
        if(currentConsensusShares >= sharesFromConsensus){
          // Zero out last consensus to evaluate just your rate of shares per this payout
          getBoxValue(currentConsensusShares - sharesFromConsensus)
        }else{
          // If your current consensus is less than the old one due to a reset, than you will simply use your
          // consensus share value. Since the maximum value of this can only be 50% of your last,
          // trying to abuse the system is discouraged if not worthless for most.
          getBoxValue(currentConsensusShares)
        }
      }else{
        // No metadata, so just do consensus normally because of initiation phase.
        getBoxValue(currentConsensusShares)
      }
    }
//    val outputsFollowConsensus = NORMAL_VOTES_FROM_TX.forall{
//      (voteBox: Box) => OUTPUTS.exists{
//        (box: Box) => getValueFromSharesConsensus(voteBox.R6[SigmaProp].get) == box.value && box.propositionBytes == voteBox.R6[SigmaProp].get.propBytes
//      }
//    }
//    val skipsValid = SKIP_VOTES_FROM_TX.forall{
//      (skipBox: Box) => OUTPUTS.exists{
//        (box: Box) =>
//          if(SKIP_PROTOCOL <= 0){
//            // Skip protocol less than 1, so handle skip values like normal votes using the consensus
//            getValueFromValueConsensus(skipBox.getRegisters(0)[SigmaProp].get) == box.value && box.propositionBytes == skipBox.getRegisters(0)[SigmaProp].get.propBytes
//          }else{
//            // Skip protocol is >= 1, so skip values are sent back to holding box.
//            // Holding box must have same hashed bytes as bytes in R6.
//            skipBox.value == box.value && blake2b56(box.propositionBytes) == skipBox.R6[Coll[Byte]].get
//          }
//      }
        // Called when number of skips are greater than number of votes i.e all votes are handled as skips
//        val normalVotesAreSkips = NORMAL_VOTES_FROM_TX.forall{
//          (voteBox: Box) => OUTPUTS.exists{
//            (box: Box) =>
//              if(SKIP_PROTOCOL <= 0){
//                // Skip protocol less than 1, so handle skip values like normal votes using the consensus
//                getValueFromValueConsensus(voteBox.R6[SigmaProp].get) == box.value && box.propositionBytes == voteBox.R6[SigmaProp].get.propBytes
//              }else{
//                // Skip protocol is >= 1, so skip values are sent back to holding box.
//                // Holding box must have same hashed bytes as bytes in R6.
//                voteBox.value == box.value && blake2b56(box.propositionBytes) == voteBox.R6[Coll[Byte]].get
//              }
//          }
            // ================= This area is for voting consensus, which determines the correct values for subpool parameters =================
    val VOTING_HEIGHT_CONSENSUS =
      if(SKIP_VOTES_FROM_TX.size <= NORMAL_VOTES_FROM_TX.size){
        val maxHeightVoteList = NORMAL_VOTES_FROM_TX.map{
          (voteBox: InputBox) => voteBox.getRegisters.get(4).getValue.asInstanceOf[Coll[Int]].toArray.toList(0)
        }
        val generatedAvg = (maxHeightVoteList.foldLeft(0){(accum:Int, voteHeight: Int) => accum + voteHeight}) / (NORMAL_VOTES_FROM_TX.size)
        generatedAvg
      }else{
        MAX_VOTING_HEIGHT
      }
    val SKIP_PROTOCOL_CONSENSUS =
      if(SKIP_VOTES_FROM_TX.size <= NORMAL_VOTES_FROM_TX.size){
        val skipProtocolVoteList = NORMAL_VOTES_FROM_TX.map{
          (voteBox: InputBox) =>
            if(voteBox.getRegisters.get(4).getValue.asInstanceOf[Coll[Int]](1) >= 1){
              1
            }else{
              0
            }
        }
        if(skipProtocolVoteList.filter{(v: Int) => v == 1}.size > (skipProtocolVoteList.size / 2)){
          1
        }else{
          0
        }
      }else{
        SKIP_PROTOCOL
      }

    val VOTING_CONSENSUS = List(VOTING_HEIGHT_CONSENSUS, SKIP_PROTOCOL_CONSENSUS)

    // ================= This area is for determining the new metadataBox and the values it will hold in its register =================
    // Remove public keys(and their associated shares) from the value consensus that have been removed from the subpool.
    // This ensures that if a skip vote is called, the value consensus does not have members from the old consensus
    // that did not exist.
    val valueConsensusWithRemovals = VALUE_CONSENSUS.filter{
      (consVal: (SigmaProp, Long)) =>
        !SUBPOOL_REMOVALS.exists{
          (member: (GroupElement, Array[Byte])) =>
            proveDlog(member._1) == consVal._1
        }
    }
    // Add new public keys to the value consensus with a share number of 0.
    // Ensures skip votes work properly when using last consensus.
    val newMembersValueConsensus = UNIQUE_ADDITIONS.map{
      (member: (GroupElement, Array[Byte])) =>
        (proveDlog(member._1), 0L)
    }
    // Modified value consensus for metadata box
    val METADATA_VALUE_CONSENSUS: List[(SigmaProp, Long)] = valueConsensusWithRemovals.++(newMembersValueConsensus)
    //println(addressToBoxValueList)
    val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
    val outBoxList: ListBuffer[OutBox] = ListBuffer.empty[OutBox]
    addrList.foreach{(addr: (Address)) =>
      outBoxList.append(txB.outBoxBuilder().value(getValueFromSharesConsensus(genSigProp(addr))).contract(new ErgoTreeContract(addr.getErgoAddress.script)).build())
    }
    // Converts the Byte Arrays in the Member Consensus into collections
    val memberConsensusWithColls = MEMBER_CONSENSUS.map{
      (member: (GroupElement, Array[Byte])) =>
        (member._1, DefaultCollBuilder.fromItems(member._2:_*)(RType.ByteType))
    }

    // Convert consensuses back to collections
    val metadataConsensusColl: Coll[(SigmaProp, Long)] = newColl(METADATA_VALUE_CONSENSUS, POOL_VAL_TYPE)
    val memberConsensusColl = newColl(memberConsensusWithColls, MEMBER_TYPE)
    val votingConsensusColl = DefaultCollBuilder.fromItems(VOTING_CONSENSUS:_*)(RType.IntType)
    val reg4 = ErgoValue.of(metadataConsensusColl, POOL_VAL_TYPE)
    val reg5 = ErgoValue.of(memberConsensusColl, MEMBER_TYPE)
    val reg6 = ErgoValue.of(votingConsensusColl, ErgoType.integerType())
    val reg7 = ErgoValue.of(ctx.getHeight)
    val reg8 = if(INPUTS.size == ALL_VOTES_FROM_TX.size + 1){
      ErgoValue.of(getMetaDataBox.get.getRegisters.get(4).getValue.asInstanceOf[Int] + 1)
    }else{
      ErgoValue.of(0)
    }
    outBoxList.append(txB.outBoxBuilder()
      .value(Parameters.MinFee)
      .contract(new ErgoTreeContract(metadataAddress.getErgoAddress.script))
      .registers(reg4, reg5, reg6, reg7, reg8)
      .build())
    outBoxList
  }

  def findValidInputBoxes(ctx: BlockchainContext, minerList: List[Address], inputBoxes:List[InputBox]): List[InputBox] = {
    minerList.map{(addr: Address) => inputBoxes.filter {
      (box: InputBox) => addr.getPublicKeyGE == box.getRegisters.get(2).getValue.asInstanceOf[GroupElement]}.head
    }

  }

}
