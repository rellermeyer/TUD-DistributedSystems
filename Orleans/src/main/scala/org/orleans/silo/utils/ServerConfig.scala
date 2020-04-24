package org.orleans.silo.utils

/**
  * Class used to configure the settings for both master and slave servers
  * @param host address
  * @param udpPort host port
  * @param tcpPort port to deploy the RPC endpoint
  */
case class ServerConfig(host: String,
                        udpPort: Int,
                        tcpPort: Int,
                        grainPorts: Set[Int] = Set())
