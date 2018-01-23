package com.michaelsteffen.osm.geometries

case class NodeLocationToPropagate(
  parentID: Long,
  nodeID: Long,
  timestamp: java.sql.Timestamp,
  location: Point
)
