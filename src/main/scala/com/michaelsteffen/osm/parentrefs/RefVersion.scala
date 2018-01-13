package com.michaelsteffen.osm.parentrefs

// TODO: could maybe get a little memory savings here by just making timestamp the first element of the array?
case class RefVersion(
  timestamp: java.sql.Timestamp,
  wayOrRelationHasGeometry: Boolean,           // only captured for ways & relations
  parents: Array[Long]
)
