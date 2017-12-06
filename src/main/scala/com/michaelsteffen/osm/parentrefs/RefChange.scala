package com.michaelsteffen.osm.parentrefs

case class RefChange (
  childID: String,
  parentID: String,
  changeset: Long,
  timestamp: java.sql.Timestamp,
  changeType: Int
)