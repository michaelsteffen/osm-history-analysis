package com.michaelsteffen.osm.geometries

import com.michaelsteffen.osm.changes.Change

final case class ChangeToPropagate(
  parentID: Long,
  change: Change
)
