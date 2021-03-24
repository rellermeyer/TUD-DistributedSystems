package taskmanager

import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import jobmanager.Task

trait TaskManagerInterface extends Remote {
    @throws(classOf[MalformedURLException])
    @throws(classOf[NotBoundException])
    @throws(classOf[RemoteException])
    def assignTask(task: Task): Unit

    @throws(classOf[MalformedURLException])
    @throws(classOf[NotBoundException])
    @throws(classOf[RemoteException])
    def terminateTask(jobID: Int, taskID: Int): Int

    @throws(classOf[MalformedURLException])
    @throws(classOf[NotBoundException])
    @throws(classOf[RemoteException])
    def suspendTask(jobID: Int, taskID: Int): Unit
}
