package app

import com.google.gson.GsonBuilder
import configs.{ConfigBuilder, SubPoolConfig, SubPoolNodeConfig, SubPoolParameters}
import contracts.ConsensusStageHelpers.{buildConsensusFromBoxes, buildOutputsFromConsensus, findValidInputBoxes, generateConsensusContract}
import contracts.HoldingStageHelpers.generateHoldingOutputRegisterList
import contracts.{ConsensusStageHelpers, HoldingStageHelpers}
import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoClient, ErgoContract, ErgoProver, ErgoToken, ErgoValue, InputBox, Mnemonic, NetworkType, OutBox, Parameters, RestApiErgoClient, SecretStorage, SecretString, SignedTransaction, UnsignedTransaction, UnsignedTransactionBuilder}
import pools.PoolGrabber.{requestFromEnigmaPool, requestFromHeroMiners}

import java.io.File
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConverters._
import ShellHelper._

object AppCommands {


  def help = {
    println("======================================================================================================")
    println("======================================================================================================")
    println("This is the Ergo Subpooling dApp. Subpools are groups of miners that pool together hashrates in order")
    println("to get quicker rewards from a larger mining pool. To create a subpool, you will need the addresses of each")
    println("miner involved in your subpool along with your unique worker name. Upon creating a pool you should receive")
    println("2 addresses, these will be the addresses of the smart contracts that let your pool function. Once your")
    println("subpool has been created, make sure to copy everything into your config file! Incorrectly copying could")
    println("lead to lost rewards. To receive rewards to your subpool, put your holdingAddress in your mining" )
    println("software. Once you've begun mining to this address, check on your pool's website to see if you've earned")
    println("any mining rewards. Once your pool has received your mining rewards, you may send a withdraw request to")
    println("your subpool. Once all members of your subpool have sent a withdraw request, the subpool will be ready")
    println("to distribute rewards to each of its members according to their share numbers. ANY member of the subpool")
    println("may sign this final transaction so that all members can get their mining rewards distributed.")
    println("Currently, subpooling only works with EngimaPool, but more pools should be on the way.")
    println("======================================================================================================")
    println("Commands:")
    println("create             - Create subpool by entering in parameters")
    println("load [config.json] - Load subpool from config")
    println("------withdraw     - Send withdrawal request to smart contract using information obtained from a mining pool")
    println("------------enigma - Get withdrawal info from Enigma Pool")
    println("------------hero   - Get withdrawal from HeroMiners Pool")
    println("------distribute   - Sign transaction from consensus stage and distribute rewards to members. All members must have sent a")
    println("                     withdrawal request in order for this to work")
    println("------join         - Join the loaded subpool. This will replace the wallet-name and worker-name with new valid inputs.")
    println("wallets            - Load wallet/signer commands")
    println("------list         - List current saved wallets/signers")
    println("------new          - Create a new wallet/signer from a mneumonic. You can restore wallets from other ")
    println("                     sources given that you have the secret phrase.")
    println("help               - Show this help message")
    println("======================================================================================================")
    println("======================================================================================================")
  }

