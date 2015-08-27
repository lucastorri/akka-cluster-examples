package com.github.lucastorri.akka.demo.cluster.patterns

import akka.actor.{Props, ActorSystem, ActorRef, Actor}
import akka.actor.Actor.Receive
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{ORSet, ORSetKey, DistributedData}
import com.github.lucastorri.akka.demo.cluster.ClusterSeed
import com.github.lucastorri.akka.demo.cluster.patterns.CRDT.{Remove, Add}
import com.typesafe.config.ConfigFactory

import scala.util.Random

/** Notes:
  *
  * http://doc.akka.io/docs/akka/snapshot/scala/distributed-data.html
  */
object CRDT {

  def start(port: Int): (ActorRef, ActorSystem) = {
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

    val actor = system.actorOf(Props[Data])

    actor -> system
  }

  class Data extends Actor {

    implicit val node = Cluster(context.system)
    val replicator = DistributedData(context.system).replicator
    val Key = ORSetKey[Int]("key-name")

    override def preStart(): Unit = {
      replicator ! Subscribe(Key, self)
    }

    override def receive: Receive = {
      case c @ Changed(Key) =>
        println(s"changes ${c.get(Key).elements}")
      case Add(n) =>
        replicator ! Update(Key, ORSet.empty[Int], WriteLocal)(_ + n)
      case Remove(n) =>
        replicator ! Update(Key, ORSet.empty[Int], WriteLocal)(_ - n)
      case msg =>
        println(s"other $msg")
    }

  }

  case class Add(n: Int)
  case class Remove(n: Int)
}

object CRDTMain extends App {

  ClusterSeed.start

  val actors = (1 to 5).map(n => CRDT.start(6660 + n)._1)

  def random: ActorRef =
    actors(Random.nextInt(actors.size))

  random ! Add(3)
  random ! Add(2)
  Thread.sleep(5000)
  random ! Remove(3)
  random ! Add(5)

}