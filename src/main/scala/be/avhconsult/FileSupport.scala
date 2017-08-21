package be.avhconsult

import java.nio.file.Paths

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink}
import akka.util.ByteString

import scala.concurrent.Future

trait FileSupport {

  def lineSink(filename: String): Sink[String, Future[IOResult]] =
    Flow[String]
      .map(s => ByteString(s"$s\n"))
      .toMat(FileIO.toPath(Paths.get(filename)))(Keep.right)

}
