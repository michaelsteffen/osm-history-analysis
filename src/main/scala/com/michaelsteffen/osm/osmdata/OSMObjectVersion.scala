package com.michaelsteffen.osm.osmdata

final case class OSMObjectVersion(
  majorVersion: Long,
  minorVersion: Long, // incremented when 'parents' change
  timestamp: java.sql.Timestamp,
  visible: Boolean,
  primaryFeatureTypes: List[String], // see http://wiki.openstreetmap.org/wiki/Map_Features
  tags: Map[String,Option[String]],
  lat: Option[BigDecimal],
  lon: Option[BigDecimal],
  children: List[Ref],    // nodes for a way; OSM members for a relation
  parents: List[String],  // defined for nodes if part of a way, and ways if part of a relation
  changeset: Long
)

object OSMObjectVersion {
  def empty: OSMObjectVersion = OSMObjectVersion(
    majorVersion = 0,
    minorVersion = 0,
    timestamp = new java.sql.Timestamp(0),
    visible = false,
    primaryFeatureTypes = List.empty[String],
    tags = Map.empty[String,Option[String]],
    lat = None,
    lon = None,
    children = List.empty[Ref],
    parents = List.empty[String],
    changeset = 0
  )
}