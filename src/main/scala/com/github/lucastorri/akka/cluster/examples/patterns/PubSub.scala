package com.github.lucastorri.akka.cluster.examples.patterns

import akka.actor._
import akka.contrib.pattern.{DistributedPubSubExtension, DistributedPubSubMediator}
import com.github.lucastorri.akka.cluster.examples.ClusterSeed
import com.github.lucastorri.akka.cluster.examples.traits.Identified
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.Random

object PubSub {

  val topic = "messages"

  def start[C <: Component](port: Int)(implicit c: ClassTag[C]): (ActorSystem, ActorRef) = {
    val config = ConfigFactory.parseString(
      s"""
         |akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
         |
         |akka.remote.netty.tcp.port = $port
         |akka.remote.netty.tcp.hostname = 127.0.0.1
         |
         |akka.extensions = ["akka.contrib.pattern.ClusterReceptionistExtension"]
         |
         |akka.cluster.seed-nodes = ["akka.tcp://${ClusterSeed.name}@127.0.0.1:${ClusterSeed.port}"]
         |
         |akka.cluster.auto-down-unreachable-after = 10s
       """.stripMargin)

    val system = ActorSystem(ClusterSeed.name, config)

    system -> system.actorOf(Props[C])
  }

  sealed trait Component extends Actor with Identified {

    val mediator = DistributedPubSubExtension(context.system).mediator

    override def receive: Receive = {
      case msg if handle.isDefinedAt(msg) => handle(msg)
      case _ => println(s"Unknown message for $id")
    }

    def handle: Receive

  }

  class Publisher extends Component {
    import context._

    val publishInterval = 10.seconds

    override def preStart(): Unit =
      system.scheduler.schedule((3 + Random.nextInt(7)).seconds, publishInterval, self, 'publish)

    override def handle: Receive = {
      case 'publish =>
        val msg = Message(id, System.currentTimeMillis.toString)
        println(s"$id is publishing ${msg.content}")
        mediator ! DistributedPubSubMediator.Publish(topic, msg)
    }

  }

  class Subscriber extends Component {

    override def preStart(): Unit =
      mediator ! DistributedPubSubMediator.Subscribe(topic, self)

    override def handle: Receive = {
      case _: DistributedPubSubMediator.SubscribeAck =>
      case Message(from, content) => println(s"$id got '$content' from $from")
    }

  }

  case class Message(from: String, content: String)

}

object PubSubMain extends App {

  val seed = ClusterSeed.start

  (1 to 5).foreach(n => PubSub.start[PubSub.Subscriber](3330 + n))
  (1 to 2).foreach(n => PubSub.start[PubSub.Publisher](4440 + n))

  
  Thread.sleep(10000)
  seed.shutdown()
}
