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
      val children =
        thisVersion.members.toSet ++
        thisVersion.nds.map(_.toMember).toSet
      val lastVersionChildren =
        lastVersion.flatMap(_.members).toSet ++
        lastVersion.flatMap(_.nds.map(_.toMember)).toSet
      val additions = children
        .diff(lastVersionChildren)
        .map(ref => RefChange(
          childID = OSMDataUtils.createID(ref.ref, ref.`type`),
          parentID = id,
          changeset = thisVersion.changeset,
          timestamp = thisVersion.timestamp,
          changeType = RefUtils.ADD
        ))
      val deletions = lastVersionChildren
        .diff(children)
        .map(ref => RefChange(
          childID = OSMDataUtils.createID(ref.ref, ref.`type`),
          parentID = id,
          changeset = thisVersion.changeset,
          timestamp = thisVersion.timestamp,
          changeType = RefUtils.DELETE
        ))

      additions ++ deletions
    })
  }

  def generateRefHistory(childID: Long, refChangeHistory: Iterator[RefChange]): RefHistory = {
    if (refChangeHistory == null || refChangeHistory.isEmpty) {
      RefHistory(childID, Array.empty[RefVersion])
    } else {
      // TODO: which direction does this end up sorted?
      // TODO: add "hasGeometry" to each version
      val sortedRefChangeHistory = refChangeHistory.toList.sortBy(_.timestamp.getTime).toIterator
      var lastVersionRefs = Set.empty[Long]
      var history = mutable.ListBuffer.empty[RefVersion]
      while (!sortedRefChangeHistory.isEmpty) {
        val thisChange = sortedRefChangeHistory.next()
        val thisVersionChanges = sortedRefChangeHistory.takeWhile(_.timestamp.getTime == thisChange.timestamp.getTime)
        val thisVersionRefs = thisVersionChanges.foldLeft(lastVersionRefs)(applyParentRefChange)
        history += RefVersion(thisChange.timestamp, thisVersionRefs.to[Array])
        lastVersionRefs = thisVersionRefs
      }

      RefHistory(childID, history.to[Array])
    }
  }

  private def applyParentRefChange(parentRefs: Set[Long], parentChange: RefChange): Set[Long] = {
    parentChange.changeType match {
      case RefUtils.ADD => parentRefs + parentChange.parentID
      case RefUtils.DELETE => parentRefs - parentChange.parentID
    }
  }
}