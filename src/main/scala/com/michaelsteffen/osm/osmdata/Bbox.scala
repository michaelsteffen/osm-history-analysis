package com.michaelsteffen.osm.osmdata

// TODO: Handle meridian
final case class Bbox(min: Point, max: Point) {
  def union(other: Bbox): Bbox = Bbox(
    min = Point(this.min.lon.min(other.min.lon), this.min.lat.min(other.min.lat)),
    max = Point(this.max.lon.max(other.max.lon), this.max.lat.max(other.max.lat))
  )
}