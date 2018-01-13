package com.michaelsteffen.osm.changes

import com.michaelsteffen.osm.osmdata._

final case class Change (
  featureID: Long,
  changeType: Int,
  count: Int,
  tagsBefore: Map[String, Option[String]],
  tagChanges: Map[String, Option[String]],
  bbox: Option[Bbox],
  timestamp: java.sql.Timestamp,
  changeset: Long
) {
  def this(id: Long, changeType: Int, count: Int, after: ObjectVersion) = this(
    featureID = id,
    changeType = changeType,
    count = count,
    tagsBefore =
      if (changeType == ChangeUtils.FEATURE_CREATE) Map.empty[String,Option[String]]
      else after.tags,
    tagChanges =
      if (changeType == ChangeUtils.FEATURE_CREATE) after.tags
      else Map.empty[String,Option[String]],
    bbox = Change.lonLatToBbox(after.lon, after.lat),
    timestamp = after.timestamp,
    changeset = after.changeset.toLong
  )

  def this(id: Long, changeType: Int, count: Int, before: ObjectVersion, after: ObjectVersion, tagChanges: Map[String, Option[String]]) = this(
    featureID = id,
    changeType = changeType,
    count = count,
    tagsBefore = before.tags,
    tagChanges = tagChanges,
    bbox = Change.lonLatToBbox(after.lon, after.lat),
    timestamp = after.timestamp,
    changeset = after.changeset.toLong
  )
}

object Change {
  def nonTagChange(id: Long, changeType: Int, count: Int, after: ObjectVersion) =
    new Change(id, changeType, count, after)

  def tagChange(id: Long, changeType: Int, count: Int, before: ObjectVersion, after: ObjectVersion, tagChanges: Map[String, Option[String]]) =
    new Change(id, changeType, count, before, after, tagChanges)

  def lonLatToBbox(lon: Option[BigDecimal], lat: Option[BigDecimal]): Option[Bbox] = {
    if (lon.nonEmpty && lat.nonEmpty) Some(Bbox(Point(lon.get, lat.get), Point(lon.get, lat.get)))
    else None
  }
}
