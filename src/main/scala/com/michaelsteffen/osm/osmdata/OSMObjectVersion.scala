package com.michaelsteffen.osm.osmdata

final case class OSMObjectVersion(
  majorVersion: Long,
  minorVersion: Long,     // incremented when 'parents' change
  timestamp: java.sql.Timestamp,
  visible: Boolean,
  featureTypeTags:  Map[String,Option[String]],
  featurePropertyTags:  Map[String,Option[String]],
  lat: Option[BigDecimal],
  lon: Option[BigDecimal],
  children: List[Ref],    // nodes for a way; OSM members for a relation
  parents: List[String],  // defined for nodes if part of a way, and ways if part of a relation
  changeset: Long
) {
  def isFeature: Boolean = featureTypeTags.nonEmpty || featurePropertyTags.nonEmpty
}

object OSMObjectVersion {
  def empty: OSMObjectVersion = OSMObjectVersion(
    majorVersion = 0,
    minorVersion = 0,
    timestamp = new java.sql.Timestamp(0),
    visible = false,
    featureTypeTags = Map.empty[String,Option[String]],
    featurePropertyTags = Map.empty[String,Option[String]],
    lat = None,
    lon = None,
    children = List.empty[Ref],
    parents = List.empty[String],
    changeset = 0
  )
}