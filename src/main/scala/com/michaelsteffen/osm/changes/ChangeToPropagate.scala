package com.michaelsteffen.osm.changes

final case class ChangeToPropagate(
  parentID: Long,
  change: Change
)