  def create(client: ErgoClient, config: SubPoolConfig) = {
    implicit val shellState: ShellState = ShellStates.createState
    println("\nTo create a subpool, you must first associate this subpool with a wallet/signer. Type in the name of your wallet/signer and press enter.")
    println("You may create a new wallet/signer using the \"wallets\" and \"new\" command from the main menu.")
    var secretName = shellInput
    if(accessWallet(shellState, secretName).isLocked){
      println("This wallet could not be accessed!")
      sys.exit(0)
    }


    println("\nNow enter the addresses of you and each miner in your pool. Press enter after each address. Type \"done\" when complete, or \"exit\" to stop. ")
    val addressList : ListBuffer[String] = ListBuffer.empty[String]
    val workerList: ListBuffer[String] = ListBuffer.empty[String]

    Iterator.continually(shellInput)
      .takeWhile(_ != "done")
      .foreach{ str:String =>
        str match {
          case "exit" => sys.exit(0)
          case elem:String => addressList.append(elem).toString

        }
      }
    println("\nNow enter the worker names that correspond to each address in the order that they were given. Press enter after each name and type \"done\" when complete.")
    Iterator.continually(shellInput)
      .takeWhile(_ != "done")
      .foreach{ str:String =>
        str match {
          case "exit" => sys.exit(0)
          case elem:String => workerList.append(elem).toString

        }
      }

    println("\nPlease re-enter YOUR own worker name. Other members of your pool will enter their worker names into their own config.")
    println("Duplicate worker names will cause issues so make sure that all members of your pool input their names correctly!")
    val workerName = shellInput
    var minerAddresses = ListBuffer.empty[Address]
    try{
    minerAddresses = addressList.map{(s: String) => Address.create(s)}
    }catch {
        case err: Throwable => println("The addresses given were not valid!")
        sys.exit(0)
      }


    println("Finally, enter your mining pool's minimum payout in ERG: ")
    val minPayout: Double = shellDouble

    client.execute(ctx => {
      val consensusContract = ConsensusStageHelpers.generateConsensusContract(ctx, minerAddresses.toList, (minPayout * Parameters.OneErg).toLong)
      val consensusAddress = contracts.generateContractAddress(consensusContract, config.getNode.getNetworkType)

      val holdingContract = HoldingStageHelpers.generateHoldingContract(ctx, minerAddresses.toList, (minPayout * Parameters.OneErg).toLong, consensusAddress)
      val holdingAddress = contracts.generateContractAddress(holdingContract, config.getNode.getNetworkType)

      val parameters = new SubPoolParameters(workerName, addressList.toArray, workerList.toArray , holdingAddress.toString, consensusAddress.toString, minPayout)
      val newConfig = ConfigBuilder.newCustomConfig(secretName, parameters)
      println("Your config file has been created! Where would you like to save it?")
      println("Enter \"default\" for default config file. Enter \"some_config_name_here.json\" to save to a custom file. Enter anything else to cancel and return to main menu.")

      def endText = {
        println("Please ensure all miners in your subpool use this file! Also ensure they use their own unique wallet/signer and workerName!")
        println("")
        println("You have finished creating your subpool! Press enter to return to main menu.")
        shellInput
      }

      shellInput match {
        case "default" =>
          println("Saving file to default config: " + ConfigBuilder.defaultConfigName)
          ConfigBuilder.writeConfig(ConfigBuilder.defaultConfigName, newConfig)
          endText
        case name:String if name.endsWith(".json") =>
          println("Saving file to custom config: " + name)
          ConfigBuilder.writeConfig(name, newConfig)
          endText
        case _ =>
          println("Cancelling and returning to main menu...")
      }

    })
  }

  def load(ergoClient: ErgoClient, config: SubPoolConfig, configPath:String ="") = {
    implicit val shellState = ShellStates.loadState

    var currentConfig = config
    var currentErgoClient = ergoClient
    if(configPath.isEmpty) {
      println("Standard Config has been file loaded.")
    }else{
      //TODO Make sure to change back to subpool_config.json
      currentConfig = SubPoolConfig.load(configPath)
      val newNodeConf: SubPoolNodeConfig = currentConfig.getNode
      val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(newNodeConf.getNetworkType)
      currentErgoClient = RestApiErgoClient.create(newNodeConf.getNodeApi.getApiUrl, newNodeConf.getNetworkType, newNodeConf.getNodeApi.getApiKey, explorerUrl)
      println(s"Config file ${configPath} has been loaded.")
    }
    println("\nEnter \"withdraw\", \"distribute\" or \"join\". Enter \"back\" to return to main menu.")
    Iterator.continually(shellInput)
      .takeWhile(_ != "back")
      .foreach{ str:String =>
        str match {
          case "withdraw" => try{withdraw(currentErgoClient, currentConfig)}catch {
            case err: Throwable => println("Error: There was an issue trying to withdraw! Have you been paid out yet?")
              println(" ErrorVal: " + err.getMessage)
              println(" StackTrace: " + err.printStackTrace())
          }
          case "distribute" => try{distribute(currentErgoClient, currentConfig)}catch {
            case err: Throwable => println("Error: There was an issue trying to sign the distribution transaction. Have all miner's sent their withdrawal request?")
              println(" ErrorVal: " + err.getMessage)
              println(" StackTrace: " + err.printStackTrace())
          }
          case "join" => join(currentConfig, configPath)
          case "exit" => sys.exit(0)
          case _: String =>
        }
      }
  }

