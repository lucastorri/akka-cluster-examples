package com.github.lucastorri.akka.demo.cluster.patterns

import akka.actor._
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.github.lucastorri.akka.demo.cluster.ClusterSeed
import ClusterSeed
import com.github.lucastorri.akka.demo.util.Identification
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.Random

/** Notes:
  *
  * Shards can used together with persistence or with distributed data mode
  * http://doc.akka.io/docs/akka/2.4.0-RC1/scala/cluster-sharding.html#Distributed_Data_Mode
  *
  */
object Sharding {

  val shardName = "Shard"
  val role = "Shard"
  val nodes = 5

  val idExtractor: ShardRegion.ExtractEntityId = {
    case msg @ Ping(id) => (id.toString, msg)
  }

  val shardResolver: ShardRegion.ExtractShardId = {
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
         |akka.cluster.roles = ["$role"]
         |akka.cluster.seed-nodes = ["akka.tcp://${ClusterSeed.name}@127.0.0.1:${ClusterSeed.port}"]
         |akka.cluster.auto-down-unreachable-after = 10s
         |
         |mem-journal.class = "com.github.lucastorri.akka.demo.persist.journal.SharedInMemJournal"
         |mem-journal.plugin-dispatcher = "akka.actor.default-dispatcher"
         |
         |akka.cluster.sharding.journal-plugin-id = "mem-journal"
       """.stripMargin)

    val system = ActorSystem(ClusterSeed.name, config)

    val settings = ClusterShardingSettings(system).withRole(role)
    ClusterSharding(system).start(shardName, Props[Shard], settings, idExtractor, shardResolver)

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

  class Shard extends Actor with Identification {

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
    val client = ClusterSharding(system).startProxy(shardName, Some(role), idExtractor, shardResolver)

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

  val system = ClusterSeed.start


  (1 to Sharding.nodes).foreach(n => Sharding.shard(7770 + n))

  Sharding.client

}