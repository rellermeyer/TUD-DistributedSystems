package nl.tudelft.globule

import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._

object HTMLDocumentSubstitution extends App {
  
  def extractRelativeURLs(htmlString: String): List[String] = {

    val browser = JsoupBrowser()
    val doc = browser.parseString(htmlString)

    val listOfHrefs = doc >> attrs("src")("img")
    val relativeHrefs = listOfHrefs.filter((url: String) => {
      !url.contains("//")
    }).toList.distinct

    relativeHrefs
  }

  def substituteRelativeToNew(mappedUrl: Map[String, String], htmlStringInput: String): String = {

    var htmlString: String = htmlStringInput

    for ((oldUrl, newUrl) <- mappedUrl) {

      val strVar1 = "src='" + oldUrl + "'"
      val strVar2 = "src=" + oldUrl + ""
      val strVar3 = "src=\"" + oldUrl + "\""

      val newStr = "src=\"" + newUrl + "\""


      htmlString = htmlString.replaceAll(strVar1, newStr)
      htmlString = htmlString.replaceAll(strVar2, newStr)
      htmlString = htmlString.replaceAll(strVar3, newStr)

    }

    htmlString
  }
}
