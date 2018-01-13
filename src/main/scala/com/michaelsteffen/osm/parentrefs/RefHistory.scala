package com.michaelsteffen.osm.parentrefs

case class RefHistory(
  id: Long,
  versions: Array[RefVersion]
)