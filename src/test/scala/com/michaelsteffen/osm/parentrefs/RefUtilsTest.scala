package com.michaelsteffen.osm.parentrefs

import com.michaelsteffen.osm.testspecs.UnitTest

class RefUtilsTest extends UnitTest {
  describe("The generateRefChangesFromObjectHistory method") {
    it("should generate an empty history for an object with only one version") {}
    it("should detect the addition of a node from a way") {}
    it("should detect the addition of a member from a relation") {}
    it("should detect the removal of a node from a way") {}
    it("should detect the removal of a member from a relation") {}
    it("should detect the addition of multiple children") {}
    it("should detect the removal of multiple children") {}
    it("should detect the addition and removal of children in the same change") {}
  }

  describe("The collectRefChangesForChild method") {
    it("should sort ref changes by timestamp") {}
  }

  describe("The addParentRefs method") {
    it("should return an unmodified object history if the RefChangeGroupToPropagate is null") {}
    it("should return an unmodified object history if the RefChangeGroupToPropagate is empty") {}
    it("should correctly add parent refs to the first version of an object") {}
    describe("when handling parent ref changes contemporaneous with creation of a new major version of an object") {
      it("should add new parent refs to the major version of the object") {}
      it("should remove deleted parent refs from the major version of the object") {}
    }
    describe("when handling parent ref changes between major versions of an object") {
      it("should create a minor version of the object and with new parent refs") {}
      it("should create a minor version of the object and with deleted parent refs") {}
      it("should consolidate contemporaneous parent changes into the same minor version of the object") {}
      it("should create multiple minor version of the object as needed") {}
    }
  }
}
