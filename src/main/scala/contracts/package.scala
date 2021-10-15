import org.ergoplatform.{ErgoAddressEncoder, Pay2SHAddress}
import org.ergoplatform.appkit.{Address, ErgoContract, NetworkType}
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.eval.CostingSigmaDslBuilder.proveDlog
import sigmastate.eval.SigmaDsl
import special.sigma.SigmaProp

package object contracts {

  def genSigProp(addr: Address): SigmaProp = {
    proveDlog(SigmaDsl.GroupElement(addr.getPublicKey.value))
  }

  def genDlog(addr: Address): ProveDlog = {
    addr.getPublicKey
  }

  def generateContractAddress(contract: ErgoContract, networkType: NetworkType): Address = {
    Address.fromErgoTree(contract.getErgoTree, networkType)
  }

}
