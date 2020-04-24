package org.orleans.developer.twitter
import org.orleans.client.OrleansRuntime
import org.orleans.developer.twitter.TwitterMessages._
import org.orleans.silo.services.grain.GrainReference

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TwitterRef extends GrainReference {
  def createAccount(username: String): Future[TwitterAcountRef] = {
    // First we try check if the username already exists,
    // then if not we create a new grain of type TwitterAccount.
    val userExists = (grainRef ? UserExists(username))
    userExists.flatMap {
      case TwitterSuccess() => {
        val grain = OrleansRuntime
          .createGrain[TwitterAccount, TwitterAcountRef](masterRef)

        grain flatMap {
          case x: TwitterAcountRef =>
            (x.grainRef ! SetUsername(username))
            (grainRef ? UserCreate(username, x.grainRef.id)).flatMap {
              case TwitterSuccess() => grain
            }
        } andThen {
          case _ => grain
        }
      }
      case TwitterFailure(msg) =>
        Future.failed(new IllegalArgumentException(s"$username: $msg"))
    }
  }

  def getAccount(username: String): Future[TwitterAcountRef] = {
    (grainRef ? UserGet(username)).flatMap {
      case UserRetrieve(grainId: String) => {
        OrleansRuntime
          .getGrain[TwitterAccount, TwitterAcountRef](grainId, masterRef)
      }
      case TwitterFailure(msg: String) =>
        Future.failed(new IllegalArgumentException(s"$username: $msg"))
    }
  }

}
