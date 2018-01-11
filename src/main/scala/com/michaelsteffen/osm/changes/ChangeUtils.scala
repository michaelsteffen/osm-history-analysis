package com.michaelsteffen.osm.changes

import scala.collection._
import scala.annotation.tailrec
import com.michaelsteffen.osm.osmdata._
import com.michaelsteffen.osm.parentrefs._

object ChangeUtils {
  val FEATURE_CREATE = 0
  val FEATURE_DELETE = 1
  val TAG_ADD = 2
  val TAG_DELETE = 3
  val TAG_CHANGE = 4
  val NODE_MOVE = 5
  val NODE_ADD = 6
  val NODE_REMOVE = 7
  val MEMBER_ADD = 8
  val MEMBER_REMOVE = 9

  // TODO: rewrite this as an iterator->iterator function?
  def generateFirstOrderChanges(id: Long, objHistory: Iterator[ObjectVersion]): ChangeResults = {
    var changeResultsBuffer = ChangeResults.empty
    var priorVersion = ObjectVersion.empty

    for (objVersion <- objHistory) {
      if (
        (!priorVersion.isFeature || !priorVersion.visible) &&
        (objVersion.isFeature && objVersion.visible)
      ) {
        changeResultsBuffer = changeResultsBuffer ++
          featureCreation(id, objVersion)
      } else if (
        (priorVersion.isFeature && priorVersion.visible) &&
        (!objVersion.isFeature || !objVersion.visible)
      ) {
        changeResultsBuffer = changeResultsBuffer ++
          featureDeletion(id, priorVersion)
      } else {
        changeResultsBuffer = changeResultsBuffer ++
          tagAdditions(id, objVersion, priorVersion) ++
          tagDeletions(id, objVersion, priorVersion) ++
          tagChanges(id, objVersion, priorVersion) ++
          nodeMoves(id, objVersion, priorVersion) ++
          nodeAndMemberAdditions(id, objVersion, priorVersion) ++
          nodeAndMemberRemovals(id, objVersion, priorVersion)
        // if not a feature, keep only the results to propagate up
        if (!objVersion.isFeature)
          changeResultsBuffer = changeResultsBuffer.copy(changesToSave = Iterator.empty[Change])
      }
      priorVersion = objVersion
    }

    changeResultsBuffer
  }

  def generateSecondOrderChanges(history: RefHistory, changeGroup: ChangeGroupToPropagate): ChangeResults = {
    @tailrec
    def generateRecursively(history: RefHistory, changeGroup: ChangeGroupToPropagate, accumulator: ChangeResults): ChangeResults = {
      if (changeGroup.changes.isEmpty) {
        accumulator
      } else if (history.versions.length == 1) {
        val thisVersion = history.versions.head
        val thisVersionChanges = changeGroup.changes.map(_.copy(featureID = history.id))
        val changeResults = ChangeResults(
          changesToSave = if (thisVersion.hasGeometry) thisVersionChanges else Iterator.empty[Change],
          changesToPropagate = thisVersionChanges.flatMap(c => thisVersion.parents.map(p => ChangeToPropagate(p, c)))
        )
        accumulator ++ changeResults
      } else {
        val thisVersion = history.versions.head
        val nextVersion = history.versions(1)

        val thisVersionChanges = changeGroup.changes
          .takeWhile(_.timestamp.getTime < nextVersion.timestamp.getTime)
          .map(_.copy(featureID = history.id))
        val changeResults = ChangeResults(
          changesToSave = if (thisVersion.hasGeometry) thisVersionChanges else Iterator.empty[Change],
          changesToPropagate = thisVersionChanges.flatMap(c => thisVersion.parents.map(p => ChangeToPropagate(p, c)))
        )

        generateRecursively(
          history.copy(versions = history.versions.tail),
          changeGroup.copy(changes = changeGroup.changes.drop(thisVersionChanges.length)),
          accumulator ++ changeResults
        )
      }
    }

    generateRecursively(history, changeGroup, ChangeResults.empty)
  }

  def coalesceChanges(changes: Iterator[Change]): List[Change] = {
    changes.foldLeft(Map.empty[Int, Change])((map, c) => {
      val hash = (c.changeset, c.changeType).hashCode
      map.get(hash) match {
        case None => map + (hash -> c)
        case Some(x) => map + (hash -> x.copy(
          count = x.count + c.count,
          bbox = c.bbox.map(e => x.bbox.map(e.union).orElse(Some(e)).get).orElse(x.bbox),
          timestamp = new java.sql.Timestamp(math.max(x.timestamp.getTime, c.timestamp.getTime))))
      }
    }).values.toList
  }

