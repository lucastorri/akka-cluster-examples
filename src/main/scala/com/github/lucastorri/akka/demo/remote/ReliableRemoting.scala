package com.github.lucastorri.akka.demo.remote

import akka.actor.{Actor, ActorPath, ActorSystem, Props}
import akka.contrib.pattern.ReliableProxy
import com.github.lucastorri.akka.demo.util.Identification
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.util.Random

object ReliableRemoting {

  val systemName = "System"
  val actorName = "endpoint"

  def remote(port: Int, other: Int): ActorSystem = {
    val config = ConfigFactory.parseString(
      s"""
         |akka.actor.provider = "akka.remote.RemoteActorRefProvider"
         |
         |akka.remote.netty.tcp.port = $port
         |akka.remote.netty.tcp.hostname = 127.0.0.1
       """.stripMargin)

    val system = ActorSystem(systemName, config)

    system.actorOf(Props(new ProxyParent(other)), actorName)

    system
  }

  class ProxyParent(other: Int) extends Actor with Identification {

    import context._

    val otherPath = ActorPath.fromString(s"akka.tcp://$systemName@127.0.0.1:$other/user/$actorName")
    val proxy = actorOf(ReliableProxy.props(otherPath, 100.millis))
    val pingInterval = 10.seconds

    override def preStart(): Unit = {
      println(s"ProxyParent $id starting")
      system.scheduler.schedule(Random.nextInt(5).seconds, pingInterval) {
        println(s"ProxyParent $id pinging")
        proxy ! Ping
      }
    }

    def receive = {
      case Ping =>
        println(s"ProxyParent $id got ping")
        sender ! Pong
      case Pong =>
        println(s"ProxyParent $id got pong")
      case msg =>
        println(s"ProxyParent $id got $msg")
    }

  }

  case object Ping
  case object Pong

}

object ReliableRemotingMain extends App {

  ReliableRemoting.remote(7771, 7772)
  ReliableRemoting.remote(7772, 7771)

}