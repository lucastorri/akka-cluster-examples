package com.github.lucastorri.akka.demo.persist.snapshot

import akka.persistence.snapshot.SnapshotStore
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}

import scala.concurrent.Future

class InMemSnapshot extends SnapshotStore {
  //TODO

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = ???

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = ???

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = ???

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = ???

}
