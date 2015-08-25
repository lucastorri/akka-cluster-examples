package com.github.lucastorri.akka.cluster.examples.patterns

import akka.actor._
import akka.contrib.pattern.{ClusterSharding, ShardRegion}
import com.github.lucastorri.akka.cluster.examples.ClusterSeed
import com.github.lucastorri.akka.cluster.examples.traits.Identified
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.Random

object Sharding {

  val shardName = "Shard"
  val role = "Shard"
  val nodes = 5

  val idExtractor: ShardRegion.IdExtractor = {
    case msg @ Ping(id) => (id.toString, msg)
  }

  val shardResolver: ShardRegion.ShardResolver = {
    case Ping(id) => (id % (nodes * 10)).toString
  }

  def shard(port: Int): ActorSystem = {
    val config = ConfigFactory.parseString(
      s"""
         |akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
         |
         |akka.remote.netty.tcp.port = $port
         |akka.remote.netty.tcp.hostname = 127.0.0.1
         |
         |akka.cluster.seed-nodes = ["akka.tcp://${ClusterSeed.name}@127.0.0.1:${ClusterSeed.port}"]
         |akka.cluster.auto-down-unreachable-after = 10s
         |
         |akka.contrib.cluster.sharding.role = "$role"
       """.stripMargin)

    val system = ActorSystem(ClusterSeed.name, config)

    ClusterSharding(system).start(shardName, Some(Props[Shard]), idExtractor, shardResolver)

    system
  }

  def client: ActorSystem = {
    val config = ConfigFactory.parseString(
      s"""
         |akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
         |
         |akka.remote.netty.tcp.port = 6660
         |akka.remote.netty.tcp.hostname = 127.0.0.1
         |
         |akka.cluster.seed-nodes = ["akka.tcp://${ClusterSeed.name}@127.0.0.1:${ClusterSeed.port}"]
         |akka.cluster.auto-down-unreachable-after = 10s
       """.stripMargin)

    val system = ActorSystem(ClusterSeed.name, config)

    system.actorOf(Props[Client])

    system
  }

  class Shard extends Actor with Identified {

    override def receive: Receive = {
      case Ping(n) =>
        println(s"Shard $id ping $n")
        sender() ! Pong(id)
    }

  }

  case class Ping(to: Long)
  case class Pong(from: String)

  class Client extends Actor {

    import context._

    val pingInterval = 10.seconds
    val client = ClusterSharding(system).start(shardName, Some(Props[Shard]), idExtractor, shardResolver)

    override def preStart(): Unit =
      system.scheduler.schedule(pingInterval, pingInterval, self, 'ping)

    override def receive: Receive = {
      case 'ping =>
        val id = Random.nextLong()
        println(s"Client ping $id")
        client ! Ping(id)
      case Pong(from) =>
        println(s"Client pong from $from")
    }
  }

}

object ShardingMain extends App {

  ClusterSeed.start

  (1 to Sharding.nodes).foreach(n => Sharding.shard(7770 + n))

  Sharding.client

}