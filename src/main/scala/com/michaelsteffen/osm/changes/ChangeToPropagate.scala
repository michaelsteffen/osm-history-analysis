package com.michaelsteffen.osm.changes

final case class ChangeToPropagate(
  parentID: String,
  change: Change
)
