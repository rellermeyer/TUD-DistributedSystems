package org.orleans.developer.crypto

import org.orleans.developer.crypto.CryptoMessages.{FindMessage, MessageFound, MessageNotFound}
import org.orleans.silo.services.grain.GrainReference

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HasherRef extends GrainReference {

  def findHash(startValue: Int, endValue: Int, hash: String): Future[Option[Int]] = {
    (this.grainRef ? FindMessage(startValue, endValue, hash)).map {
      case MessageFound(value, hash) => Some(value)
      case MessageNotFound(hash) => None
    }
  }

}
