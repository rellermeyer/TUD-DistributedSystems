package main.scala.tact.protocol

import java.rmi.Remote

/**
  * RoundProtocol.
  */
trait RoundProtocol extends Remote {

  /**
    * Start the round protocol of a conit key
    *
    * @param key of type Char
    */
  def start(key: Char)

}
