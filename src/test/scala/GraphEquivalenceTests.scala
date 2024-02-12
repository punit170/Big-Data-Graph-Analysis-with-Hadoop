//import org.scalatest.funspec.AnyFunSpec
//import org.scalatest.funsuite.AnyFunSuite
import NetGraphAlgebraDefs.NodeObject
import com.grapheq.Main.*
import ModelAccuracyCheck.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.runtime.stdLibPatches.Predef.assert

class LanguageTest extends AnyFlatSpec with Matchers {
  behavior of "Tests!"

  it should "should cut a graph into subgraphs" in {
    val SubGraphList = cutGraph(originalGraph.get, 10)
    assert(SubGraphList.length != 0)
  }

  it should "throw an error if NodeObject has less than 9 fields" in {
    val node: String = "NodeObject(1,2,3,4,5,6,7,8.0)"
    assertThrows[Exception](stringToNodeObject(node))
  }

  it should "convert a string successfully to a NodeObject even with negative numbers" in {
    val node: String = "NodeObject(1,2,3,4,5,6,7,8,-9.0)"
    assert(stringToNodeObject(node) == NodeObject(1,2,3,4,5,6,7,8,-9.0))
  }

    it should "convert a string successfully to a an EdgeShard" in {
      //edge shard -> case class EdgesShard(allNEdgesInfo: List[((NodeObject, NodeObject), Double)], allPEdgesInfo: List[((NodeObject, NodeObject), Double)])
      val mapper2InputShard = "List((<NodeObject(1,2,3,4,5,6,7,8,9) -> NodeObject(2,2,3,4,5,6,7,8,9)>,0.8));List((<NodeObject(3,2,3,4,5,6,7,8,9) -> NodeObject(4,2,3,4,5,6,7,8,9)>,0.9))"
      val list1 = List(((NodeObject(1,2,3,4,5,6,7,8,9.0),NodeObject(2,2,3,4,5,6,7,8,9.0)),0.8))
      val list2 = List(((NodeObject(3,2,3,4,5,6,7,8,9.0),NodeObject(4,2,3,4,5,6,7,8,9.0)),0.9))
      val edgeshard = EdgesShard(list1, list2)

      assert(deserializeEdgeInfo(mapper2InputShard) == edgeshard)
    }

    it should "throw error while reading a local yaml file if it does not exists" in {
      assertThrows[Exception](getYamlFileContentFromLocal(yamlDir="dummy/path", yamlFileName="predictedYaml.yaml"))
    }

}

