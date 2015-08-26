package com.github.lucastorri.akka.cluster.examples.patterns

import akka.actor._
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.github.lucastorri.akka.cluster.examples.ClusterSeed
import com.github.lucastorri.akka.cluster.examples.traits.Identification
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object Singleton {

  val role = "test-role"
  val singletonName = "activer"
  val managerName = "master"

  def start(port: Int): ActorSystem = {
    val config = ConfigFactory.parseString(
      s"""
         |akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
         |
         |akka.remote.netty.tcp.port = $port
         |akka.remote.netty.tcp.hostname = 127.0.0.1
         |
         |akka.cluster.roles = ["$role"]
         |akka.cluster.seed-nodes = ["akka.tcp://${ClusterSeed.name}@127.0.0.1:${ClusterSeed.port}"]
         |akka.cluster.auto-down-unreachable-after = 10s
       """.stripMargin)
    val system = ActorSystem(ClusterSeed.name, config)

    val settings = ClusterSingletonManagerSettings(system).withRole(role)
    val manager = ClusterSingletonManager.props(Props[Singleton], PoisonPill, settings)
    system.actorOf(manager, managerName)

    system
  }

  def ping: ActorSystem = {
    val config = ConfigFactory.parseString(
      s"""
         |akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
         |
         |akka.remote.netty.tcp.port = 0
         |akka.remote.netty.tcp.hostname = 127.0.0.1
         |
         |akka.cluster.seed-nodes = ["akka.tcp://${ClusterSeed.name}@127.0.0.1:${ClusterSeed.port}"]
         |akka.cluster.auto-down-unreachable-after = 10s
       """.stripMargin)
    val system = ActorSystem(ClusterSeed.name, config)

    system.actorOf(Props[Ping])

    system
  }

  class Singleton extends Actor with Identification {

    import context._

    override def preStart(): Unit = {
      println(s"new singleton $id in $system")
      system.scheduler.scheduleOnce(10.seconds, self, 'goodbye)
    }

    override def receive: Receive = {
      case 'goodbye =>
        println(s"Singleton $id going down")
        system.terminate()
      case msg =>
        println(s"Singleton $id $msg")
    }

  }

  class Ping extends Actor {

    import context._

    val path = s"/user/$managerName"
    val settings = ClusterSingletonProxySettings(system).withRole(role)
    val singleton = context.actorOf(ClusterSingletonProxy.props(path, settings))
    val pingInterval = 5.seconds

    override def preStart(): Unit =
      system.scheduler.schedule(pingInterval, pingInterval) {
        println("Locate ping")
        singleton ! 'hi
      }

    override def receive: Receive = {
      case msg => println(s"Locate $msg")
    }

  }

}

object SingletonMain extends App {

  ClusterSeed.start

  (1 to 5).foreach(n => Singleton.start(5550 + n))

  Singleton.ping

}