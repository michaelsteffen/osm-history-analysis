package com.michaelsteffen.osm.changes

final case class ChangeGroupToPropagate(
  parentID: Long,
  changes: Iterator[Change]
)
