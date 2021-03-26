package taskmanager

import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import jobmanager.Task
import jobmanager.BW

trait TaskManagerInterface extends Remote {
    @throws(classOf[MalformedURLException])
    @throws(classOf[NotBoundException])
    @throws(classOf[RemoteException])
    def assignTask(task: Task, initialState: Int, bws: Array[Int]): Unit

    @throws(classOf[MalformedURLException])
    @throws(classOf[NotBoundException])
    @throws(classOf[RemoteException])
    def terminateTask(taskID: Int): Unit

    @throws(classOf[MalformedURLException])
    @throws(classOf[NotBoundException])
    @throws(classOf[RemoteException])
    def migrate(taskID: Int, to: (Int, Int), task: Task, bws: Array[Int]): Unit
}
