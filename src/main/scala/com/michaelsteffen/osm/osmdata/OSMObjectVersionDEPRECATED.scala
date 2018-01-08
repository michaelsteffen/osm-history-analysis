package com.michaelsteffen.osm.osmdata

final case class OSMObjectVersionDEPRECATED(
  majorVersion: Long,
  minorVersion: Long,     // incremented when 'parents' change
  timestamp: java.sql.Timestamp,
  visible: Boolean,
  tags:  Map[String,Option[String]],
  lat: Option[BigDecimal],
  lon: Option[BigDecimal],
  children: List[Ref],    // nodes for a way; OSM members for a relation
  parents: List[String],  // defined for nodes if part of a way, and ways if part of a relation
  changeset: Long
) {
  // multipolygons with no other tags are not counted as features to avoid double-counting old-style multipolygons
  // with tags on the outer way(s)
  def isFeature: Boolean = !(tags.isEmpty || tags == Map("type" -> Some("multipolygon")))
}

object OSMObjectVersionDEPRECATED {
  def empty: OSMObjectVersionDEPRECATED = OSMObjectVersionDEPRECATED(
    majorVersion = 0,
    minorVersion = 0,
    timestamp = new java.sql.Timestamp(0),
    visible = false,
    tags = Map.empty[String,Option[String]],
    lat = None,
    lon = None,
    children = List.empty[Ref],
    parents = List.empty[String],
    changeset = 0
  )
}