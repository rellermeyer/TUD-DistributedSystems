package sgrub.playground

import com.google.common.primitives.Longs
import sgrub.inmemory.{InMemoryDataOwner, InMemoryDataUser, InMemoryStorageProvider}

object ScryptoInMemoryThings {
  def tryInMemoryGrub(): Unit = {
    println(
      "\n" +
        "\n================================" +
        "\nIN-MEMORY ADS TEST (Scrypto)" +
        "\n================================")

    val StorageProvider = new InMemoryStorageProvider
    val DU1 = new InMemoryDataUser(StorageProvider)
    val DO1 = new InMemoryDataOwner(StorageProvider)
    DO1.register(DU1)

    val someNewData = Map[Long, Array[Byte]](
      1L -> "Some Arbitrary Data".getBytes(),
      2L -> "Some More Arbitrary Data".getBytes(),
      3L -> "Hi".getBytes(),
      4L -> "Hello".getBytes(),
    )

    val someNewerData = Map[Long, Array[Byte]](
      4L -> "Updated Hello ".getBytes(),
      5L -> "Added Hello".getBytes(),
    )

    val kvPrint: (Long, Array[Byte]) => Unit = (key, value) => {
      println(s"Key: $key, Value: ${new String(value)}")
    }

    println("Initial input:")
    someNewData.foreach(x => kvPrint(x._1, x._2))

    println(s"DO1 verification result: ${DO1.gPuts(someNewData)}")
    println("DU1 gGet results:")
    someNewData.foreach(kv => {
      DU1.gGet(kv._1, kvPrint)
    })
    println("Adding new data...")
    someNewerData.foreach(x => kvPrint(x._1, x._2))
    println(s"DO1 verification result: ${DO1.gPuts(someNewerData)}")
    println("DU1 gGet results:")
    someNewerData.foreach(kv => {
      DU1.gGet(kv._1, kvPrint)
    })

    println("MinimalVerifier gets...")
    val minV = new MinimalVerifier(DO1.latestDigest.slice(0,33))
    for (key <- 1L to 5L) {
      println(s"Key: $key")
      StorageProvider.request(key, proof => {
        println(s"Valid? ${minV.manualVerify(Longs.toByteArray(key), proof, {
          case Some(existResult) => {
            println(s"Value: ${new String(existResult)}")
          }
          case _ => println(s"Fail. No value for key: $key")
        })}")
      })
    }

  }
}
