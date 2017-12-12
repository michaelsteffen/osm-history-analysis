package com.michaelsteffen.osm.changes

final case class ChangeResults (
  changesToSave: List[Change],
  changesToPropagate: List[ChangeToPropagate]
) {
  def ++ (cr: ChangeResults): ChangeResults = ChangeResults(
    changesToSave = this.changesToSave ++ cr.changesToSave,
    changesToPropagate = this.changesToPropagate ++ cr.changesToPropagate
  )
}

object ChangeResults {
  def empty: ChangeResults = ChangeResults(
    changesToSave = List.empty[Change],
    changesToPropagate = List.empty[ChangeToPropagate]
  )
}