package be.avhconsult

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent.Future

object ReactiveTweets
  extends App
    with ExecutionSupport
    with FileSupport {

  final case class Author(handle: String)

  final case class Hashtag(name: String)

  final case class Tweet(author: Author, timestamp: Long, body: String) {
    def hashtags: Set[Hashtag] =
      body.split(" ").collect { case t if t.startsWith("#") => Hashtag(t) }.toSet
  }

  val akkaTag = Hashtag("#akka")

  implicit val system = ActorSystem("reactive-tweets")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val tweets: Source[Tweet, NotUsed] =  Source(
    Tweet(Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
      Tweet(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
      Tweet(Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
      Tweet(Author("drewhk"), System.currentTimeMillis, "#akka !") ::
      Tweet(Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
      Tweet(Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
      Tweet(Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
      Tweet(Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
      Tweet(Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
      Tweet(Author("drama"), System.currentTimeMillis, "we compared #apples to #oranges!") ::
      Nil)


  val authors: Source[Author, NotUsed] =
    tweets
      .filter(_.hashtags.contains(akkaTag))
      .map(_.author)

  val hashtags: Source[Hashtag, NotUsed] =
    tweets
      .mapConcat(_.hashtags)


  def example1(): Unit = {
    authors
      .runWith(Sink.foreach(println))
      .onComplete(doComplete[Done])
  }

  val writeAuthors: Sink[Author, NotUsed] = Flow[Author]
    .toMat(Sink.foreach(println))(Keep.left)

  val writeHashtags: Sink[Hashtag, NotUsed] = Flow[Hashtag]
    .toMat(Sink.foreach(println))(Keep.left)

  val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import GraphDSL.Implicits._

    val bcast = b.add(Broadcast[Tweet](2))
    tweets ~> bcast.in
    bcast.out(0) ~> Flow[Tweet].map(_.author) ~> writeAuthors
    bcast.out(1) ~> Flow[Tweet].mapConcat(_.hashtags.toList) ~> writeHashtags
    ClosedShape
  })

  def example2(): Unit = {
    g.run()
    system.terminate()
  }

  def example3(): Unit = {
    tweets
      .buffer(10, OverflowStrategy.dropHead)
      .map(t => t)
      .runWith(Sink.foreach(println))
      .onComplete(doComplete[Done])
  }

  val count: Flow[Tweet, Int, NotUsed] = Flow[Tweet].map(_ => 1)

  val sumSink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)((acc, elt) => acc + elt)

  val counterGraph: RunnableGraph[Future[Int]] =
    tweets
      .via(count)
      .toMat(sumSink)(Keep.right)

  def example4(): Unit = {
    counterGraph
      .run()
      .foreach(c => {
        system.terminate()
        println(s"processed $c tweets so far")
      })

  }

  example4()

}
