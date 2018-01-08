package com.michaelsteffen.osm.changes

import com.michaelsteffen.osm.osmdata._
import com.michaelsteffen.osm.rawosmdata.{Bbox, Point}

final case class Change (
  featureID: String,
  changeType: Int,
  count: Int,
  version: Long,    // version after the change
  tagsBefore: Map[String, Option[String]],
  tagChanges: Map[String, Option[String]],
  bbox: Option[Bbox],
  timestamp: java.sql.Timestamp,
  changeset: Long
) {
  def this(id: String, changeType: Int, count: Int, after: OSMObjectVersionDEPRECATED) = this(
    featureID = id,
    changeType = changeType,
    count = count,
    version = after.majorVersion,
    tagsBefore =
      if (changeType == ChangeUtils.FEATURE_CREATE) Map.empty[String,Option[String]]
      else after.tags,
    tagChanges =
      if (changeType == ChangeUtils.FEATURE_CREATE) after.tags
      else Map.empty[String,Option[String]],
    bbox = Change.lonLatToBbox(after.lon, after.lat),
    timestamp = after.timestamp,
    changeset = after.changeset
  )

  def this(id: String, changeType: Int, count: Int, before: OSMObjectVersionDEPRECATED, after: OSMObjectVersionDEPRECATED, tagChanges: Map[String, Option[String]]) = this(
    featureID = id,
    changeType = changeType,
    count = count,
    version = after.majorVersion,
    tagsBefore = before.tags,
    tagChanges = tagChanges,
    bbox = Change.lonLatToBbox(after.lon, after.lat),
    timestamp = after.timestamp,
    changeset = after.changeset
  )
}

object Change {
  def nonTagChange(id: String, changeType: Int, count: Int, after: OSMObjectVersionDEPRECATED) =
    new Change(id, changeType, count, after)

  def tagChange(id: String, changeType: Int, count: Int, before: OSMObjectVersionDEPRECATED, after: OSMObjectVersionDEPRECATED, tagChanges: Map[String, Option[String]]) =
    new Change(id, changeType, count, before, after, tagChanges)

  def lonLatToBbox(lon: Option[BigDecimal], lat: Option[BigDecimal]): Option[Bbox] = {
    if (lon.nonEmpty && lat.nonEmpty) Some(Bbox(Point(lon.get, lat.get), Point(lon.get, lat.get)))
    else None
  }
}
