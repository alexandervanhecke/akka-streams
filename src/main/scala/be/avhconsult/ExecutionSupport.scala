package be.avhconsult

import akka.actor.ActorSystem

import scala.util.{Failure, Success, Try}

trait ExecutionSupport {

  def doComplete[T](done: Try[T])(implicit system: ActorSystem) = done match {
    case Success(result) =>
      println(result)
      system.terminate()
    case Failure(f) =>
      f.printStackTrace()
      system.terminate()
  }

}
