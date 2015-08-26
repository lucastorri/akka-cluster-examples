package com.github.lucastorri.akka.cluster.examples.traits

import akka.actor.{Actor, ActorSystem, Props}
import akka.persistence.Persistence
import akka.persistence.inmem.journal.{SharedInMemoryJournal, SharedInMemoryMessageStore}

trait SharedInMemJournal { self: Actor =>

}

object SharedInMemJournal {

  private[SharedInMemJournal] val system = ActorSystem()

  private[SharedInMemJournal] val store =
    system.actorOf(Props[SharedInMemoryMessageStore], "journalStore")

  Persistence(system)
  SharedInMemoryJournal.setStore(store, system)

}