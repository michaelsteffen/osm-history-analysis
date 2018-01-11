package com.michaelsteffen.osm.osmdata

case class ObjectHistory (
  id: Long,
  versions: Iterator[ObjectVersion]
)
