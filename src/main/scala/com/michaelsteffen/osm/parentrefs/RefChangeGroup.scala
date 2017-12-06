package com.michaelsteffen.osm.parentrefs

case class RefChangeGroup (
  childID: String,
  changes: List[RefChange]
)