#!/usr/bin/env amm
import $ivy.`org.slf4j:slf4j-nop:1.7.26`

import $ivy.`com.iheart::ficus:1.4.5`
import $file.SSHHelper
import com.typesafe.config.ConfigFactory
import java.io.File
import scala.collection.JavaConverters._

@doc("Script to collect log files from all servers into a given directory")
@main
def collect(name: String @doc("Name of the directory")): Unit = {
  val dir = new File(name)
  if(!dir.exists()) dir.mkdir()
  val config = ConfigFactory.parseFile(new File("config/servers.conf"))
  val servers = config.getList("servers")
  val conn = servers.asScala.map{
    server =>
      val serverConf = server.atKey("s")
      val address = serverConf.getString("s.address")
      val serverName = serverConf.getString("s.name")
      val withoutGui = serverConf.getString("s.type") == "standard"
      println(s"Connecting to $serverName")
      val ssh = new SSHHelper.SSHHelper("in4391", address, "./id_rsa")
      if(withoutGui)
        ssh.executeCommand("sudo systemctl stop condor")
      (ssh, serverName, withoutGui)
  }
  conn.foreach{ case (ssh, serverName, withoutGui) =>
    println(s"Getting log file from $serverName")
    ssh.copyFileFromServer("log/condor.log", dir + File.separator + s"$serverName.log")
    if(withoutGui)
      ssh.executeCommand("sudo systemctl start condor")
    ssh.close()
  }
}