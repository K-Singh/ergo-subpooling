package pools

import com.google.gson.GsonBuilder
import configs.SubPoolConfig
import okhttp3.{OkHttpClient, Request}

object PoolGrabber {
  // Send Request To Enigma Pool, format responses into useable values
  def requestFromEnigmaPool(config: SubPoolConfig): (Array[Long], Long) = {
    val gson = new GsonBuilder().create()
    val addrStr = config.getParameters.getHoldingAddress
    val httpClient = new OkHttpClient()

    val reqTotalShares = new Request.Builder().url(s"https://api.enigmapool.com/shares/${addrStr}").build()
    val respTotalShares = httpClient.newCall(reqTotalShares).execute()
    val respString1 = respTotalShares.body().string()

    val sharesReqObject = gson.fromJson(respString1, classOf[EnigmaPoolRequests.SharesRequest])
    val totalShares = sharesReqObject.shares.valid.toLong

    val reqWorkerShares = new Request.Builder().url(s"https://api.enigmapool.com/workers/${addrStr}").build()
    val respWorkerShares = httpClient.newCall(reqWorkerShares).execute()
    val respString2 = respWorkerShares.body().string()

    val workerReqObject = gson.fromJson(respString2, classOf[EnigmaPoolRequests.WorkerRequest])
    def getWorkerShareNumber(workerName: String) = {
      val worker = workerReqObject.workers.toList.find{(w: EnigmaPoolRequests.Worker) => w.worker == workerName}
      if(worker.isDefined){
        worker.get.shares.toLong
      }else{
        println(s"Error: Worker ${workerName} could not be found!")
        sys.exit(0)
      }
    }
    //val workerShareList = getWorkerShareNumber("testWorker")
    val workerShareList = config.getParameters.getWorkerList.map(getWorkerShareNumber)
    //println(totalShares)
    //workerShareList.foreach(println)
    // println(workerShareList.mkString("Array(", ", ", ")"))
    (workerShareList, totalShares)
  }

  def requestFromHeroMiners(config: SubPoolConfig): (Array[Long], Long) ={
    val gson = new GsonBuilder().create()
    val addrStr = config.getParameters.getHoldingAddress
    val httpClient = new OkHttpClient()

    val reqPoolState = new Request.Builder().url(s"https://ergo.herominers.com/api/stats_address?address=${addrStr}").build()
    val respPoolState = httpClient.newCall(reqPoolState).execute()
    val respString = respPoolState.body().string()
    //println(respString)
    val poolStateObject = gson.fromJson(respString, classOf[HeroMinersRequests.PoolState])

    def getWorkerShareNumber(workerName: String) = {
      val worker = poolStateObject.workers.toList.find{(w: HeroMinersRequests.Worker) => w.name == workerName}
      if(worker.isDefined){
        worker.get.shares_good
      }else{
        println(s"Error: Worker ${workerName} could not be found!")
        sys.exit(0)
      }
    }

    val workerShareList = config.getParameters.getWorkerList.map(getWorkerShareNumber)
    val totalShares = workerShareList.sum
    //println(totalShares)
    //workerShareList.foreach(println)
    // println(workerShareList.mkString("Array(", ", ", ")"))
    (workerShareList, totalShares)
  }
}
