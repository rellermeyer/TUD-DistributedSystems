import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import ZeusSuperVisor.{BroadcastTransactions, TransactionStarted}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}
import ZeusSuperVisor.{KeyLocked, OwnershipMsg, OwnershipRequest, OwnershipResponse, ReportLockedData, ReportOSRequest, ReporterMsg, StartTransaction}
import datatypes.ZeusDataObject.ZeusDataObject

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.DurationInt
import scala.io.{BufferedSource, Source}


object ZeusSuperVisor {

  // Parent type
  sealed trait OwnershipMsg

  sealed trait ReporterMsg

  sealed trait TaskMsg

  // Child types
  final case class OwnershipRequest(key: String, from: ActorRef[OwnershipMsg]) extends OwnershipMsg

  final case class OwnershipResponse(key: String, data: ZeusDataObject, from: ActorRef[OwnershipMsg]) extends OwnershipMsg

  final case class KeyLocked(message: OwnershipMsg) extends OwnershipMsg

  final case class StartTransaction(key: String, ref: ActorRef[OwnershipMsg]) extends OwnershipMsg

  final case class TransactionStarted() extends OwnershipMsg

  final case class ReportOSRequest() extends ReporterMsg

  final case class ReportLockedData() extends ReporterMsg

  final case class BroadcastTransactions(acts: ListBuffer[ActorRef[ZeusSuperVisor.OwnershipMsg]]) extends OwnershipMsg


  val rActors: ListBuffer[ActorRef[ZeusSuperVisor.OwnershipMsg]] = ListBuffer[ActorRef[ZeusSuperVisor.OwnershipMsg]]()

  // Initial datastore
  val ds: mutable.HashMap[String, ZeusDataObject] = new mutable.HashMap()

  def apply(): Behavior[OwnershipMsg] = Behaviors.setup {
    context => {

      implicit val timeout: Timeout = 1.seconds
      implicit val scheduler: Scheduler = context.system.scheduler

      //load the contents of the file
      val bufferedSource: BufferedSource = Source.fromResource("test1.csv")

      for (line <- bufferedSource.getLines) {
        val cols = line.split(",").map(_.trim)

        //populate the data store
        ds.put(cols {
          0
        }, ZeusDataObject("Valid", 1, cols {
          1
        }))
      }

      val reporter = context.spawn(ZeusReporter(), "reporter")
      val broadcaster = context.spawn(ZeusTaskManager(), "taskmanager")

      // Generate 10 actors
      for (i <- 0 to 9) {
        context.log.info("spawn actor: " + i)
        val rAct = context.spawn(ZeusActor(context.self, reporter, broadcaster), "act" + i)
        rActors.addOne(rAct)
      }

      broadcaster ! BroadcastTransactions(rActors);
      
      Behaviors.receiveMessage {
        message => {
          message match {
            // Handle ownership request
            case OwnershipRequest(key, from) => {
              //              context.log.info("OSR from: " + from + " for data " + key);
              if (ds.contains(key)) {
                // If the data hasn't been claimed (thus still in the supervisor)
                context.log.info("Initial ownership of " + key + " given to " + from);
                ds.put(key, ZeusDataObject(ds(key).state, ds(key).version, ds(key).data))
                from ! OwnershipResponse(key, ds.remove(key).get, context.self)
              } else {
                reporter ! ReportOSRequest()
                // If the data is not present, broadcast to all actors except the source the ownership request
                rActors.foreach(x => {
                  if (!x.equals(from)) {
                    x ! message
                  }
                })
              }
            }
            case other => {
              context.log.warn("Supervisor received an unknown message type: " + other.toString)
            }
          }
          Behaviors.same
        }
      }
    }

  }

}
// The data actor

/** TODO Some sort of testing functionality we can call for testing.
 * TODO In this functionality, there should also be some sort of timeout to make sure actors don't get stuck in case they request a non-existent datakey
 * TODO or if the data has moved to another actor during the request. Easiest solution is probably by just retrying with a maximum number of attempts.
 */
object ZeusActor {


  def apply(superVisor: ActorRef[OwnershipMsg], reporter: ActorRef[ReporterMsg], taskmanager: ActorRef[OwnershipMsg]): Behavior[OwnershipMsg] = Behaviors.setup {
    context => {

      val report: ActorRef[ReporterMsg] = reporter
      val sv: ActorRef[OwnershipMsg] = superVisor
      val ds: mutable.HashMap[String, ZeusDataObject] = new mutable.HashMap()

      // Change data function on given key
      def changeData(key: String, from: ActorRef[OwnershipMsg]): Unit = {

        // check if it currently owns the data for the key
        if(ds.contains(key)) {
          taskmanager ! TransactionStarted()
          val currentData = ds(key)

          // Lock the data in the data store
          ds.put(key, ZeusDataObject("locked", currentData.version, currentData.data))
          val newData = context.self.path.name + currentData.data.slice(3, currentData.data.length)
          val updatedData = ZeusDataObject("Valid", currentData.version + 1, newData)

          // Put the updated data back with 'Valid' status
          ds.put(key, updatedData)
          //          context.log.info("changed data for " + key + " to " + ds.get(key))
        } else {
          sv ! OwnershipRequest(key, context.self)
        }
      }

      context.log.info("Setup finished for " + context.self.path.name + " , initial datastore has " + ds.size + " entries")

      // Receive the message as an actor of the system
      Behaviors.receiveMessage {
        message => {

          message match {

            // Handle ownership request
            case OwnershipRequest(key, from) => {
              if (ds.contains(key)) {
                // if the data is locked(being changed) report that back to original data requester
                if (ds(key).state == "locked") {
                  from ! KeyLocked(message)
                } else {
                  // Send a message to 'from' containing the data. Also, remove the data from it's own hashmap
                  //                  context.log.info("transfer ownership of " + key + " to " + from)
                  from ! OwnershipResponse(key, ds.remove(key).get, context.self)
                }
              }
            }

            // Handle ownership response
            case OwnershipResponse(key, data, from) => {
              //              context.log.info("received data: " + data.toString + " from " + from)
              val tmpSize = ds.size
              ds.put(key, ZeusDataObject("Valid", data.version, data.data))
//              context.log.info("DS SIZE: " + tmpSize + " -> " + ds.size)
              //change the data after receiving it from 'from'
              changeData(key, from)
            }

            case StartTransaction(key, from) => {
              changeData(key, from)
//              from ! TransactionStarted()
            }

            // handle locked key in current owner
            case KeyLocked(message) => {
              context.log.info("Asking for resource again in 1 second: " + message)
              report ! ReportLockedData()
              // retry to get the resource after 1 second
              context.scheduleOnce(1.seconds, sv, message)
            }
            case _ => {
              context.log.info("")
            }
          }
          Behaviors.same
        }
      }
    }
  }
}


