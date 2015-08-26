package com.github.lucastorri.akka.demo.persist.journal

import java.util.concurrent.atomic.AtomicLong

import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.{AtomicWrite, PersistentRepr}

import scala.collection.{immutable, mutable}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Try}

class SharedInMemJournal extends AsyncWriteJournal {

  override def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = Future {
    messages.map { write =>
      val store = SharedInMemJournal(write.persistenceId)
      write.payload.foreach(store.add)
      Success(())
    }
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = Future {
    SharedInMemJournal(persistenceId).removeUpTo(toSequenceNr)
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = Future {
    SharedInMemJournal(persistenceId).lastId
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = Future {
    SharedInMemJournal(persistenceId).slice(fromSequenceNr, toSequenceNr, max).foreach(replayCallback)
  }

}

object SharedInMemJournal {

  private val stores = mutable.HashMap.empty[String, Store]

  private[SharedInMemJournal] def apply(persistenceId: String): Store = {
    stores.getOrElseUpdate(persistenceId, new Store)
  }

  class Store {

    private val counter = new AtomicLong(1)
    private var store = mutable.ListBuffer.empty[Entry]

    def add(repr: PersistentRepr): Unit = synchronized {
      store += Entry(counter.getAndIncrement(), repr)
    }

    def removeUpTo(id: Long): Unit = synchronized {
      store = store.dropWhile(_.id <= id)
    }

    def lastId: Long = {
      if (store.isEmpty) -1 else store.last.id
    }

    def slice(fromId: Long, toId: Long, max: Long): Stream[PersistentRepr] = {
      val m = if (max > Int.MaxValue) Int.MaxValue else max.toInt
      store.toStream.dropWhile(_.id < fromId).takeWhile(_.id <= toId).take(m).map(_.value)
    }

  }

  private case class Entry(id: Long, value: PersistentRepr)

}