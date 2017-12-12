package com.michaelsteffen.osm.parentrefs

import scala.collection._
import com.michaelsteffen.osm.osmdata._

object RefUtils {
  val ADD = 0
  val DELETE = 1

  def generateRefChangesFromObjectHistory(objHistory: OSMObjectHistory): List[RefChange] = {
    var changesBuffer = new mutable.ListBuffer[RefChange]
    var lastVersionMembers = new mutable.HashSet[Ref]

    for (objVersion <- objHistory.versions) {
      val members = objVersion.children.toSet
      val additions = members
        .diff(lastVersionMembers)
        .map((ref) => RefChange(
          childID = ref.ref,
          parentID = objHistory.id,
          changeset = objVersion.changeset,
          timestamp = objVersion.timestamp,
          changeType = RefUtils.ADD
        ))
      val deletions = lastVersionMembers
        .diff(members)
        .map((ref) => RefChange(
          childID = ref.ref,
          parentID = objHistory.id,
          changeset = objVersion.changeset,
          timestamp = objVersion.timestamp,
          changeType = RefUtils.DELETE
        ))
      changesBuffer ++= (additions ++ deletions)
      // convert immutable (members) to mutable (lastVersionMembers)
      lastVersionMembers = mutable.HashSet(members.toList: _*)
    }

    changesBuffer.toList
  }

  def collectRefChanges(childID: String, refChanges: Iterator[RefChange]): RefChangeGroupToPropagate = RefChangeGroupToPropagate(
    childID = childID,
    changes = refChanges.toList.sortWith(_.timestamp.getTime < _.timestamp.getTime)
  )

  def addParentRefs (objData: (OSMObjectHistory, RefChangeGroupToPropagate)): OSMObjectHistory = {
    if (objData._2 == null || objData._2.changes.isEmpty) {
      objData._1
    } else {
      val objHistory = objData._1
      // we add a None at the end of the obj versions list to allow a lookahead in the for loop below
      val objHistoryIterator = (objHistory.versions.map(Some(_)) ::: List(None)).iterator.sliding(2)
      val parentChangesIterator: Iterator[RefChange] = objData._2.changes.iterator

      var versionsBuffer = mutable.ListBuffer.empty[OSMObjectVersion]
      var parentsBuffer = List.empty[String]
      var parentChange: Option[RefChange] = Some(parentChangesIterator.next)

      for (List(objVersionOption, objNextMajorVersionOption) <- objHistoryIterator) {
        val objVersion = objVersionOption.get   // will always work -- only next objNextMajorVersionOption can be None
        var minorVersionNumber = 0
        var minorVersionChangeset = objVersion.changeset
        var minorVersionTimestamp = objVersion.timestamp

        while (
          parentChange.isDefined &&
          (objNextMajorVersionOption.isEmpty ||
          parentChange.get.timestamp.getTime <= objNextMajorVersionOption.get.timestamp.getTime)
        ) {
          if (parentChange.get.timestamp.getTime <= minorVersionTimestamp.getTime) {
            // apply ref change to the current object version
            // We have to include the < case here, because in certain edge cases from the old API, a node can show up as
            // reference in a way prior to the timestamp for its v1. This may have something to do with "unwayed
            // segments", which is where I've seen it (see http://wiki.openstreetmap.org/wiki/Unwayed_segments). In any
            // event, we just attribute all changes to the first node version.
            // The == case is that these ref changes were part of the same upload as the one that caused us to create
            // this minor version
            parentsBuffer = applyParentRefChange(parentsBuffer, parentChange.get)
          } else {
            // save current object version to versionsBuffer
            versionsBuffer += objVersion.copy(
              parents = parentsBuffer,
              minorVersion = minorVersionNumber,
              changeset = minorVersionChangeset,
              timestamp = minorVersionTimestamp
            )
            // start a new minor object version & apply ref change to the new minor version
            minorVersionNumber += 1
            minorVersionChangeset = parentChange.get.changeset
            minorVersionTimestamp = parentChange.get.timestamp
            parentsBuffer = applyParentRefChange(parentsBuffer, parentChange.get)
          }

          parentChange = if (parentChangesIterator.hasNext) Some(parentChangesIterator.next) else None
        }

        // save final object version from this major-version subset to versionsBuffer
        versionsBuffer += objVersion.copy(
          parents = parentsBuffer,
          minorVersion = minorVersionNumber,
          changeset = minorVersionChangeset,
          timestamp = minorVersionTimestamp
        )
      }

      objHistory.copy(versions = versionsBuffer.toList)
    }
  }

  private def applyParentRefChange(parentRefs: List[String], parentChange: RefChange): List[String] = {
    parentChange.changeType match {
      case RefUtils.ADD => parentChange.parentID :: parentRefs
      case RefUtils.DELETE => parentRefs.filter(_ != parentChange.parentID)
    }
  }
}