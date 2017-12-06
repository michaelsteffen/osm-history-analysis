package com.michaelsteffen.osm.osmdata

final case class OSMObjectVersion(
  primaryFeatureTypes: List[String],        // see http://wiki.openstreetmap.org/wiki/Map_Features
  tags: Map[String,Option[String]],
  lat: Option[BigDecimal],
  lon: Option[BigDecimal],
  members: List[Ref],                       // nodes for a way; OSM members for a relation
  parents: List[String],                    // defined for nodes if part of a way, and ways if part of a relation
  majorVersion: Long,
  minorVersion: Long,                       // incremented when 'parents' changes
  changeset: Long,
  timestamp: java.sql.Timestamp,
  visible: Boolean
)