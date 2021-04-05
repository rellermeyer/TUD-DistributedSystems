import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * Contains all node mirrors and managers.
  *
  */
class ManagedArchitecture extends Serializable {
    val repairManager: RepairManager = new RepairManager()
    var nodeMirrors: mutable.Map[String, NodeMirror] = mutable.Map.empty

    var hardwareNodeIps: List[String] = _
    var freeHardwareNodeIps: ListBuffer[String] = new ListBuffer
}
