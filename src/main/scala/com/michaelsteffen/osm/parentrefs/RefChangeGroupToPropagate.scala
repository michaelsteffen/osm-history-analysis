package com.michaelsteffen.osm.parentrefs

case class RefChangeGroupToPropagate(
  childID: String,
  changes: List[RefChange]
)