object ZeusReporter {

  def apply(): Behavior[ReporterMsg] = Behaviors.setup {

    var countOSRequest = 0
    var countLockedData = 0

    context => {
      Behaviors.receiveMessage {
        message => {
          message match {
            case ReportOSRequest() => {
              countOSRequest = countOSRequest + 1;
//                            context.log.info("OS Request made, currently at: " + countOSRequest)
              context.log.info("OSR: " + countOSRequest + " - LD: " + countLockedData)
            }
            case ReportLockedData() => {
              countLockedData = countLockedData + 1;
//                            context.log.info("Data locked returned, currently at: " + countLockedData)
              context.log.info("OSR: " + countOSRequest + " - LD: " + countLockedData)
            }
          }
        }
          Behaviors.same
      }
    }
  }
}


object ZeusTaskManager {

//  def performTransactions(acts: ListBuffer[ActorRef[ZeusSuperVisor.OwnershipMsg]], actorContext: ActorContext[TaskMsg]): Unit = {
//    implicit val timeout: Timeout = 5.seconds
//    implicit val scheduler: Scheduler = actorContext.system.scheduler
//    implicit val system: ActorSystem[_] = actorContext.system
//    implicit val ec: ExecutionContextExecutor = actorContext.system.executionContext
//    //    val bufferedSource : BufferedSource = Source.fromResource("tmp_test_transactions.csv")
//    //    val bufferedSource : BufferedSource = Source.fromResource("sharedresourcescase.csv")
//    //    val bufferedSource : BufferedSource = Source.fromResource("simpletestcase0.csv")
//    val bufferedSource: BufferedSource = Source.fromResource("simpletestcaseXX.csv")
//    //    val bufferedSource : BufferedSource = Source.fromResource("concurrencycase.csv")
//
//    // TODO Mark start time
//    for (line <- bufferedSource.getLines) {
//      val cols = line.split(",").map(_.trim)
//      //populate the data store
//
//      acts.foreach(x => {
//        // TODO Break loop if match is found.
//        if (x.path.toString.equals("akka://supervisor/user/" + cols {0})) {
//
//          val result: Future[OwnershipMsg] = x.ask(ref => StartTransaction(cols{1}, ref))
//          result.onComplete{
//            case Success(TransactionStarted()) => println("succesful response")
//            case Failure(exception) => println(s"${exception.getMessage}")
//          }
//          actorContext.log.info("test")
//        }
//      })
//    }
//    // TODO Mark endtime
//  }

  def apply(): Behavior[OwnershipMsg] = Behaviors.setup {

    context => {
      implicit val timeout: Timeout = 5.seconds
      implicit val scheduler: Scheduler = context.system.scheduler
      implicit val system: ActorSystem[_] = context.system
      implicit val ec: ExecutionContextExecutor = context.system.executionContext
//      val bufferedSource: BufferedSource = Source.fromResource("simpletestcase0.csv")
      val bufferedSource: BufferedSource = Source.fromResource("concurrencycase0.csv")
//      val bufferedSource: BufferedSource = Source.fromResource("simpletestcase2.csv")
//      val bufferedSource: BufferedSource = Source.fromResource("sharedresourcescase.csv")
//            val bufferedSource: BufferedSource = Source.fromResource("concurrencycase7.csv")
//        val bufferedSource: BufferedSource = Source.fromResource("concurrencycase5A.csv")

      Behaviors.receiveMessage {
        message => {
          message match {

            case BroadcastTransactions(acts) => {
              for (line <- bufferedSource.getLines) {
                val cols = line.split(",").map(_.trim)
                //populate the data store

                acts.foreach(x => {
                  // TODO Break loop if match is found.
                  if (x.path.toString.equals("akka://supervisor/user/" + cols {
                    0
                  })) {

                    val result: Future[OwnershipMsg] = x.ask(ref => StartTransaction(cols{1}, ref))
                    result.onComplete{
                      case Success(TransactionStarted()) => println("succesful response")
                      case Failure(exception) => {}
                    }
                  }
                })
              }
            }

            case _ => {

            }
          }
        }
          Behaviors.same
      }
    }
  }
}

//#main-class
object Zeus extends App {

  val superVisor: ActorSystem[ZeusSuperVisor.OwnershipMsg] = ActorSystem(ZeusSuperVisor(), "supervisor")

}
//#main-class
//#full-example
