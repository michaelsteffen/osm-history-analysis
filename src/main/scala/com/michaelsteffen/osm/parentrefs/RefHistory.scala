package com.michaelsteffen.osm.parentrefs

case class RefHistory(
  id: Long,
  hasGeometry: Boolean,
  versions: Array[RefVersion]
)