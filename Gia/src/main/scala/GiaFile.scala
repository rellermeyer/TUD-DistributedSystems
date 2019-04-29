package gia.core

@SerialVersionUID(1L)
class GiaFile(val name: String) extends Serializable{

  override def equals(obj: Any): Boolean = {
    if (obj == null || !obj.isInstanceOf[GiaFile]) {
      return false;
    }
    val otherFile = obj.asInstanceOf[GiaFile]
    return this.name.equals(otherFile.name)
  }

  override def toString: String = {
    return name
  }

}
