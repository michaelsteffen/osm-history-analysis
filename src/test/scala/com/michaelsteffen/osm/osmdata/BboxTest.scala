package com.michaelsteffen.osm.osmdata

import com.michaelsteffen.osm.testspecs._

class BboxTest extends UnitTest {
  describe("A Bbox") {
    it("should union correctly with another Bbox") {
      val bbox = Bbox(Point(75, 35), Point(76,38))
      val other = Bbox(Point(75.5, 37), Point(77,39))
      val expected = Bbox(Point(75, 35), Point(77,39))
      assert(bbox.union(other) === expected)
    }

    ignore("should union correctly when it crosses the dateline") {
      false
    }

    ignore("should union correctly when the other Bbox crosses the dateline") {
      false
    }

    ignore("should union correctly when both Bboxes cross the dateline") {
      false
    }
  }
}
