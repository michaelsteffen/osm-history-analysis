package com.michaelsteffen.osm.rawosmdata

final case class NodeRef(
  ref: Long
) {
  def toMember: MemberRef = MemberRef(
    ref = this.ref,
    `type` = "node",
    role = ""
  )
}