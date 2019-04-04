import WrapperId.WrapperId

/**
  * The repair manager, acting on the managed architecture.
  */
class RepairManager extends Serializable {
    /**
      * Callback on node failure.
      */
    def nodeFailed(node: NodeMirror): Unit = {
        if (!JadeManager.isLeader) {
            return
        }

        println(s"Repairing node: ${node.id}")

        val newNode = JadeManager.deployNode(node.id)
        for ((wrapperId, _) <- node.wrappers) {
            val binding = node.wrappers(wrapperId).binding
            if(binding != null) {
                JadeManager.deployWrapper(newNode, wrapperId, Some(binding))
            } else {
                JadeManager.deployWrapper(newNode, wrapperId)
            }
        }

        JadeManager.removeNode(node)
    }

    /**
      * Callback on wrapper failure.
      *
      * @param nodeMirror the node mirror on which the wrapper failed
      * @param wrapper    the ID of the failed wrapper
      */
    def wrapperFailed(nodeMirror: NodeMirror, wrapper: WrapperId): Unit = {
        if (!JadeManager.isLeader) {
            return
        }

        println(s"Repairing wrapper: $wrapper on node ${nodeMirror.id}")
        nodeMirror.restartWrapper(wrapper)
    }
}
