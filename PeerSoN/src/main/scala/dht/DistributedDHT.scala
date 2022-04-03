package dht

import net.tomp2p.dht.{PeerBuilderDHT, PeerDHT}
import net.tomp2p.futures.{BaseFuture, BaseFutureAdapter, FutureBootstrap}
import net.tomp2p.p2p.PeerBuilder
import net.tomp2p.peers.Number160
import net.tomp2p.storage.Data

import java.net.InetAddress
import scala.util.Random

class DistributedDHT(nodeId: Int,bootstrapHost : String) extends DHT {

  // create a new DHT node
  val p2p = new PeerBuilder(Number160.createHash(Random.nextLong()))
//    .behindFirewall()
    .ports(5000).start
  val fd = p2p.discover().inetAddress(InetAddress.getByName(bootstrapHost)).ports(5000).start()
  fd.awaitUninterruptibly()

  println(s"address = ${fd.peerAddress()}")

  // connect to a stable DHT node
//  val fb: FutureBootstrap = this.peer.peer.bootstrap.inetAddress(InetAddress.getByName("150.230.20.128")).ports(5000).start
  val fb: FutureBootstrap = p2p.bootstrap.peerAddress(fd.peerAddress()).start
    fb.awaitUninterruptibly
//    if (fb.isSuccess) peer.peer.discover.peerAddress(fb.bootstrapTo.iterator.next).start.awaitUninterruptibly
  val peer: PeerDHT = new PeerBuilderDHT(p2p).start



  // METHODS FOR RETRIEVING FROM DHT
  override def get(key: String, callback: Option[Any] => Unit): Unit = {
    val futureGet = peer.get(Number160.createHash(key)).start

    futureGet.addListener(new BaseFutureAdapter[BaseFuture] {

      override def operationComplete(future: BaseFuture): Unit = {
        if(future.isSuccess && !futureGet.dataMap.values.isEmpty) {
          callback(Some(futureGet.dataMap.values.iterator.next.`object`()))
        } else {
          callback(null)
        }
      }

      })
  }

  override def getAll(key: String, callback: Option[List[Any]] => Unit): Unit = {

    val futureGet = peer.get(Number160.createHash(key)).start

    futureGet.addListener(new BaseFutureAdapter[BaseFuture] {

      override def operationComplete(future: BaseFuture): Unit = {
        if(future.isSuccess && !futureGet.dataMap().values().isEmpty) {
          val value = futureGet.dataMap.values.iterator.next.`object`()
          value match {
            case v : List[Any] => callback(Some(v))
            case v@_ =>
              callback(None)
          }
        } else {
          callback(None)
        }
      }

    })
  }

  override def contains(key: String, callback: Boolean => Unit): Unit = {
    val futureGet = peer.get(Number160.createHash(key)).start

    futureGet.addListener(new BaseFutureAdapter[BaseFuture] {

      override def operationComplete(future: BaseFuture): Unit = {
        if(future.isSuccess) {
          if (futureGet.isEmpty) {
            callback(false)
          } else {
            callback(true)
          }
        } else {
          callback(false)
        }
      }
    })
  }



  // METHODS FOR PUTTING IN THE DHT
  override def remove(key: String): Unit = {
    peer.remove(Number160.createHash(key)).start()
  }

  override def append(key: String, data: Any): Unit = {
    val futureGet = peer.get(Number160.createHash(key)).start
    futureGet.awaitUninterruptibly
    if (futureGet.isSuccess) {
      if (futureGet.dataMap.values.iterator.hasNext) {


        val list = futureGet.dataMap.values.iterator.next.`object`()
        list match {
          case x :: xs => put(key, data :: (x :: xs))
          case _ => println("Could not append to list in dht, because the entry in the dht is not a list.")
        }
      } else {
        put(key,data::Nil)
      }
    }

  }
  override def put(key: String, data: Any): Unit = {
    peer.put(Number160.createHash(key)).data(new Data(data)).start()
  }
}
