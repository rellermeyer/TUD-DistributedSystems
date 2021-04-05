#!/usr/bin/env amm
//This is an Ammonite script. Do not try to run with IntelliJ

import $ivy.`org.slf4j:slf4j-nop:1.7.26`

import $ivy.`com.iheart::ficus:1.4.5`
import $file.SSHHelper
import com.typesafe.config.ConfigFactory
import java.io.File
def getListOfFiles(dir: String):List[File] = {
  val d = new File(dir)
  if (d.exists && d.isDirectory) {
    d.listFiles.filter(_.isFile).toList
  } else {
    List[File]()
  }
}
def install(host: String, name: String, withoutGui: Boolean) {
  val ssh = new SSHHelper.SSHHelper("in4391", host, "./id_rsa")
  val result = ssh.executeCommand("ls in4391_installed")
  if (result.length == 0) {
    println(s"Installing on $host, this can take a couple of minutes")
    ssh.executeCommand("sudo apt update")
    val installationResult = ssh.executeCommand("sudo apt install -y openjfx", 120)
    ssh.copyFileToServer("../../target/scala-2.12/CondorFlocking-assembly-0.1.jar",
      "/home/in4391/")
    val fileList = getListOfFiles(s"./servers/$name")
    fileList.foreach { file =>
      ssh.copyFileToServer(file, "/home/in4391/")
    }
    if(withoutGui){
      //Install our program as a service
      ssh.copyFileToServer("./services/condor.service",
        "/home/in4391/")
      ssh.executeCommand("sudo mv /home/in4391/condor.service /etc/systemd/system/")
      ssh.executeCommand("sudo systemctl daemon-reload")
      ssh.executeCommand("sudo systemctl enable condor")
      ssh.executeCommand("sudo systemctl start condor")
    }else{
      //install vnc
      ssh.executeCommand("sudo DEBIAN_FRONTEND=noninteractive apt remove -q -y gdm3", 60)
      ssh.executeCommand("sudo DEBIAN_FRONTEND=noninteractive apt install -q -y tightvncserver lxde", 600)
      //Screensaver is a bit annoying
      ssh.executeCommand("sudo DEBIAN_FRONTEND=noninteractive apt remove -q -y xscreensaver", 60)

      //Copy configuration
      ssh.executeCommand("mkdir -p .vnc")
      ssh.copyFileToServer("./config/passwd",
        "/home/in4391/.vnc/")
      ssh.copyFileToServer("./config/xstartup",
        "/home/in4391/.vnc/")
      ssh.executeCommand("chmod +x /home/in4391/.vnc/xstartup")
      ssh.executeCommand("chmod 600 /home/in4391/.vnc/passwd")
      //Add VNC as a service
      ssh.copyFileToServer("./services/vnc@.service",
        "/home/in4391/")
      ssh.executeCommand("sudo mv /home/in4391/vnc@.service /etc/systemd/system/")
      ssh.executeCommand("sudo systemctl daemon-reload")
      ssh.executeCommand("sudo systemctl enable vnc@1")
      ssh.executeCommand("sudo systemctl start vnc@1")
    }
    ssh.executeCommand("touch in4391_installed")
    println(s"Installed on $host")
  } else {
    println(s"Already there on $host, updating")
    if(withoutGui)
      ssh.executeCommand("sudo systemctl stop condor")
    ssh.copyFileToServer("../../target/scala-2.12/CondorFlocking-assembly-0.1.jar",
      "/home/in4391/")
    val fileList = getListOfFiles(s"./servers/$name")
    fileList.foreach { file =>
      ssh.copyFileToServer(file, "/home/in4391/")
    }
    if(withoutGui) {
      ssh.copyFileToServer("./services/condor.service",
        "/home/in4391/")
      ssh.executeCommand("sudo mv /home/in4391/condor.service /etc/systemd/system/")
      ssh.executeCommand("sudo systemctl daemon-reload")
      ssh.executeCommand("sudo systemctl start condor")
    }else
      ssh.executeCommand("chmod +x run.sh")
  }
  ssh.close()
}
val config = ConfigFactory.parseFile(new File("config/servers.conf"))
val servers = config.getList("servers")
servers.forEach{server =>
  val serverConf = server.atKey("s")
  val address = serverConf.getString("s.address")
  val withoutGui = serverConf.getString("s.type") == "standard"
  val name = serverConf.getString("s.name")
  println(s"Processing config for $name")
  install(address, name, withoutGui)
}