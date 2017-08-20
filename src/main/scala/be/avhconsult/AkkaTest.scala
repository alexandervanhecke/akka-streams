package be.avhconsult

import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.util.ByteString

import scala.concurrent._
import scala.concurrent.duration._
import java.nio.file.Paths

import scala.util.{Failure, Success, Try}


object AkkaTest extends App {

  implicit val system = ActorSystem("AkkaTest")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val source: Source[Int, NotUsed] = Source(1 to 100)
  val numbers: Source[Int, NotUsed] = Source(0 to 100)

  def lineSink(filename: String): Sink[String, Future[IOResult]] =
    Flow[String]
      .map(s => ByteString(s"$s\n"))
      .toMat(FileIO.toPath(Paths.get(filename)))(Keep.right)

  def doComplete[T](done: Try[T])(implicit system: ActorSystem) = done match {
    case Success(result) =>
      println(result)
      system.terminate()
    case Failure(f) =>
      f.printStackTrace()
      system.terminate()
  }

  def example1()(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext) = {
    val done: Future[Done] = source.runForeach(i => println(i))
    done.onComplete(_ => system.terminate())
  }

  val factorials: Source[BigInt, NotUsed] = source.scan(BigInt(1))((acc, elt) => acc * elt)

  def example2()(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext) = {
    factorials
      .map(num => ByteString(s"$num\n"))
      .runWith(FileIO.toPath(Paths.get("/develop/files/factorial.txt")))
      .onComplete(doComplete[IOResult])
  }

  def example3()(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext) = {
    factorials
      .map(_.toString)
      .runWith(lineSink("/develop/files/factorial.txt"))
      .onComplete(doComplete[IOResult])
  }

  def example4()(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext) = {
    factorials
      .zipWith(numbers)((factorial, num) => s"$num! = $factorial")
      .throttle(1, 100.milliseconds, 1, ThrottleMode.shaping)
      .runWith(Sink.foreach(println))
      .onComplete(doComplete[Done])
  }


  example4()

}