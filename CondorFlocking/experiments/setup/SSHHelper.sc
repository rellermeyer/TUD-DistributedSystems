//#!/usr/bin/env amm
import $ivy.`com.hierynomus:sshj:0.27.0`

import java.security.PublicKey
import java.util.concurrent.TimeUnit
import java.io.File

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.xfer.FileSystemFile

class SSHHelper(user: String, host: String, keyFile: String) {
  private val client = new SSHClient()
  private lazy val DontVerify: HostKeyVerifier =
    (_: String, _: Int, _: PublicKey) => true

  client.addHostKeyVerifier(DontVerify)
  client.useCompression()
  client.connect(host)
  client.authPublickey(user, keyFile)


  def executeCommand(command: String, timeout: Int): String = {
    val session = client.startSession()
    val commandObj = session.exec(command)
    val result = IOUtils.readFully(commandObj.getInputStream)
    commandObj.join(timeout, TimeUnit.SECONDS)
    session.close()
    result.toString
  }

  def executeCommand(command: String): String = executeCommand(command, 5)

  def copyFileToServer(filePath: String, targetPath: String)  {
    client.newSCPFileTransfer.upload(new FileSystemFile(filePath), targetPath)
  }

  def copyFileToServer(file: File, targetPath: String)  {
    client.newSCPFileTransfer.upload(new FileSystemFile(file), targetPath)
  }

  def copyFileFromServer(filePath: String, targetFile: String): Unit = {
    client.newSCPFileTransfer.download(filePath, targetFile)
  }

  def copyFileFromServer(filePath: String, targetFile: File): Unit = {
    client.newSCPFileTransfer.download(filePath, new FileSystemFile(targetFile))
  }

  def close(): Unit = {

    client.disconnect()
  }

}