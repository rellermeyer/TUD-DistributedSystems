import akka.actor.ActorSystem
import akka.cluster.Cluster

object Main {
  def main(args: Array[String]): Unit = {
    val systemName = "ByzantineApp"
    val node1 = ActorSystem(systemName)
    val joinAddress = Cluster(node1);
  }
}
