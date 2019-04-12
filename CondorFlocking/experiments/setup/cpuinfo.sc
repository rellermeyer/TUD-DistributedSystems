#!/usr/bin/env amm
//This is an Ammonite script. Do not try to run with IntelliJ

import $ivy.`org.slf4j:slf4j-nop:1.7.26`

import $ivy.`com.iheart::ficus:1.4.5`
import $file.SSHHelper
import scala.collection.JavaConverters._

import java.io.File

import com.typesafe.config.ConfigFactory

val config = ConfigFactory.parseFile(new File("config/servers.conf"))
val servers = config.getList("servers")
servers.asScala.foreach{
  server =>
    val serverConf = server.atKey("s")
    val address = serverConf.getString("s.address")
    val serverName = serverConf.getString("s.name")
    println(s"CPU of $serverName")
    val ssh = new SSHHelper.SSHHelper("in4391", address, "./id_rsa")
    println(ssh.executeCommand("lscpu"))
    ssh.close()
}