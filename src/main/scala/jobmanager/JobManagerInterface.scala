// package jobmanager

import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;

trait JobManagerInterface extends Remote {
  @throws(classOf[MalformedURLException])
  @throws(classOf[NotBoundException])
  @throws(classOf[RemoteException])
  def register(): Int

  // @throws(classOf[MalformedURLException])
  // @throws(classOf[NotBoundException])
  // @throws(classOf[RemoteException])
  // def monitorReport(
  //     id: Int,
  //     numSlots: Int,
  //     latenciesToSelf: Array[Latency],
  //     bandwidthsToSelf: Array[BW],
  //     ipRate: Int,
  //     opRate: Int
  // ): Unit

  @throws(classOf[MalformedURLException])
  @throws(classOf[NotBoundException])
  @throws(classOf[RemoteException])
  def runJob(ops: Array[String], parallelisms: Array[Int]): Boolean
}
