import org.ergoplatform.{ErgoAddressEncoder, Pay2SHAddress}
import org.ergoplatform.appkit.{Address, ErgoContract, ErgoType, NetworkType}
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.eval.CostingSigmaDslBuilder.proveDlog
import sigmastate.eval.SigmaDsl
import special.collection.Coll
import special.sigma.{GroupElement, SigmaProp}

package object contracts {

  def genSigProp(addr: Address): SigmaProp = {
    proveDlog(addr.getPublicKeyGE)
  }

  def genDlog(addr: Address): ProveDlog = {
    addr.getPublicKey
  }


  def generateContractAddress(contract: ErgoContract, networkType: NetworkType): Address = {
    Address.fromErgoTree(contract.getErgoTree, networkType)
  }
  /**
   * Represents custom ErgoTypes to be used in Subpooling contracts
   */
  object SpType {
    final val STRING_TYPE = ErgoType.collType(ErgoType.byteType())
    final val POOL_VAL_TYPE = ErgoType.pairType[SigmaProp, Long](ErgoType.sigmaPropType(), ErgoType.longType())
    final val MEMBER_TYPE = ErgoType.pairType[GroupElement, Coll[Byte]](ErgoType.groupElementType(), STRING_TYPE)
    final val POOL_STATE_TYPE = ErgoType.collType(POOL_VAL_TYPE)
    final val MEMBER_LIST_TYPE = ErgoType.collType(MEMBER_TYPE)
    final val MEMBER_POLL_TYPE = ErgoType.pairType(MEMBER_LIST_TYPE, MEMBER_LIST_TYPE)
  }

  /**
   * Returns new collection of type Coll[T] where T must have some corresponding ErgoType ErgoType[T]
   */
  def newColl[T](list: List[T], ergoType: ErgoType[T]): Coll[T] = {
    special.collection.Builder.DefaultCollBuilder.fromItems(list:_*)(ergoType.getRType)
  }
  def newColl[T](arr: Array[T], ergoType: ErgoType[T]): Coll[T] = {
    special.collection.Builder.DefaultCollBuilder.fromArray(arr)(ergoType.getRType)
  }



}
