package com.michaelsteffen.osm.changes

final case class ChangeResults (
  changesToSave: Array[Change],
  changesToPropagate: Array[ChangeToPropagate]
) {
  def ++ (cr: ChangeResults): ChangeResults = ChangeResults(
    changesToSave = this.changesToSave ++ cr.changesToSave,
    changesToPropagate = this.changesToPropagate ++ cr.changesToPropagate
  )
}

object ChangeResults {
  def empty: ChangeResults = ChangeResults(
    changesToSave = Array.empty[Change],
    changesToPropagate = Array.empty[ChangeToPropagate]
  )
}