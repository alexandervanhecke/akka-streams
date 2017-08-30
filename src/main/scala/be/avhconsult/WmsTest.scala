package be.avhconsult

import java.io.{File, FileOutputStream}
import java.nio.ByteBuffer
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent._
import be.wegenenverkeer.rxhttp.RxHttpClient
import be.wegenenverkeer.rxhttp.scala.ImplicitConversions._


object WmsTest
  extends App
    with FileSupport
    with ExecutionSupport {

  val workerCount = 5

  implicit val system = ActorSystem("AkkaTest")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(workerCount))

  val basePath = Paths.get("/develop/workspace-awv/elisa-server/tools/tiles")

  val source = FileIO.fromPath(Paths.get("/develop/workspace-awv/elisa-server/tools/tiles/output.csv"))
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 1024))
    .map(_.utf8String)

  val httpClient = new RxHttpClient.Builder()
    .setRequestTimeout(15000)
    .setReadTimeout(10000)
    .setConnectTimeout(3000)
    .setAllowPoolingConnections(true)
    .setMaxConnections(workerCount * 5)
//    .setBaseUrl("http://apigateway:5089/geoserver/wms")
    .setBaseUrl("http://apigateway/geowebcache/service/wms")
    .setExecutorService(Executors.newCachedThreadPool())
    .build.asScala

  case class TileRequest(z: Int,
                         x: Int,
                         y: Int,
                         xmin: Double,
                         ymin: Double,
                         xmax: Double,
                         ymax: Double)

  case class RequestAndResponse(request: TileRequest,
                                response: Array[Byte])

  def lineToTileRequest: Flow[String, TileRequest, NotUsed] =
    Flow[String]
      .map(line => line.split(";"))
      .map(data => TileRequest(data(0).toInt, data(1).toInt, data(2).toInt, data(3).toDouble, data(4).toDouble, data(5).toDouble, data(6).toDouble))
      .filter(request => !Files.exists(getTileFile(request, basePath)))

  def tileRequestToPngOrError(request: TileRequest): Future[Either[String, RequestAndResponse]] = {
    val httpRequest = httpClient.requestBuilder()
      .setMethod("GET")
      .setUrlRelativetoBase("/")
      .addQueryParam("LAYERS", "ELISA")
      .addQueryParam("FORMAT", "image/png8")
      .addQueryParam("UNITS", "m")
      .addQueryParam("SRS", "EPSG:31370")
      .addQueryParam("VERSION", "1.1.1")
      .addQueryParam("SERVICE", "WMS")
      .addQueryParam("REQUEST", "GetMap")
      .addQueryParam("STYLES", "")
      .addQueryParam("EXCEPTIONS", "application/xml")
      .addQueryParam("WIDTH", "256")
      .addQueryParam("HEIGHT", "256")
      .addQueryParam("BBOX", s"${request.xmin},${request.ymin},${request.xmax},${request.ymax}")
      .build()

//    println(s"launching another request, ${httpRequest.toString}")
    val result: Future[(Int, ByteBuffer)] = httpClient.execute(httpRequest, serverResponse => serverResponse.getStatusCode -> serverResponse.getResponseBodyAsByteBuffer)

    result
      .map {
      case (200, bytebuffer) if new String(bytebuffer.array()).contains("xml version") =>
        Left(s"Tile Request $request generated an XML value")
      case (200, bytebuffer)  =>
        Right(RequestAndResponse(request, bytebuffer.array()))
      case (statusCode, _) =>
        Left(s"received status code $statusCode")
    }
    .recover { case f: Exception => Left(s"received a failure ${f.getMessage} for tile request $request and HTTP request $httpRequest") }
  }

  def getTileFile(request: TileRequest, baseDir: Path, tag : String = "") : Path = {
    val layerdir = baseDir.resolve("elisa-kaart")
    val zdir = layerdir.resolve(request.z.toString)
    val xdir = zdir.resolve(request.x.toString)
    xdir.resolve(s"${request.y}.png$tag")
  }

  def writeTileFile(request: TileRequest, img : Array[Byte], baseDir: Path) : String = {
    // write the tile to a temp file, if the application  crashes while writing, the file wil remain in _processing
    val tempFile = getTileFile(request, baseDir, "_processing")
    Files.deleteIfExists(tempFile)
    Files.createDirectories(tempFile.getParent)
    Files.write(tempFile, img)
    // rename the temp file (atomic operation)
    val finalFile = getTileFile(request, baseDir)
    Files.move(tempFile, finalFile)

    s"wrote tile: ${request.z} ${request.x} ${request.y} ${finalFile.toString}"
  }

  def treatErrors =
    Flow[Either[String, RequestAndResponse]]
      .filter(_.isLeft)
      .map(_.left.get)
      .to(Sink.foreach(println))

  def tileRequestToAnswer: Flow[TileRequest, RequestAndResponse, NotUsed] =
    Flow[TileRequest]
      .flatMapConcat(request => Source.fromFuture(tileRequestToPngOrError(request)))
      .alsoTo(treatErrors)
      .filter(_.isRight)
      .map(_.right.get)

  def writeTile: Flow[RequestAndResponse, String, NotUsed] =
    Flow[RequestAndResponse]
      .zipWith(Source.apply(Stream.from(1)))(Keep.both)
      .map { case (reqres, number) =>
        if (number % 100 == 0) {
          println(s"processed $number lines")
        }
        writeTileFile(reqres.request, reqres.response, basePath)
      }

  val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
    import GraphDSL.Implicits._
    val in = source.async
    val out = lineSink("/develop/workspace-awv/elisa-server/tools/tiles/output-flow.txt")

    val bcast = builder.add(Balance[TileRequest](workerCount))
    val merge = builder.add(Merge[RequestAndResponse](workerCount))

    in ~> lineToTileRequest ~> bcast.in
    for (_ <- 1 to workerCount) {
      bcast ~> tileRequestToAnswer.async ~> merge
    }
    merge ~> writeTile ~> out

    ClosedShape
  })

  g.run()

  println("Done.")

}