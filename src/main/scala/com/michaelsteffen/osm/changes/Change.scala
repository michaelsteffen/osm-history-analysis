package com.michaelsteffen.osm.changes

import com.michaelsteffen.osm.osmdata._

final case class Change (
  primaryFeatureID: String,
  primaryFeatureTypes: List[String],
  primaryFeatureVersion: Long, // version after the change
  changeType: Int,
  count: Int,
  extent: Option[BboxExtent],
  timestamp: java.sql.Timestamp,
  changeset: Long
) {
  def this(id: String, objVer: OSMObjectVersion, changeType: Int, count: Int) = this(
    primaryFeatureID = id,
    primaryFeatureTypes = objVer.primaryFeatureTypes,
    primaryFeatureVersion = objVer.majorVersion,
    changeType = changeType,
    count = count,
    extent =
      if (objVer.lon.nonEmpty && objVer.lat.nonEmpty)
        Some(BboxExtent(Point(objVer.lon.get, objVer.lat.get), Point(objVer.lon.get, objVer.lat.get)))
      else None,
    timestamp = objVer.timestamp,
    changeset = objVer.changeset
  )
}

object Change {
  def apply(id: String, objVer: OSMObjectVersion, changeType: Int, count: Int) =
    new Change(id, objVer, changeType, count)
}
