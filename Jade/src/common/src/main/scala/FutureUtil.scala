import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Utilities for managing sequences of Future objects.
  */
object FutureUtil {
    implicit val ec: ExecutionContext = ExecutionContext.global

    /**
      * Executes the given futures.
      *
      * @param futures    the futures to execute
      * @param onComplete callback to call when all futures have either completed or failed
      */
    def executeFutures[T](futures: Seq[Future[T]], onComplete: () => Unit): Unit = {
        Future.sequence(liftFutures(futures)) onComplete {
            case Success(_) =>
                onComplete()
            case Failure(t) => throw new Error("An error has occurred: " + t.getMessage)
        }
    }

    /**
      * Executes the given futures.
      *
      * @param futures    the futures to execute
      * @param onComplete callback to call when all futures have either completed or failed
      */
    def executeFutures[T](futures: Seq[Future[T]], onComplete: Seq[T] => Unit): Unit = {
        Future.sequence(liftFutures(futures)) onComplete {
            case Success(completedFutures) =>
                val states = completedFutures.map {
                    case Success(value) => value
                    case Failure(t) => throw new Error("An error has occurred: " + t.getMessage)
                }
                onComplete(states)
            case Failure(t) => throw new Error("An error has occurred: " + t.getMessage)
        }
    }

    /**
      * Converts the given future sequence into a success-failure handled sequence of futures.
      *
      * @param futures sequence of futures to convert
      * @tparam T the output type
      * @return the converted sequence of futures
      */
    private def liftFutures[T](futures: Seq[Future[T]]): Seq[Future[Try[T]]] =
        futures.map(_.map {
            Success(_)
        }.recover { case t => Failure(t) })
}
