package com.michaelsteffen.osm.changes

import scala.collection._
import scala.annotation.tailrec
import com.michaelsteffen.osm.osmdata._

object ChangeUtils {
  val FEATURE_CREATE = 0
  val FEATURE_DELETE = 1
  val FEATURE_CHANGE_TYPE = 2
  val TAG_ADD = 3
  val TAG_DELETE = 4
  val TAG_MODIFY = 5
  val NODE_MOVE = 6
  val NODE_ADD = 7
  val NODE_REMOVE = 8
  val MEMBER_ADD = 9
  val MEMBER_REMOVE = 10

  def typeToString(changeType: Int): String = changeType match {
    case FEATURE_CREATE => "feature creation"
    case FEATURE_DELETE => "feature deletion"
    // TODO: FEATURE_CHANGE_TYPE is currently unused
    case FEATURE_CHANGE_TYPE => "feature type change"
    case TAG_ADD => "tag addition"
    case TAG_DELETE => "tag deletion"
    case TAG_MODIFY => "tag value modification"
    case NODE_MOVE => "node move"
    case NODE_ADD => "node addition"
    case NODE_REMOVE => "node removal"
    case MEMBER_ADD => "member addition"
    case MEMBER_REMOVE => "member removal"
    case _ => "unknown change type"
  }

  def generateFirstOrderChanges(objHistory: OSMObjectHistory): ChangeResults = {
    var changeResultsBuffer = ChangeResults.empty
    var priorVersion = OSMObjectVersion.empty
    val id = objHistory.id

    for (objVersion <- objHistory.versions) {
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
        // if not a primary feature, keep only the results to propagate up
        if (objVersion.isFeature)
          changeResultsBuffer = changeResultsBuffer.copy(changesToSave = List.empty[Change])
      }
      priorVersion = objVersion
    }

