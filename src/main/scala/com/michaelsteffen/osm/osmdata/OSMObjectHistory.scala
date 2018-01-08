package com.michaelsteffen.osm.osmdata

final case class OSMObjectHistory (
  objType: String,                          // n, w, or r, stored separately from ID to allow fast filtering by type
  id: String,                               // n<osmID>, w<osmID>, or r<osmID> to make IDs globally unique
  versions: List[OSMObjectVersionDEPRECATED]
)