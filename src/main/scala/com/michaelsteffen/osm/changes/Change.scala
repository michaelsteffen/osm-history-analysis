package com.michaelsteffen.osm.changes

import com.michaelsteffen.osm.osmdata.OSMObjectVersion

final case class Change (
  primaryFeatureID: String,
  primaryFeatureTypes: List[String],
  primaryFeatureVersion: Long,            // version after the change
  changeType: Int,
  count: Int,
  lat: Option[BigDecimal],
  lon: Option[BigDecimal],
  timestamp: java.sql.Timestamp,
  changeset: Long
) {
  def this(id: String, objVer: OSMObjectVersion, changeType: Int, count: Int) = this(
    primaryFeatureID = id,
    primaryFeatureTypes = objVer.primaryFeatureTypes,
    primaryFeatureVersion = objVer.majorVersion,
    changeType = changeType,
    count = count,
    lat = objVer.lat,
    lon = objVer.lon,
    timestamp = objVer.timestamp,
    changeset = objVer.changeset
  )
}

object Change {
  def apply(id: String, objVer: OSMObjectVersion, changeType: Int, count: Int) =
    new Change(id, objVer, changeType, count)
}
