package com.michaelsteffen.osm.parentrefs

case class RefChange (
  childID: Long,
  parentID: Long,
  changeset: Long,
  timestamp: java.sql.Timestamp,
  changeType: Int
)