package com.github.lucastorri.akka.cluster.examples.patterns

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{UnreachableMember, InitialStateAsEvents, MemberEvent}
import com.github.lucastorri.akka.cluster.examples.ClusterSeed
import com.github.lucastorri.akka.cluster.examples.traits.Identified
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object Presence {

  def start(port: Int, n: Int): ActorSystem = {
    val config = ConfigFactory.parseString(
      s"""
         |akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
         |
         |akka.remote.netty.tcp.port = $port
         |akka.remote.netty.tcp.hostname = 127.0.0.1
         |
         |akka.cluster.seed-nodes = ["akka.tcp://${ClusterSeed.name}@127.0.0.1:${ClusterSeed.port}"]
         |akka.cluster.auto-down-unreachable-after = 10s
       """.stripMargin)
    val system = ActorSystem(ClusterSeed.name, config)

    system.actorOf(Props(new Attendant(n)))

    system
  }

  def observer: ActorSystem = {
    val config = ConfigFactory.parseString(
      s"""
         |akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
         |
         |akka.remote.netty.tcp.port = 4444
         |akka.remote.netty.tcp.hostname = 127.0.0.1
         |
         |akka.cluster.seed-nodes = ["akka.tcp://${ClusterSeed.name}@127.0.0.1:${ClusterSeed.port}"]
         |akka.cluster.auto-down-unreachable-after = 10s
       """.stripMargin)
    val system = ActorSystem(ClusterSeed.name, config)

    system.actorOf(Props[Observer])

    system
  }

  class Attendant(n: Int) extends Actor with Identified {

    import context._

    val shutdownTimeout = 10.seconds * n

    override def preStart(): Unit = {
      println(s"Attendant $id starting")
      system.scheduler.scheduleOnce(shutdownTimeout) {
        println(s"Attendant $id terminating")
        system.terminate()
      }
    }

    override def receive: Receive = {
      case msg => println(s"Attendant $id $msg")
    }

  }

  class Observer extends Actor {

    val cluster = Cluster(context.system)

    override def preStart(): Unit =
      cluster.subscribe(self, InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])

    override def postStop(): Unit =
      cluster.unsubscribe(self)

    override def receive: Receive = {
      case msg => println(s"Observer $msg")
    }

  }


}

object PresenceMain extends App {

  ClusterSeed.start

  Presence.observer

  (1 to 5).foreach { n =>
    Presence.start(5500 + n, n)
    Thread.sleep(3000)
  }

}