  def withdraw(ergoClient: ErgoClient, config: configs.SubPoolConfig) = {
    implicit val shellState: ShellState = ShellStates.withdrawState
    var workerShareList = Array.empty[Long]
    var totalShares = 0L
    println("\nPlease enter the mining pool that your subpool currently mines to.")
    println("Enter \"enigma\" for Enigma Pool or \"hero\" for HeroMiners:")
    val poolName = shellInput
    try {
      poolName match {
        case "enigma" =>
          val httpReq = requestFromEnigmaPool(config)
          workerShareList = httpReq._1
          totalShares = httpReq._2
        case "hero" =>
          val httpReq = requestFromHeroMiners(config)
          workerShareList = httpReq._1
          totalShares = httpReq._2
        case _ => println("The pool name given is not valid!")
          sys.exit(0)
      }
    }catch {
      case err:Throwable => println("There was an unknown error while accessing information from your mining pool!")
        println(" ErrorVal: " + err.getMessage)
        println(" StackTrace: " + err.printStackTrace())
        sys.exit(1)
    }

    ergoClient.execute(ctx => {
      val prover: ErgoProver = createProver(shellState, ctx, config.getNode.getWallet.getWalletName)

      var provingAddress = prover.getAddress
      val holdingAddress = Address.create(config.getParameters.getHoldingAddress)
      val consensusAddress = Address.create(config.getParameters.getConsensusAddress)
      val minerAddressList = config.getParameters.getMinerAddressList.map(Address.create)
      val consensusAmnt: Long = ((config.getParameters.getMinimumPayout*Parameters.OneErg)/minerAddressList.size).toLong - Parameters.MinFee

      val filterList: Array[Address] = minerAddressList.filter{(addr: Address) => prover.getEip3Addresses.asScala.exists{(eip: Address) => eip.toString == addr.toString}}
        if (filterList.length > 0) {
          provingAddress = filterList(0)
        } else {
          println("The secret string provided was unable to verify you as a member of the subpool!")
          println("These are the addresses associated with the given prover:")
          prover.getEip3Addresses.asScala.foreach(println)
          println("If these addresses do not look like yours")
          println("then the mneumonic phrase you entered is wrong.")
          sys.exit(0)
        }

      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
      val tokenList = List.empty[ErgoToken].asJava
      val holdingBoxList: java.util.List[InputBox] = ctx.getCoveringBoxesFor(holdingAddress, consensusAmnt + Parameters.MinFee, tokenList).getBoxes
      val registerList = generateHoldingOutputRegisterList(minerAddressList.toList, workerShareList.toList, totalShares, provingAddress, config.getParameters.getWorkerName)

      val holdingOutBox: OutBox = txB.outBoxBuilder
        .value(consensusAmnt)
        .contract(ctx.newContract(consensusAddress.getErgoAddress.script))
        .registers(registerList: _*)
        .build()


      val tx: UnsignedTransaction = txB
        .boxesToSpend(holdingBoxList)
        .outputs(holdingOutBox)
        .fee(Parameters.MinFee)
        .sendChangeTo(holdingAddress.getErgoAddress)
        .build()
      val signed: SignedTransaction = prover.sign(tx)
      val txId: String = ctx.sendTransaction(signed)
      println(s"\nYour withdrawal request was successful! Your transaction id is: ${txId}")
    })
  }

