package services

object GetPeerKey {
  /**
   * user and location as a key of peers
   *
   * @param user     mail (not hashed)
   * @param location location string
   */
  def apply(user: String, location: String): String = {
    s"'$user'@$location"
  }
}
