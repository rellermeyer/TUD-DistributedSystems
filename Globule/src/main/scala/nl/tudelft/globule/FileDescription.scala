package nl.tudelft.globule

class FileDescription(val servername: String, val filename: String) extends Serializable { // e.g.: filename = /my/path/some_file.png servername = uir-adihfa
  def filePath: String = {
    Configs.DATA_DIR + "/" + servername + "/" + filename
  }
}
