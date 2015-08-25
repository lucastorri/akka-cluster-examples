package com.github.lucastorri.akka.cluster.examples.patterns

import akka.actor._
import akka.contrib.pattern.{ClusterSingletonManager, ClusterSingletonProxy}
import com.github.lucastorri.akka.cluster.examples.ClusterSeed
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.Random

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
         |akka.cluster.seed-nodes = ["akka.tcp://${ClusterSeed.name}@127.0.0.1:${ClusterSeed.port}"]
         |akka.cluster.roles = [$role]
         |akka.cluster.auto-down-unreachable-after = 10s
       """.stripMargin)
    val system = ActorSystem(ClusterSeed.name, config)

    val manager = ClusterSingletonManager.props(Props[Singleton], singletonName, PoisonPill, None)
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
         |akka.cluster.roles = [$role]
         |akka.cluster.auto-down-unreachable-after = 10s
       """.stripMargin)
    val system = ActorSystem(ClusterSeed.name, config)

    system.actorOf(Props[Ping])

    system
  }

  class Singleton extends Actor {

    import context._
    val id = Random.alphanumeric.take(8).mkString

    println(s"new singleton $id in $system")
    system.scheduler.scheduleOnce(10.seconds, self, 'goodbye)

    override def receive: Receive = {
      case 'goodbye =>
        println(s"Singleton $id going down")
        system.shutdown()
      case msg =>
        println(s"Singleton $id $msg")
    }

  }

  class Ping extends Actor {

    import context._

    val path = s"/user/$managerName/$singletonName"
    val singleton = context.actorOf(ClusterSingletonProxy.props(path, Some(role)))
    val pingInterval = 5.seconds

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