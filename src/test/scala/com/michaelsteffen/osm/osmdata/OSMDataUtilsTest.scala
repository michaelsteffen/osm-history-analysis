package com.michaelsteffen.osm.osmdata

import com.michaelsteffen.osm.testspecs._
import com.michaelsteffen.osm.osmdata._
import java.sql._

class OSMDataUtilsTest extends UnitTest {
  describe("The createID method") {
    it("should create a correct id for a node") {
      assert(OSMDataUtils.createID(100, "node") === "n100")
    }

    it("should create a correct id for a way") {
      assert(OSMDataUtils.createID(101, "way") === "w101")
    }

    it("should create a correct id for a relation") {
      assert(OSMDataUtils.createID(102, "relation") === "r102")
    }

    it("should create a ?-prefixed id for any unknown type") {
      assert(OSMDataUtils.createID(103, "anything") === "?103")
    }

    it("should create a ?-prefixed id for an empty (\"\") type") {
      assert(OSMDataUtils.createID(104, "") === "?104")
    }
  }

  describe("The toOSMObjectHistory method") {

    // test fixtures
    object F {
      val baseNodeRawVersion = RawOSMObjectVersion(
        id = 100,
        `type` = "node",
        tags = Map.empty[String, Option[String]],
        lat = Some(-77.2),
        lon = Some(38.9),
        nds = List.empty[RawNodeRef],
        members = List.empty[RawMemberRef],
        changeset = 200,
        timestamp = new java.sql.Timestamp(2017, 1, 1, 0, 0, 0, 0),
        uid = 300,
        user = "OSMUser",
        version = 1,
        visible = true
      )

      val baseOSMObjectHistory = OSMObjectHistoryDEPRECATED(
        objType = "n",
        id = "n100",
        versions = List(
          OSMObjectVersionDEPRECATED(
            majorVersion = 1,
            minorVersion = 0,
            timestamp = new Timestamp(2017, 1, 1, 0, 0, 0, 0),
            visible = true,
            tags = Map.empty[String, Option[String]],
            lat = Some(-77.2),
            lon = Some(38.9),
            children = List.empty[Ref],
            parents = List.empty[String],
            changeset = 200
          )
        )
      )
    }

    it("should correctly handle a simple, single-version node") {
      assert(
        OSMDataUtils.toOSMObjectHistory("n100", Iterator(F.baseNodeRawVersion)) ===
          F.baseOSMObjectHistory
      )
    }

    it("should correctly sort versions by timestamp") {
      val rawv1 = F.baseNodeRawVersion
      val rawv2 = F.baseNodeRawVersion.copy(
        version = 2,
        timestamp = new Timestamp(F.baseNodeRawVersion.timestamp.getTime + 600000),
        changeset = 201,
        lat = Some(-78.0)
      )
      val rawv3 = F.baseNodeRawVersion.copy(
        version = 3,
        timestamp = new Timestamp(F.baseNodeRawVersion.timestamp.getTime + 1200000),
        changeset = 301,
        lat = Some(-76.0)
      )
      val rawHistory = Iterator(rawv2, rawv1, rawv3)
      val convertedv1 = F.baseOSMObjectHistory.versions.head
      val convertedv2 = convertedv1.copy(
        majorVersion = 2,
        timestamp = new Timestamp(F.baseNodeRawVersion.timestamp.getTime + 600000),
        changeset = 201,
        lat = Some(-78.0)
      )
      val convertedv3 = convertedv1.copy(
        majorVersion = 3,
        timestamp = new Timestamp(F.baseNodeRawVersion.timestamp.getTime + 1200000),
        changeset = 301,
        lat = Some(-76.0)
      )
      val convertedHistory = F.baseOSMObjectHistory.copy(
        versions = List(convertedv1, convertedv2, convertedv3)
      )

      assert(OSMDataUtils.toOSMObjectHistory("n100", rawHistory) === convertedHistory)
    }

    describe("when converting child references") {
      it("should correctly handle way->node references") {
        val rawVersion = F.baseNodeRawVersion.copy(
          `type` =  "way",
          nds = List(RawNodeRef(101), RawNodeRef(202), RawNodeRef(303))
        )
        val expected = List(Ref("n101",""), Ref("n202",""), Ref("n303",""))
        val converted = OSMDataUtils.toOSMObjectHistory("w100", Iterator(rawVersion))
        assert(converted.versions.head.children === expected)
      }

      it("should correctly handle relation refs") {
        val rawVersion = F.baseNodeRawVersion.copy(
          `type` =  "relation",
          members = List(
            RawMemberRef(101, "node", "role1"),
            RawMemberRef(202, "way", "role2"),
            RawMemberRef(303, "relation", "role3")
          )
        )
        val expected = List(Ref("n101","role1"), Ref("w202","role2"), Ref("r303","role3"))
        val converted = OSMDataUtils.toOSMObjectHistory("r100", Iterator(rawVersion))
        assert(converted.versions.head.children === expected)
      }
    }
  }
}
