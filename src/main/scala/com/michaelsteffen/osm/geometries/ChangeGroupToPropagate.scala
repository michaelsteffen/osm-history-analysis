package com.michaelsteffen.osm.geometries

import com.michaelsteffen.osm.changes.Change

final case class ChangeGroupToPropagate(
  parentID: Long,
  changes: Array[Change]
)