  def distribute(ergoClient: ErgoClient, config: SubPoolConfig) = {
    implicit val shellState: ShellState = ShellStates.loadState
    ergoClient.execute(ctx => {
      val prover: ErgoProver = createProver(shellState, ctx, config.getNode.getWallet.getWalletName)

      val minerAddressList = config.getParameters.getMinerAddressList.map(Address.create)
      val consensusAmnt: Long = ((config.getParameters.getMinimumPayout*Parameters.OneErg)/minerAddressList.size).toLong

      // Protection contract for consensus box

      val consensusAddress = Address.create(config.getParameters.getConsensusAddress)
      consensusAddress.getErgoAddress.script
      // Create unsigned transaction builder
      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder
      val consensusBoxList = ctx.getUnspentBoxesFor(consensusAddress, 0, 20)
      val consensusInputBoxes = findValidInputBoxes(ctx, minerAddressList.toList, consensusBoxList.asScala.toList)


      val totalValue: Long = consensusInputBoxes.foldLeft(0L) { (accum: Long, box: InputBox) => accum + box.getValue }
      val avgTotalShares: Long = consensusInputBoxes.foldLeft(0L) { (accum: Long, box: InputBox) => accum + box.getRegisters.get(1).getValue.asInstanceOf[Long] } / (consensusInputBoxes.size * 1L)
      val minerPKs = minerAddressList.map(contracts.genSigProp)

      val consensusResult = buildConsensusFromBoxes(consensusInputBoxes.asJava, minerPKs.toList)
      val consensusOutBoxList = buildOutputsFromConsensus(ctx, consensusResult, minerAddressList.toList, avgTotalShares, totalValue)

      val tx: UnsignedTransaction = txB
        .boxesToSpend(consensusInputBoxes.asJava)
        .outputs(consensusOutBoxList: _*)
        .fee(Parameters.MinFee)
        .sendChangeTo(consensusAddress.getErgoAddress)
        .build()

      // Sign transaction of mining pool to holding box
      val signed: SignedTransaction = prover.sign(tx)
      val txId: String = ctx.sendTransaction(signed)
      println(s"\nYour distribution transaction was successful! Your transaction id is: ${txId}")
    })
  }

  /**
   * Command that opens up wallet access. Can create and list wallets here.
   */
  def wallets(ergoClient: ErgoClient) ={
    implicit val shellState: ShellState = ShellStates.walletsState
    println("\nTo list your current wallets/signers, type \"list\". To create a new wallet/signer, type \"new\"")
    val walletCmd = shellInput
    walletCmd match {
      case "list" =>
        val secretDirs = new File("./data/")
        try {
          secretDirs.listFiles().foreach { (f: File) => println(f.getName) }
        }catch {
          case err:NullPointerException => println("You have no wallets/signers.")
        }
      case "new" => newSecret()
      case _ => println("That command is invalid!")
        sys.exit(0)
    }

    def newSecret(): Unit ={
      implicit val shellState: ShellState = ShellStates.newState
      println("\nPlease begin by entering your mneumonic. This mneumonic/phrase must be the secret key that corresponds to one unique address in your subpool.")
      println("Enter your phrase all lowercase, with spaces between each word. Press enter when complete. Example: \"this is the secret phrase to an address i own\"")
      val secretPhrase = SecretString.create(shellInput)
      println("Please enter your mneumonic password. If you are unsure of what this is, simply leave it blank and press enter.")
      val secretPhrasePass = SecretString.create(shellInput)
      println("Please enter an encryption password to encrypt your data")
      val secretPass = SecretString.create(shellInput)
      val newMneumonic = Mnemonic.create(secretPhrase, secretPhrasePass)
      println("Finally, enter the name of this wallet/signer. You can use this name to load this wallet/signer into future subpools.")
      val secretDir = shellInput
      val secStorage = SecretStorage.createFromMnemonicIn("data/"+secretDir, newMneumonic, secretPass)
      println("New wallet/signer created!")
      ergoClient.execute(ctx =>
        {
          val prover: ErgoProver =
            ctx.newProverBuilder
              .withMnemonic(newMneumonic)
              .withEip3Secret(0)
              .withEip3Secret(1)
              .withEip3Secret(2)
              .withEip3Secret(3)
              .withEip3Secret(4)
              .withEip3Secret(5)
              .build()
          println("These are the first 5 EIP3 addresses associated with your new wallet: ")
          prover.getEip3Addresses.asScala.foreach(println)
        }
      )
    }
  }