  private def featureCreation(id: Long, objVersion: ObjectVersion): ChangeResults = {
    ChangeResults(
      changesToSave = Iterator(Change.nonTagChange(id, FEATURE_CREATE, 1, objVersion)),
      changesToPropagate = Iterator.empty[ChangeToPropagate]
    )
  }

  private def featureDeletion(id: Long, objVersion: ObjectVersion): ChangeResults = {
    ChangeResults(
      changesToSave = Iterator(Change.nonTagChange(id, FEATURE_DELETE, 1, objVersion)),
      changesToPropagate = Iterator.empty[ChangeToPropagate]
    )
  }

  private def tagAdditions(id: Long, objVersion: ObjectVersion, priorVersion: ObjectVersion): ChangeResults = {
    val newKeys = objVersion.tags.keys.toSet.diff(priorVersion.tags.keys.toSet)
    if (newKeys.nonEmpty) ChangeResults(
      changesToSave = Iterator(Change.tagChange(id, TAG_ADD, newKeys.size, priorVersion, objVersion, objVersion.tags.filterKeys(newKeys))),
      changesToPropagate = Iterator.empty[ChangeToPropagate]
    ) else ChangeResults.empty
  }

  private def tagDeletions(id: Long, objVersion: ObjectVersion, priorVersion: ObjectVersion): ChangeResults = {
    val deletedKeys = priorVersion.tags.keys.toSet.diff(objVersion.tags.keys.toSet)
    if (deletedKeys.nonEmpty) ChangeResults(
      changesToSave = Iterator(Change.tagChange(id, TAG_DELETE, deletedKeys.size, priorVersion, objVersion, priorVersion.tags.filterKeys(deletedKeys))),
      changesToPropagate = Iterator.empty[ChangeToPropagate]
    ) else ChangeResults.empty
  }

  private def tagChanges(id: Long, objVersion: ObjectVersion, priorVersion: ObjectVersion): ChangeResults = {
    val sharedKeys = objVersion.tags.keySet.intersect(priorVersion.tags.keySet)
    val keysWithChanges = sharedKeys.filter(key => objVersion.tags(key) != priorVersion.tags(key))
    if (keysWithChanges.nonEmpty) ChangeResults(
      changesToSave = Iterator(Change.tagChange(id, TAG_CHANGE, keysWithChanges.size, priorVersion, objVersion, objVersion.tags.filterKeys(keysWithChanges))),
      changesToPropagate = Iterator.empty[ChangeToPropagate]
    ) else ChangeResults.empty
  }

  private def nodeMoves(id: Long, objVersion: ObjectVersion, priorVersion: ObjectVersion): ChangeResults = {
    if (id(0) == 'n' && (objVersion.lat, objVersion.lon) != (priorVersion.lat, priorVersion.lon)) {
      val change = Change.nonTagChange(id, NODE_MOVE, 1, objVersion)
      ChangeResults(
        changesToSave = Iterator(change),
        changesToPropagate = objVersion.parents.map(ChangeToPropagate(_, change))
      )
    } else {
      ChangeResults.empty
    }
  }

  private def nodeAndMemberAdditions(id: Long, objVersion: ObjectVersion, priorVersion: ObjectVersion): ChangeResults = {
    if (id(0) == 'w' || id(0) == 'r') {
      val newMembersCount = objVersion.children.toSet.diff(priorVersion.children.toSet).size
      val changeType = if (id(0) == 'w') NODE_ADD else MEMBER_ADD
      if (newMembersCount > 0) {
        val change = Change.nonTagChange(id, changeType, newMembersCount, objVersion)
        ChangeResults(
          changesToSave = Iterator(change),
          changesToPropagate = objVersion.parents.map(ChangeToPropagate(_, change))
        )
      } else ChangeResults.empty
    } else ChangeResults.empty
  }

  private def nodeAndMemberRemovals(id: Long, objVersion: ObjectVersion, priorVersion: ObjectVersion): ChangeResults = {
    if (id(0) == 'w' || id(0) == 'r') {
      val removedMembersCount = priorVersion.children.toSet.diff(objVersion.children.toSet).size
      val changeType = if (id(0) == 'w') NODE_REMOVE else MEMBER_REMOVE
      if (removedMembersCount > 0) {
        val change = Change.nonTagChange(id, changeType, removedMembersCount, objVersion)
        ChangeResults(
          changesToSave = List(change),
          changesToPropagate = objVersion.parents.map(ChangeToPropagate(_, change))
        )
      } else ChangeResults.empty
    } else ChangeResults.empty
  }
}
