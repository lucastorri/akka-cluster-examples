package com.github.lucastorri.akka.demo.persist

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.persistence.{RecoveryCompleted, SnapshotOffer, PersistentActor}
import com.github.lucastorri.akka.demo.persist.Persistent.{Message, Snapshot, Throw}
import com.typesafe.config.ConfigFactory

object Persistent {

  def start: (ActorRef, ActorSystem) = {
    val config = ConfigFactory.parseString(
      """
        |mem-journal.class = "com.github.lucastorri.akka.demo.persist.journal.SharedInMemJournal"
        |mem-journal.plugin-dispatcher = "akka.actor.default-dispatcher"
        |
        |mem-snapshot.class = "com.github.lucastorri.akka.demo.persist.snapshot.SharedInMemSnapshot"
        |mem-snapshot.plugin-dispatcher = "akka.actor.default-dispatcher"
        |
        |akka.persistence.journal.plugin = "mem-journal"
        |akka.persistence.snapshot-store.plugin = "mem-snapshot"
      """.stripMargin)

    val system = ActorSystem("sys", config)

    val actor = system.actorOf(Props[Persistent])

    actor -> system
  }

  class Persistent extends PersistentActor {

    var state = State()

    override val persistenceId: String = "persistence-id"

    override def receiveRecover: Receive = {
      case msg: Message =>
        if (state.contains(msg)) {
          println(s"recover already has $msg")
        } else {
          state += msg
          println(s"recover added $msg $state")
        }
      case SnapshotOffer(meta, s: State) =>
        state = s
        println(s"recover from snapshot $state")
      case RecoveryCompleted =>
        println(s"recover completed $state")
      case msg =>
        println(s"recover $msg")
    }

    override def receiveCommand: Receive = {
      case msg: Message =>
        persist(msg)(persisted)
        state += msg
        println(s"command $msg => $state")
      case Snapshot =>
        println("snapshot")
        saveSnapshot(state)
      case Throw =>
        println("throw")
        throw new Exception
      case other =>
        println(s"other $other")
    }

    def persisted(msg: Message): Unit =
      println(s"persisted $msg")

  }

  case object Throw
  case object Snapshot
  case class Message(id: Int)
  case class State(messages: Set[Message] = Set.empty) {
    def +(msg: Message): State = State(messages + msg)
    def contains(msg: Message): Boolean = messages.contains(msg)
  }

}

object PersistentMain extends App {

  def run(f: ActorRef => Unit): Unit = {
    val (actor, sys) = Persistent.start
    f(actor)
    Thread.sleep(1000)
    sys.terminate()
    println("---")
  }

  run { actor =>
    actor ! Message(1)
    actor ! Snapshot
    actor ! Message(2)
    actor ! Snapshot
    actor ! Throw
  }

  run { actor =>
    actor ! Message(3)
    actor ! Message(4)
    actor ! Throw
  }

}