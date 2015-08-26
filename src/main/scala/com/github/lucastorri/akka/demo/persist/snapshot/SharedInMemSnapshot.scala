package com.github.lucastorri.akka.demo.persist.snapshot

import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SharedInMemSnapshot extends SnapshotStore {

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = Future {
    SharedInMemSnapshot(persistenceId)
      .slice(criteria)
      .toSeq
      .sortBy(- _.number)
      .headOption
      .map(_.toSelected)
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = Future {
    SharedInMemSnapshot(metadata.persistenceId).save(metadata, snapshot)
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    deleteAsync(metadata.persistenceId,
      SnapshotSelectionCriteria(metadata.sequenceNr, metadata.timestamp, metadata.sequenceNr, metadata.timestamp))
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = Future {
    SharedInMemSnapshot(persistenceId).delete(criteria)
  }

}

object SharedInMemSnapshot {

  private val stores = mutable.HashMap.empty[String, Store]

  private[SharedInMemSnapshot] def apply(persistenceId: String): Store = {
    stores.getOrElseUpdate(persistenceId, new Store)
  }

  class Store {

    private var snapshots = mutable.ListBuffer.empty[Snapshot]

    def save(metadata: SnapshotMetadata, data: Any): Unit = synchronized {
      snapshots += Snapshot(data, metadata)
    }

    def slice(criteria: SnapshotSelectionCriteria): Stream[Snapshot] = {
      snapshots.toStream.filter(snapshotCriteria(criteria))
    }

    def delete(criteria: SnapshotSelectionCriteria): Unit = synchronized {
      snapshots = snapshots.filterNot(snapshotCriteria(criteria))
    }

    def snapshotCriteria(criteria: SnapshotSelectionCriteria)(s: Snapshot): Boolean =
      s.number >= criteria.minSequenceNr && s.number <= criteria.maxSequenceNr &&
        s.timestamp >= criteria.minTimestamp && s.timestamp <= criteria.maxTimestamp

  }

  case class Snapshot(data: Any, metadata: SnapshotMetadata) {
    def number: Long = metadata.sequenceNr
    def timestamp: Long = metadata.timestamp

    def toSelected: SelectedSnapshot = SelectedSnapshot(metadata, data)
  }

}