    changeResultsBuffer
  }

  def collectChangesToPropagate(parentID: String, changes: Iterator[ChangeToPropagate]): ChangeGroupToPropagate =
    ChangeGroupToPropagate(
      parentID = parentID,
      changes = changes
        .toList
        .sortWith(_.change.timestamp.getTime < _.change.timestamp.getTime)
        .map(_.change)
    )

  def generateSecondOrderChanges(history: OSMObjectHistory, changeGroup: ChangeGroupToPropagate): ChangeResults = {
    @tailrec
    def generateRecursively(history: OSMObjectHistory, changeGroup: ChangeGroupToPropagate, accumulator: ChangeResults): ChangeResults = {
      if (changeGroup.changes.isEmpty) {
        accumulator
      } else if (history.versions.length == 1) {
        val thisVersion = history.versions.head
        val thisVersionChanges = changeGroup.changes.map(c => Change(history.id, thisVersion, c.changeType, c.count))
        val changeResults = ChangeResults(
          changesToSave =
            if (OSMDataUtils.hasGeometry(history.objType, thisVersion)) thisVersionChanges
            else List.empty[Change],
          changesToPropagate =
            thisVersion.parents.flatMap(p => thisVersionChanges.map(c => ChangeToPropagate(p, c)))
        )
        accumulator ++ changeResults
      } else {
        val thisVersion = history.versions.head
        val nextVersion = history.versions(1)

        val thisVersionChanges = changeGroup.changes
          .takeWhile(_.timestamp.getTime < nextVersion.timestamp.getTime)
          .map(c => Change(history.id, thisVersion, c.changeType, c.count))
        val changeResults = ChangeResults(
          changesToSave =
            if (OSMDataUtils.hasGeometry(history.objType, thisVersion)) thisVersionChanges
            else List.empty[Change],
          changesToPropagate =
            thisVersion.parents.flatMap(p => thisVersionChanges.map(c => ChangeToPropagate(p, c)))
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
      val hash = (c.primaryFeatureVersion, c.changeset, c.changeType).hashCode
      map.get(hash) match {
        case None => map + (hash -> c)
        case Some(x) => map + (hash -> x.copy(
          count = x.count + c.count,
          bbox = c.bbox.map(e => x.bbox.map(e.union).orElse(Some(e)).get).orElse(x.bbox),
          timestamp = new java.sql.Timestamp(math.max(x.timestamp.getTime, c.timestamp.getTime))))
      }
    }).values.toList
  }

  private def featureCreation(id: String, objVersion: OSMObjectVersion): ChangeResults = {
    ChangeResults(
      changesToSave = List(Change(id, objVersion, FEATURE_CREATE, 1)),
      changesToPropagate = List.empty[ChangeToPropagate]
    )
  }

  private def featureDeletion(id: String, objVersion: OSMObjectVersion): ChangeResults = {
    ChangeResults(
      changesToSave = List(Change(id, objVersion, FEATURE_DELETE, 1)),
      changesToPropagate = List.empty[ChangeToPropagate]
    )
  }

  private def tagAdditions(id: String, objVersion: OSMObjectVersion, priorVersion: OSMObjectVersion): ChangeResults = {
    val newKeysCount =
      objVersion.featureTypeTags.keys.toSet.diff(priorVersion.featureTypeTags.keys.toSet).size +
      objVersion.featurePropertyTags.keys.toSet.diff(priorVersion.featurePropertyTags.keys.toSet).size
    if (newKeysCount > 0) ChangeResults(
      changesToSave = List(Change(id, objVersion, TAG_ADD, newKeysCount)),
      changesToPropagate = List.empty[ChangeToPropagate]
    ) else ChangeResults.empty
  }

  private def tagDeletions(id: String, objVersion: OSMObjectVersion, priorVersion: OSMObjectVersion): ChangeResults = {
    val deletedKeysCount =
      priorVersion.featureTypeTags.keys.toSet.diff(objVersion.featureTypeTags.keys.toSet).size +
      priorVersion.featurePropertyTags.keys.toSet.diff(objVersion.featurePropertyTags.keys.toSet).size
    if (deletedKeysCount > 0) ChangeResults(
      changesToSave = List(Change(id, objVersion, TAG_DELETE, deletedKeysCount)),
      changesToPropagate = List.empty[ChangeToPropagate]
    ) else ChangeResults.empty
  }

  private def tagChanges(id: String, objVersion: OSMObjectVersion, priorVersion: OSMObjectVersion): ChangeResults = {
    val sharedTypeTags = objVersion.featureTypeTags.keySet.intersect(priorVersion.featureTypeTags.keySet)
    val sharedPropertyTags = objVersion.featurePropertyTags.keySet.intersect(priorVersion.featurePropertyTags.keySet)
    val newValuesCount =
      sharedTypeTags.count(key => objVersion.featureTypeTags(key) != priorVersion.featureTypeTags(key)) +
      sharedPropertyTags.count(key => objVersion.featurePropertyTags(key) != priorVersion.featurePropertyTags(key))
    if (newValuesCount > 0) ChangeResults(
      changesToSave = List(Change(id, objVersion, TAG_MODIFY, newValuesCount)),
      changesToPropagate = List.empty[ChangeToPropagate]
    ) else ChangeResults.empty
  }

  private def nodeMoves(id: String, objVersion: OSMObjectVersion, priorVersion: OSMObjectVersion): ChangeResults = {
    if (id(0) == 'n' && (objVersion.lat, objVersion.lon) != (priorVersion.lat, priorVersion.lon)) {
      val change = Change(id, objVersion, NODE_MOVE, 1)
      ChangeResults(
        changesToSave = List(change),
        changesToPropagate = objVersion.parents.map(ChangeToPropagate(_, change))
      )
    } else {
      ChangeResults.empty
    }
  }

  private def nodeAndMemberAdditions(id: String, objVersion: OSMObjectVersion, priorVersion: OSMObjectVersion): ChangeResults = {
    if (id(0) == 'w' || id(0) == 'r') {
      val newMembersCount = objVersion.children.toSet.diff(priorVersion.children.toSet).size
      val changeType = if (id(0) == 'w') NODE_ADD else MEMBER_ADD
      if (newMembersCount > 0) {
        val change = Change(id, objVersion, changeType, newMembersCount)
        ChangeResults(
          changesToSave = List(change),
          changesToPropagate = objVersion.parents.map(ChangeToPropagate(_, change))
        )
      } else ChangeResults.empty
    } else ChangeResults.empty
  }

  private def nodeAndMemberRemovals(id: String, objVersion: OSMObjectVersion, priorVersion: OSMObjectVersion): ChangeResults = {
    if (id(0) == 'w' || id(0) == 'r') {
      val removedMembersCount = priorVersion.children.toSet.diff(objVersion.children.toSet).size
      val changeType = if (id(0) == 'w') NODE_REMOVE else MEMBER_REMOVE
      if (removedMembersCount > 0) {
        val change = Change(id, objVersion, changeType, removedMembersCount)
        ChangeResults(
          changesToSave = List(change),
          changesToPropagate = objVersion.parents.map(ChangeToPropagate(_, change))
        )
      } else ChangeResults.empty
    } else ChangeResults.empty
  }
}
