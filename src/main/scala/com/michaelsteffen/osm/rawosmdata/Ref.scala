package com.michaelsteffen.osm.rawosmdata

final case class Ref(                       // unify notion of node ref and member ref for simpler logic
  ref: String,
  role: String                              // "" in the case of a node on a way
)


