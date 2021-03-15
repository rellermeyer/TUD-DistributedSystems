package FileSystem

class FileSuite (suiteId: Int){

  /**
   * constructor
   */
  private val _suiteId: Int = suiteId
  private val _suiteR: Int = suiteR
  private val _suiteW: Int = suiteW

  private val _repsList: Seq[ContainerResponse] = scala.collection.immutable.Vector.empty


  /**
   * accessor methods
   */
  def suiteId: Int = _suiteId
  def suiteR: Int = _suiteR
  def suiteW: Int = _suiteW



  // def repWeights: Seq[Int] = _repWeights

  // def getRep(i: Int): Representative = _repsList(i)
  // def addRep(rep: Representative): Seq[Representative] = _repsList :+ rep
  // def removeRep(i: Int) = _repsList.





}
