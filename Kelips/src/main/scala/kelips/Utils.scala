package kelips

import java.security.MessageDigest

object Utils {

  /**
    * Parse the akka actor into a more readable string
    * @param actorRef The akka actor string to parse
    * @return of the form node{i}.
    */
  def parseActor(actorRef: String): String = {
    actorRef.split('/')(4).split('#')(0)
  }

  def hash(input: String): Int = {
    BigInt(MessageDigest.getInstance("SHA-1").digest(input.getBytes("UTF-8"))).intValue()
  }
}
