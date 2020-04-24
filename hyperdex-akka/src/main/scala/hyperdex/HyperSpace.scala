package hyperdex

import hyperdex.API.{Attribute, AttributeMapping, Key}

import scala.collection.immutable.Set

// axis 0: key, axis 1..n: attr1 ... attrn
case class Region(sectionPerAxis: Seq[Int])

class HyperSpace(val attributes: Seq[String], val amountNodes: Int, val cutsPerAxis: Int) {

  class KeySubspace(val amountNodes: Int) {
    def getResponsibleNode(key: Key): Int = {
      math.abs(key % amountNodes)
    }
  }

  val _keySubspace = new KeySubspace(amountNodes)

  /**
    * ASSUMPTION: amount of regions is less than integer max value
    * [crash if that does not hold]
    */
  val amountRegions: Double = scala.math.pow(cutsPerAxis, attributes.size + 1)
  assert(amountRegions <= Integer.MAX_VALUE)

  /**
    * for internal use
    */
  val _regionToNodeMapping: Map[Region, Int] = {
    // toInt should not throw because amountRegions < Int.MAX
    val minAmountRegionsPerNode = (amountRegions / amountNodes).toInt
    val amountNodesWithExtraRegion = (amountRegions % amountNodes).toInt
    createRegionNodeMapping(minAmountRegionsPerNode, amountNodesWithExtraRegion)
  }

  /**
    * ASSUMPTIONS:
    * all queries with attributes contain only valid attributes,
    * [no errors when this does not hold, but also no crash]
    */
  /**
    * find responsible node for a lookup
    * @param key
    * @return
    */
  def getResponsibleNodeId(key: Key): Int =
    _keySubspace.getResponsibleNode(key)

  /**
    * find responsible nodes for a new item to be put
    * @param key
    * @param value
    * @return
    */
  def getResponsibleNodeIds(key: Key, value: AttributeMapping): Set[Int] =
    getPossibleRegions(Some(key), value)
      .map(regionToNode)
      .+(_keySubspace.getResponsibleNode(key))

  /**
    * find responsible nodes given a search query
    * @param query
    * @return
    */
  def getResponsibleNodeIds(query: AttributeMapping): Set[Int] =
    getPossibleRegions(None, query).map(regionToNode)

  //---------------------------------------------------------------------------

  private def regionToNode(r: Region): Int = {
    assert(_regionToNodeMapping.get(r).isDefined, s"region: $r is not defined in mapping")
    _regionToNodeMapping(r)
  }

  private def getAxisSections(numAxes: Int, cutsPerAxis: Int): Seq[Set[Int]] = {
    (0 until numAxes).map(_ => (0 until cutsPerAxis).toSet)
  }

  // TODO: (potentially) assign nodes in a way that they get a clustered set of regions
  //  in order to reduce amount of nodes contacted in a search
  /**
    * create a random mapping from regions to nodes which is evenly distributed
    * (amounts of regions a node is responsible for differ at most by 1)
    * @return
    */
  private def createRegionNodeMapping(
    minAmountRegionsPerNode: Int,
    amountNodesWithExtraRegion: Int
  ): Map[Region, Int] = {

    // create regions by splitting each axis into sections as specified by amount of cuts
    // transform sequence of axis sections to all possible regions
    val axisSections = getAxisSections(attributes.size + 1, cutsPerAxis)
    val regions = axisSectionsToRegions(axisSections)

    // a list of responsible nodes where indices signify region ids and elements are node ids
    val responsibleNodes = (0 until amountNodes)
      .flatMap(id => {
        if (id < amountNodesWithExtraRegion)
          List.fill(minAmountRegionsPerNode + 1)(id)
        else
          List.fill(minAmountRegionsPerNode)(id)
      })
    // set of regions and seq of responsible node ids are randomly forming tuples
    regions.zip(responsibleNodes).toMap
  }

  /**
    * @param axisSections : a sequence where index denotes an axis and values are sets of section indices
    * @return the set of regions that corresponds to the areas defined by the axes and their cuts
    */
  private def axisSectionsToRegions(axisSections: Seq[Set[Int]]): Set[Region] = {
    //    @annotation.tailrec
    def getAllAxisSectionTails(startAxis: Int): Set[Seq[Int]] = {
      if (startAxis == axisSections.size) {
        Set.empty[Seq[Int]]
      } else if (startAxis == axisSections.size - 1) {
        axisSections.last.map(Seq(_))
      } else {
        val sections = axisSections(startAxis)
        val result =
          for {
            s <- sections
            tail <- getAllAxisSectionTails(startAxis + 1)
          } yield Seq(s) ++ tail
        result
      }
    }
    getAllAxisSectionTails(0)
      .map(Region)
  }

  /**
    * resolve a query to the regions that results can possibly reside in
    * @param optKey
    * @param attributeValues
    * @return
    */
  private def getPossibleRegions(optKey: Option[Key], attributeValues: AttributeMapping): Set[Region] = {

    def optAttributeToSection(optAtt: Option[Attribute]): Set[Int] = optAtt match {
      case Some(att) => Set(getAttributeSection(att))
      case None      => (0 until cutsPerAxis).toSet
    }

    val keyAxisSections = optAttributeToSection(optKey)
    val attributesSections = attributes
      .map(a => optAttributeToSection(attributeValues.get(a)))
    val axisSections = Seq(keyAxisSections) ++ attributesSections
    axisSectionsToRegions(axisSections)
  }

  private def getAttributeSection(attributeValue: Attribute): Int = {
    math.abs(attributeValue % cutsPerAxis)
  }

}
