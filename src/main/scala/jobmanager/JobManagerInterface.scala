package jobmanager

import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;

trait JobManagerInterface extends Remote {
  @throws(classOf[MalformedURLException])
  @throws(classOf[NotBoundException])
  @throws(classOf[RemoteException])
  def register(): Int

  @throws(classOf[MalformedURLException])
  @throws(classOf[NotBoundException])
  @throws(classOf[RemoteException])
  def runJob(
      ops: Array[String],
      parallelisms: Array[Int],
      dataSize: Int
  ): Boolean

  @throws(classOf[MalformedURLException])
  @throws(classOf[NotBoundException])
  @throws(classOf[RemoteException])
  def reportResult(result: Int): Unit
}