  def join(config: SubPoolConfig, configPath:String = "") = {
    implicit val shellState:ShellState = ShellStates.joinState
    var fileName = configPath
    if(configPath.isEmpty){
      fileName = ConfigBuilder.defaultConfigName
    }
    val oldParams = config.getParameters
    val addressList = oldParams.getMinerAddressList
    val workerList = oldParams.getWorkerList
    val holdingAddress = oldParams.getHoldingAddress
    val consensusAddress = oldParams.getConsensusAddress
    val minPayout = oldParams.getMinimumPayout
    println("\nTo join this subpool, please enter the wallet/signer name you would like to use in this subpool.")
    val secretName = shellInput
    val storedWallet = accessWallet(shellState, secretName)
    println("\nNow enter the worker name you would like to use in this subpool.")
    val workerName = shellInput
    if(!workerList.contains(workerName)){
      println(s"Worker name ${workerName} is not present in list of workers!")
      workerList.foreach(println)
      sys.exit(0)
    }
    val parameters = new SubPoolParameters(workerName, addressList.toArray, workerList.toArray , holdingAddress.toString, consensusAddress.toString, minPayout)
    val newConfig = ConfigBuilder.newCustomConfig(secretName, parameters)
    ConfigBuilder.writeConfig(fileName, newConfig)
    println(s"You have successfully joined the subpool in config file ${fileName}!")
  }

  /**
   * Access wallet/signer
   * @param shellState Current ShellState
   * @param secretName Name of wallet/signer to access
   * @return returns loaded SecretStorage. Exits in case of error.
   */
  def accessWallet(implicit shellState: ShellState, secretName: String): SecretStorage ={
    val secretDir = new File("./data/"+secretName+"/")
    var secretFile = secretDir
    if(secretDir.isDirectory){
      secretFile = secretDir.listFiles()(0)
    }else{
      println(s"The wallet/signer with name: ${secretName} could not be found!")
      sys.exit(0)
    }
    val loadedStorage = SecretStorage.loadFrom(secretFile)
    println(s"Please enter the encryption password for wallet/signer ${secretName}. The characters typed will not be shown on-screen.")
    val secretPass = SecretString.create(shellPassword)
    try {
      loadedStorage.unlock(secretPass)
    }catch{
      case err:RuntimeException =>
        println("The encryption password was incorrect!")
        sys.exit(0)
    }
    println(s"Wallet/Signer ${secretName} was successfully accessed!")

     loadedStorage
  }

  /**
   * Creates ErgoProver by accessing wallet secret storage
   * @param shellState Current ShellState
   * @param ctx Current BlockchainContext
   * @param secretName Name of wallet/signer to make prover from
   * @return ErgoProver made from secret key in SecretStorage
   */
  def createProver(implicit shellState:ShellState, ctx:BlockchainContext, secretName: String): ErgoProver ={
    val storedWallet = accessWallet(shellState, secretName)
      if(storedWallet.isLocked) {
        println("A prover could not be made from this wallet/signer!")
        sys.exit(0)
      }
    val prover: ErgoProver =
      ctx.newProverBuilder
        .withSecretStorage(storedWallet)
        .withEip3Secret(0)
        .withEip3Secret(1)
        .withEip3Secret(2)
        .withEip3Secret(3)
        .withEip3Secret(4)
        .withEip3Secret(5)
        .build()
    prover
    }
}
