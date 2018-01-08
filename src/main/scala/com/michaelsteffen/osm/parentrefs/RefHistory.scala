package com.michaelsteffen.osm.parentrefs

case class RefHistory(
  childID: Long,
  versions: Array[RefVersions]
)