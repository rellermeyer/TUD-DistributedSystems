package chubby.common

import java.io.{File, PrintWriter}

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.{AmazonClientException, AmazonServiceException}
import org.apache.commons.io.{FileUtils, IOUtils}

import scala.xml.{Elem, XML}

final case class FileSystemManagerException(
    private val message: String = "",
    private val cause: Throwable = None.orNull
) extends Exception(message, cause)

class FileSystemManager {
  val config: Elem = XML.loadFile("config.xml")
  val BUCKET_NAME: String = (config \\ "BUCKET_NAME").text
  val AWS_ACCESS_KEY: String = (config \\ "AWS_ACCESS_KEY").text
  val AWS_SECRET_KEY: String = (config \\ "AWS_SECRET_KEY").text

  var _client: AmazonS3Client = _

  def getFileSystemClient: AmazonS3Client = {
    if (this._client == null) {
      val awsCredentials = new BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY)
      this._client = new AmazonS3Client(awsCredentials)
    }

    this._client
  }

  def does_file_exist(filename: String): Boolean = {
    val FILE_NAME = filename

    var result = false

    try {
      val amazonS3Client = this.getFileSystemClient

      try {
        amazonS3Client.getObjectMetadata(BUCKET_NAME, FILE_NAME).getRawMetadata
        result = true
      } catch {
        case ase: AmazonServiceException =>
          // File does not exist.
          if (ase.getStatusCode == 404) {
            result = false
          }
      }

    } catch {
      case ase: AmazonServiceException =>
        System.err.println("Service Exception: " + ase.toString)
        throw FileSystemManagerException("Service Exception: " + ase.toString)
      case ace: AmazonClientException =>
        System.err.println("Client Exception: " + ace.toString)
        throw FileSystemManagerException("Client Exception: " + ace.toString)
    }

    result
  }

  def get_file_from_filesystem(filename: String): String = {
    println(s"[FILESYSTEM] Reading from '${filename}'")
    val amazonS3Client = this.getFileSystemClient
    val obj = amazonS3Client.getObject(BUCKET_NAME, filename)

    IOUtils.toString(obj.getObjectContent)
  }

  def write_file_to_filesystem(filename: String, file_content: String): Unit = {
    println(s"[FILESYSTEM] Writing to '${filename}'")
    val amazonS3Client = this.getFileSystemClient
    val temp_file = writeToTempFile(file_content, "tmp", ".txt")

    FileUtils.readFileToString(temp_file)

    amazonS3Client.putObject(BUCKET_NAME, filename, temp_file)
  }

  def writeToTempFile(contents: String, prefix: String, suffix: String): File = {
    val temp_file = File.createTempFile(prefix, suffix)
    temp_file.deleteOnExit()
    new PrintWriter(temp_file) {
      try {
        write(contents)
      } finally {
        close()
      }
    }
    temp_file
  }

}
