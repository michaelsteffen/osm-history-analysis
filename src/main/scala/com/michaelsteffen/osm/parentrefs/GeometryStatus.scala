package com.michaelsteffen.osm.parentrefs

case class GeometryStatus(
  id: Long,
  timestamp: java.sql.Timestamp,
  hasGeometry: Boolean
)
