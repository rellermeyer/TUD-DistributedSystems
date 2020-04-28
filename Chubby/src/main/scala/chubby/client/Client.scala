package chubby.client

import java.util.UUID.randomUUID
import java.util.concurrent.TimeUnit

import chubby.common.{FileSystemManager, Lock, TimeoutFuture}
import chubby.grpc.{ChubbyService, LockRequest}

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success}

// Todo: Implement a background process to continuously renew the lease of all locks
class Client(val identifier: String, val replicaAddresses: List[String]) {
  val fileSystemManager: FileSystemManager = new FileSystemManager
  val leaderManager: LeaderManager = new LeaderManager(identifier, replicaAddresses)
  val lockManager: LockManager = new LockManager()

  def this(replicaAddresses: List[String]) {
    this(randomUUID().toString, replicaAddresses)
  }

  def lock[T](identifier: String, timeout: Duration = Duration(10, TimeUnit.SECONDS), startTime: Long = System.currentTimeMillis())(
      callback: => T
  ): Unit = {
    this.processLock(identifier, writeable = true, timeout, startTime)(callback)
  }

  def read[T](identifier: String, timeout: Duration = Duration(10, TimeUnit.SECONDS), startTime: Long = System.currentTimeMillis())(
      callback: => T
  ): Unit = {
    this.processLock(identifier, writeable = false, timeout, startTime)(callback)
  }

  def readFile(identifier: String, timeout: Duration = Duration(10, TimeUnit.SECONDS)): Unit = {
    this.read(identifier, timeout)(
      this.fileSystemManager.get_file_from_filesystem(identifier)
    )
  }

  def writeFile(identifier: String, content: String, timeout: Duration = Duration(10, TimeUnit.SECONDS)): Unit = {
    this.lock(identifier, timeout)(
      this.fileSystemManager.write_file_to_filesystem(identifier, content)
    )
  }

  private def processLock[T](
      identifier: String,
      writeable: Boolean = false,
      timeout: Duration = Duration.Inf,
      startTime: Long
  )(
      callback: => T
  ): Unit = {
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

    // If the valid lock is already acquired, no need to contact the Chubby cluster.
    if (this.lockManager.containsValidLock(identifier, writeable)) {
      util.Try(callback)
      return
    }

    // Determine the current leader settings (With exponential backoff)
    val leaderService: Future[ChubbyService] = this.leaderManager.determineLeaderService()

    leaderService onComplete {
      case Failure(t) => println("An error has occurred: " + t.getMessage)
      case Success(service) => {
        val responseUnprocessed = service.requestLock(LockRequest(this.identifier, identifier, writeable))

        var response = TimeoutFuture.futureWithTimeout(responseUnprocessed, FiniteDuration(timeout.toSeconds, TimeUnit.SECONDS))

        response onComplete {
          // Message received from the Chubby master confirming a successful lock
          case Success(lock) if lock.locked =>
            println(
              s"[request time took ${System.currentTimeMillis() - startTime} millisecs] Lock '${lock.lockIdentifier}' ('${lock.write}') acquired!"
            )
            this.lockManager.append(new Lock(lock.lockIdentifier, lock.timeLocked, lock.timeGranted, lock.write))
            util.Try(callback)

          // Message received from the Chubby master denying the lock
          case Success(lock) if !lock.locked =>
            throw new Exception(s"[${System.currentTimeMillis()}] Lock '${lock.lockIdentifier}' is not acquired!")

          // No message received from the Chubby master, retry the communication
          case _ =>
            println(
              s"[${System.currentTimeMillis()}] Communication with the current Chubby master timed out, determining the new master."
            )
            this.leaderManager.leaderAddressFuture = null
            this.processLock(identifier, writeable, timeout, startTime)(callback)
        }
      }
    }
  }
}
