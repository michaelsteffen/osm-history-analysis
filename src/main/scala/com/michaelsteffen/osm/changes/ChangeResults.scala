package com.michaelsteffen.osm.changes

final case class ChangeResults (
  changesToSave: Iterator[Change],
  changesToPropagate: Iterator[ChangeToPropagate]
) {
  def ++ (cr: ChangeResults): ChangeResults = ChangeResults(
    changesToSave = this.changesToSave ++ cr.changesToSave,
    changesToPropagate = this.changesToPropagate ++ cr.changesToPropagate
  )
}

object ChangeResults {
  def empty: ChangeResults = ChangeResults(
    changesToSave = Iterator.empty[Change],
    changesToPropagate = Iterator.empty[ChangeToPropagate]
  )
}