package logic.wall

import logic.login.LocatorInfo

object FileOperations {
  /**
   * DHTFileEntry is a file entry stored in DHT:
   * "${hashedMail}@${fileType} -> ${hashedMail}#${locator}#${version}"
   */
  case class DHTFileEntry(hashedMail: String, locator: LocatorInfo, version: Int)
}
