package com.michaelsteffen.osm.parentrefs

import scala.collection._
import com.michaelsteffen.osm.osmdata._

object RefUtils {
  val ADD = 0
  val DELETE = 1

  // iterator->iterator transformation. see _High Performance Spark_ at p. 98
  def generateRefChanges(id: Long, objHistory: Iterator[ObjectVersion]): Iterator[RefChange] = {
    val pairedObjHistory = (Iterator(None) ++ objHistory.map(Some(_))).sliding(2)
    pairedObjHistory.flatMap(p => {
      val lastVersion = p.head
      val thisVersion = p(1).get
      val children = thisVersion.children.toSet
      val lastVersionChildren = lastVersion match {
        case Some(v) => v.children.toSet
        case None => Set.empty[MemberRef]
      }
      val additions = children
        .diff(lastVersionChildren)
        .map(ref => RefChange(
          childID = OSMDataUtils.createID(ref.ref, ref.`type`),
          parentID = id,
          changeset = thisVersion.changeset.toLong,
          timestamp = thisVersion.timestamp,
          changeType = RefUtils.ADD
        ))
      val deletions = lastVersionChildren
        .diff(children)
        .map(ref => RefChange(
          childID = OSMDataUtils.createID(ref.ref, ref.`type`),
          parentID = id,
          changeset = thisVersion.changeset.toLong,
          timestamp = thisVersion.timestamp,
          changeType = RefUtils.DELETE
        ))

      additions ++ deletions
    })
  }

  def generateRefTree(childID: Long, refChangeHistory: Iterator[RefChange], geometryHistory: Iterator[GeometryStatus]): Iterator[RefHistory]= {
    // TODO: preserve changeset info for ref changes so we can include it in the augmented history
    if (refChangeHistory == null || refChangeHistory.isEmpty) {
      ---
      // TODO: I think the statement below is false -- what about a way or relation with no parents?
      // if there are no parent references, we can safely ignore the geometryHistory and drop the object
      Iterator.empty
    } else {
      // TODO: which direction does this end up sorted?
      val sortedRefChangeHistory = refChangeHistory
        .toList
        .sortBy(_.timestamp.getTime)
        .toIterator
        .buffered

      val sortedGeometryHistory = geometryHistory
        .toList
        .sortBy(_.timestamp.getTime)
        .toIterator
        .buffered

      var lastVersionRefs = Set.empty[Long]
      var lastVersionGeometry = false
      var history = mutable.ListBuffer.empty[RefVersion]
      while (sortedRefChangeHistory.nonEmpty || sortedGeometryHistory.nonEmpty) {
        --
        // TODO: this logic is broken because we advance both iterators by one, even if we're only using one of them.
        val thisRefChange = if (sortedRefChangeHistory.hasNext) Some(sortedRefChangeHistory.head) else None
        val thisGeometryStatus =  if (sortedGeometryHistory.hasNext) Some(sortedGeometryHistory.head) else None

        val thisVersionTimestamp = (thisRefChange, thisGeometryStatus) match {
          case (None, Some(g)) => g.timestamp.getTime
          case (Some(r), None) => r.timestamp.getTime
          case (Some(r), Some(g)) => Math.min(r.timestamp.getTime, g.timestamp.getTime)
        }

        val thisVersionRefChanges = sortedRefChangeHistory.takeWhile(_.timestamp.getTime < thisVersionTimestamp)
        val thisVersionRefs = thisVersionRefChanges.foldLeft(lastVersionRefs)(applyParentRefChange)

        val thisVersionGeometries = sortedGeometryHistory.takeWhile(_.timestamp.getTime < thisVersionTimestamp)
        val thisVersionGeometry =
          if (thisVersionGeometries.nonEmpty) thisVersionGeometries.next.hasGeometry
          else lastVersionGeometry

        history += RefVersion(new java.sql.Timestamp(thisVersionTimestamp), thisVersionGeometry, thisVersionRefs.to[Array])

        lastVersionRefs = thisVersionRefs
        lastVersionGeometry = thisVersionGeometry
      }

      // wrap result in an iterator for spark's cogroup function
      Iterator(RefHistory(childID, history.to[Array]))
    }
  }

  private def applyParentRefChange(parentRefs: Set[Long], parentChange: RefChange): Set[Long] = {
    parentChange.changeType match {
      case RefUtils.ADD => parentRefs + parentChange.parentID
      case RefUtils.DELETE => parentRefs - parentChange.parentID
    }
  }
}