package helper

import java.io.File
import java.nio.file.Paths

import core.Node
import invalidationlog.CheckpointItem

/**
  * Helper class that is responsible for checkpoint seeding
  *
  * @param node
  */
class CheckpointSeeder(node: Node) {
  /**
    * Function that seeds checkpoint from files found in root dir.
    */
  def seedCheckpoint(): Unit = {
    // get File paths in root recursively, remove directories
    val files = getFilesR(new File(node.dataDir)).filter(!_.isDirectory)

    // Strip object Ids (remove root from path)
    files.foreach(f => {
      extractId(f.getPath) match {
        case Some(bodyId) => {

          // Create and insert body to checkpoint
          val bod = node.createBody(bodyId)
          val chkIt = new CheckpointItem(bod.path, bod, false, node.clock.time)
          node.checkpoint.update(chkIt)
        }
        case None => throw new IllegalArgumentException("Could not extract file id from File path")
      }
    })
  }


  /**
    * Function that extracts root dir from file path.
    *
    * @param path relative path of a file
    * @return object ID
    */
  private def extractId(path: String): Option[String] = {
    path match {
      case s if s.startsWith(Paths.get(node.dataDir).toString) => Some(s.stripPrefix(Paths.get(node.dataDir).toString))
      case _ => None
    }
  }

  /**
    * Recursive helper function, that traverses all files given in a path
    *
    * @param f root file, from where recursive file search is executed
    * @return
    */
  private def getFilesR(f: File): Array[File] = {
    val mbFile = Option(f.listFiles)
    var these = Array[File]()

    mbFile match {
      case Some(f) => these = f ++ f.filter(_.isDirectory).flatMap(getFilesR)
      case _ => // do nuffin if null
    }

    return these
  }
}
