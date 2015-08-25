package com.github.lucastorri.akka.cluster.examples

import akka.actor.{Actor, ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.ClusterDomainEvent
import com.typesafe.config.ConfigFactory

/** Notes:
  *
  * http://doc.akka.io/docs/akka/2.3.12/scala/cluster-usage.html#Cluster_Dispatcher
  *
  *
  * How to avoid a fixed entry point?
  *   - Dynamically configure seed: when a node started, it adds itself to a set in redis, and uses that
  *   as the list of available nodes (Cluster(system).joinSeedNodes(addresses))
  *
  *
  * After peers are connected, the entry point node can even be shut down.
  */
object ClusterSeed {

  val port = 8888
  val name = "ClusterSystem"

  def start: ActorSystem = {
    val config = ConfigFactory.parseString(
      s"""
        |akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
        |
        |akka.remote.netty.tcp.port = $port
        |akka.remote.netty.tcp.hostname = 127.0.0.1
        |
        |akka.cluster.seed-nodes = ["akka.tcp://$name@127.0.0.1:$port"]
        |akka.cluster.auto-down-unreachable-after = 10s
      """.stripMargin)

    val system = ActorSystem(name, config)

    system
  }

}
