package com.michaelsteffen.osm.geometries

import scala.collection._
import scala.annotation.tailrec
import com.michaelsteffen.osm.osmdata._
import com.michaelsteffen.osm.parentrefs._

object GeometryUtils {
  def generateKeyNodeLocations(id: Long, nodeHistory: ObjectHistory, refHistory: RefHistory): List[NodeLocationToPropagate] = {
    if (refHistory == null || refHistory.versions.isEmpty) {
      // if a node has no parents at any point in time, we can safely ignore it
      List.empty[NodeLocation]
    } else {
      val refVersions = refHistory.versions.toIterator.buffered
      val nodeVersions = nodeHistory.versions.toIterator.buffered

      var lastVersionRefs = Set.empty[Long]
      var locationsBuffer = mutable.ListBuffer.empty[NodeLocation]
      // step through parent reference versions and node geometry versions, capturing locations whenever there is either:
      //   - a new parent reference added (in which case the new parent needs to know the node's location at that moment in time); or
      //   - a change in the node's location (in which case all the parents need to know about the move)
      while (refVersions.nonEmpty || nodeVersions.nonEmpty) {
        --
        // TODO: this logic is broken because we advance both iterators by one, even if we're only using one of them.
        val nextRefVersion = if (refVersions.hasNext) Some(refVersions.head) else None
        val nextNodeVersion =  if (nodeVersions.hasNext) Some(nodeVersions.head) else None

        val timestamp = (nextRefVersion, nextNodeVersion) match {
          case (None, Some(n)) => n.timestamp.getTime
          case (Some(r), None) => r.timestamp.getTime
          case (Some(r), Some(n)) => Math.min(r.timestamp.getTime, n.timestamp.getTime)
        }

        // TODO: add this
        val location =

        // TODO: should be at most 1 in each category, right? logic should account for this
        val thisVersionMoves = nodeMoves(nodeVersions.takeWhile(_.timestamp.getTime < thisVersionTimestamp), location, nextLocation)
        val thisVersionParentAdditions = parentAdditions(nodeVersions.takeWhile(_.timestamp.getTime < thisVersionTimestamp), lastVersionRefs , nextLocation)

        locationsBuffer += (thisVersionMoves ++ thisVersionParentAdditions)

        lastVersionRefs = thisVersionRefs
        lastLocation = location
      }

      locationsBuffer.toList
    }
  }

  def generateSecondOrderChanges(history: RefHistory, changeGroup: ChangeGroupToPropagate, depth: Int, propagateOnly: Boolean = false): ChangeResults = {
    @tailrec
    def generateRecursively(history: RefHistory, changeGroup: ChangeGroupToPropagate, accumulator: ChangeResults): ChangeResults = {
      if (changeGroup.changes.isEmpty) {
        accumulator
      } else if (history.versions.length == 1) {
        val thisVersion = history.versions.head
        val thisVersionChanges = changeGroup.changes.map(_.copy(featureID = history.id, depth = depth))
        val changeResults = ChangeResults(
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
        val changeResults = ChangeResults(
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

  def collectGeometries(changes: Iterator[Change]): List[Change] = {
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
