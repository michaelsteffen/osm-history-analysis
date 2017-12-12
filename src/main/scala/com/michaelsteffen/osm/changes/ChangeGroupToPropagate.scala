package com.michaelsteffen.osm.changes

final case class ChangeGroupToPropagate(
  parentID: String,
  changes: List[Change]
)
