package com.michaelsteffen.osm.changes

import com.michaelsteffen.osm.geometries
import com.michaelsteffen.osm.geometries.{ChangeGroupToPropagate, ChangeResults, ChangeToPropagate}

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

  def generateFirstOrderGeometryChanges(id: Long, objHistory: Iterator[ObjectVersion]): ChangeResults = {
    var changeResultsBuffer = ChangeResults.empty
    var priorVersion = ObjectVersion.empty

    val sortedObjHistory = objHistory.toList.sortBy(_.timestamp.getTime)
    for (objVersion <- sortedObjHistory) {
      changeResultsBuffer = changeResultsBuffer ++
        nodeMoves(id, objVersion, priorVersion) ++
        nodeAndMemberAdditions(id, objVersion, priorVersion) ++
        nodeAndMemberRemovals(id, objVersion, priorVersion)
      // if not a feature, keep only the results to propagate up
      if (!objVersion.isFeature)
        changeResultsBuffer = changeResultsBuffer.copy(changesToSave = Array.empty[Change])
      priorVersion = objVersion
    }

    changeResultsBuffer
  }

  def generateSecondOrderChanges(history: RefHistory, changeGroup: ChangeGroupToPropagate, depth: Int, propagateOnly: Boolean = false): ChangeResults = {
    @tailrec
    def generateRecursively(history: RefHistory, changeGroup: ChangeGroupToPropagate, accumulator: ChangeResults): ChangeResults = {
      if (changeGroup.changes.isEmpty) {
        accumulator
      } else if (history.versions.length == 1) {
        val thisVersion = history.versions.head
        val thisVersionChanges = changeGroup.changes.map(_.copy(featureID = history.id, depth = depth))
        val changeResults = geometries.ChangeResults(
          changesToSave = if (thisVersion.wayOrRelationHasGeometry && !propagateOnly) thisVersionChanges else Array.empty[Change],
          changesToPropagate = thisVersionChanges.flatMap(c => thisVersion.parents.map(p => ChangeToPropagate(p, c)))
        )
        accumulator ++ changeResults
      } else {
        val thisVersion = history.versions.head
        val nextVersion = history.versions(1)

        val thisVersionChanges = changeGroup.changes
          .takeWhile(_.timestamp.getTime < nextVersion.timestamp.getTime)
          .map(_.copy(featureID = history.id, depth = depth))
        val changeResults = geometries.ChangeResults(
          changesToSave = if (thisVersion.wayOrRelationHasGeometry && !propagateOnly) thisVersionChanges else Array.empty[Change],
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

  // the changes below can propagate, but on the first iteration we just "propagate" them to ourselves
  private def nodeMoves(id: Long, objVersion: ObjectVersion, priorVersion: ObjectVersion): ChangeResults = {
    if (OSMDataUtils.isNode(id) && (objVersion.lat, objVersion.lon) != (priorVersion.lat, priorVersion.lon)) {
      val change = Change.nonTagChange(id, NODE_MOVE, 1, objVersion)
      ChangeResults(
        changesToSave = Array(change),
        changesToPropagate = Array(ChangeToPropagate(id, change))
      )
    } else {
      ChangeResults.empty
    }
  }

  private def nodeAndMemberAdditions(id: Long, objVersion: ObjectVersion, priorVersion: ObjectVersion): ChangeResults = {
    if (OSMDataUtils.isWay(id) || OSMDataUtils.isRelation(id)) {
      val newMembersCount = objVersion.children.toSet.diff(priorVersion.children.toSet).size
      val changeType = if (OSMDataUtils.isWay(id)) NODE_ADD else MEMBER_ADD
      if (newMembersCount > 0) {
        val change = Change.nonTagChange(id, changeType, newMembersCount, objVersion)
        ChangeResults(
          changesToSave = Array(change),
          changesToPropagate = Array(ChangeToPropagate(id, change))
        )
      } else ChangeResults.empty
    } else ChangeResults.empty
  }

  private def nodeAndMemberRemovals(id: Long, objVersion: ObjectVersion, priorVersion: ObjectVersion): ChangeResults = {
    if (OSMDataUtils.isWay(id) || OSMDataUtils.isRelation(id)) {
      val removedMembersCount = priorVersion.children.toSet.diff(objVersion.children.toSet).size
      val changeType = if (OSMDataUtils.isWay(id)) NODE_REMOVE else MEMBER_REMOVE
      if (removedMembersCount > 0) {
        val change = Change.nonTagChange(id, changeType, removedMembersCount, objVersion)
        ChangeResults(
          changesToSave = Array(change),
          changesToPropagate = Array(ChangeToPropagate(id, change))
        )
      } else ChangeResults.empty
    } else ChangeResults.empty
  }
}
