/*
package com.grapheq

import NetGraphAlgebraDefs.*
import NetModelAnalyzer.Analyzer
import Utilz.{CreateLogger, NGSConstants}
import com.google.common.graph
import com.google.common.graph.*
import com.grapheq.Main.logger
import com.typesafe.config.ConfigFactory
import net.minidev.asm.ConvertDate.StringCmpNS
import org.slf4j.Logger
import org.yaml.snakeyaml.{DumperOptions, Yaml}

import scala.collection.mutable.{ListBuffer, Map, Queue, Set}
import scala.collection.{immutable, mutable}
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps
import scala.util.Try

//import java.io._
import org.apache.hadoop.fs.{FileSystem, Path}

import java.io.*
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

object Main:

  val logger: Logger = CreateLogger(classOf[Main.type])
//  val originalGraph = NetGraph.load(fileName = "Graph50.ngs", dir = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/Graph 50/")
//  val perturbedGraph = NetGraph.load(fileName = "Graph50.ngs.perturbed", dir = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/Graph 50/")
  val originalGraph = NetGraph.load(fileName = "Graph50.ngs", dir = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/Graph 50/")
  val perturbedGraph = NetGraph.load(fileName = "Graph50.ngs.perturbed", dir = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/Graph 50/")

val ATLMap = mutable.Map[NodeObject, (NodeObject, Float)]()
  val DTLMap = mutable.Map[NodeObject, List[(NodeObject, Float)]]()


  def findMaxIndices(arr: Array[Float]): List[Int] = {
    val maxIndices = arr.zipWithIndex.foldLeft(List.empty[Int]) {
      case (acc, (value, index)) if (value != 0) && (acc.isEmpty || value > arr(acc.head)) =>
        List(index)
      case (acc, (value, index)) if (value != 0) && value == arr(acc.head) =>
        index :: acc
      case (acc, _) => acc
    }
    return maxIndices
  }

  def findBestMatch(u: NodeObject, matchingIndices: List[Int], PGNodes: Array[NodeObject]): Int = {
    for (j <- matchingIndices.indices) {
      val v = PGNodes(matchingIndices(j))
      if (u == v) then return matchingIndices(j)
    }

    val scores: Array[Int] = Array.fill(matchingIndices.length)(0)

    for (j <- matchingIndices.indices) {
      val v = PGNodes(matchingIndices(j))

      if (u.children == v.children) then scores(j) += 1
      if (u.props == v.props) then scores(j) += 1
      if (u.maxDepth == v.maxDepth) then scores(j) += 1
      if (u.maxProperties == v.maxProperties) then scores(j) += 1
    }

    val maxIndex = scores.zipWithIndex.reduceLeft { (a, b) =>
      if (a._1 > b._1) a else b
    }._2
    return matchingIndices(maxIndex)
  }

  def setRowToZero(matrix: Array[Array[Float]], rowNumber: Int): Unit = {
    if (rowNumber >= 0 && rowNumber < matrix.length) {
      for (col <- 0 until matrix(rowNumber).length) {
        matrix(rowNumber)(col) = 0.0
      }
    } else {
      throw new IllegalArgumentException("Invalid row number")
    }
  }

  def setColumnToZero(matrix: Array[Array[Float]], colNumber: Int): Unit = {
    if (colNumber >= 0 && matrix.nonEmpty && colNumber < matrix.head.length) {
      for (row <- matrix.indices) {
        matrix(row)(colNumber) = 0.0
      }
    } else {
      throw new IllegalArgumentException("Invalid column number")
    }
  }

  def cutGraph(graph: NetGraph, numOfParts: Int): List[NetGraph] = {

    def getDepthMap(graph: NetGraph): Map[NodeObject, Int] = {
      val allNodes = graph.sm.nodes().asScala.toSet
      val visitedNodes = Set[NodeObject]()
      val depthMap = Map[NodeObject, Int]()

      def calculateDepthMap(currNode: NodeObject, currDepth: Int): Unit = {
        // Process the current node here
        if !(visitedNodes.contains(currNode)) then {
          visitedNodes += currNode
          depthMap += (currNode -> currDepth)

          // Get successors and recursively process them
          val successors = graph.sm.successors(currNode).asScala
          successors.foreach(successor => calculateDepthMap(successor, currDepth + 1))
        }
      }

      val node0 = allNodes.find(_.id == 0).get
      calculateDepthMap(node0, 0)
      allNodes.foreach(n => if !(visitedNodes.contains(n)) then calculateDepthMap(n, 0))

      return depthMap
    }

    val graphSm = graph.sm
    val capacityPerSubgraph: Int = graph.sm.nodes().size() / numOfParts

    val subgraphs = ListBuffer[NetGraph]()
    val visitedNodes = Set[NodeObject]()
    val bfsQueue = Queue[NodeObject]()
    val graphDepthMap = getDepthMap(graph)

    // Define a BFS traversal function
    def bfsTraversal(startNode: NodeObject): NetGraph = {
      val subgraphNodes = ListBuffer[NodeObject]()
      //NGsm.predecessors(startNode).asScala.toList.foreach(pn => subgraphNodes += pn)
      bfsQueue.enqueue(startNode)
      visitedNodes += startNode

      while (bfsQueue.nonEmpty && subgraphNodes.size < capacityPerSubgraph) {
        val currentNode = bfsQueue.dequeue()
        subgraphNodes += currentNode

        val currNodeSuccessors = graphSm.successors(currentNode).asScala.toList

        // Add neighboring nodes to the BFS queue
        for (neighbor <- currNodeSuccessors) {
          if !visitedNodes.contains(neighbor) then {
            bfsQueue.enqueue(neighbor)
            visitedNodes += neighbor
          }
        }

        // Continue BFS traversal from unvisited nodes
        if (bfsQueue.isEmpty) then {
          val remainingUnvisitedNodes = graphSm.nodes().asScala.filterNot(visitedNodes.contains)
          if (remainingUnvisitedNodes.nonEmpty) {
            val unvisitedDepthMap = graphDepthMap.filter((n, _) => remainingUnvisitedNodes.contains(n))
            val minDepth = unvisitedDepthMap.values.min
            val nodesWithMinDepth = unvisitedDepthMap.filter { case (_, depth) => depth == minDepth }.keys.toList
            val nextMinDepthNode = nodesWithMinDepth.head
            bfsQueue.enqueue(nextMinDepthNode)
            visitedNodes += nextMinDepthNode
          }
        }
      }

      //bfsQueue.foreach(n => NGsm.predecessors(n).asScala.toList.foreach(pn => subgraphNodes += pn))
      // Create an induced subgraph from the shardBuilder
      if (subgraphs.nonEmpty) then
        val lastSubgraphNodes = subgraphs.last.sm.nodes().asScala.toList
        val lastSubgraphSuccessors = lastSubgraphNodes.foldLeft(Set[NodeObject]())((acc, n) => acc ++ graphSm.successors(n).asScala.toSet)
        val currSubgraphPredecessors = subgraphNodes.foldLeft(Set[NodeObject]())((acc, n) => acc ++ graphSm.predecessors(n).asScala.toSet)
        val commonNodes = lastSubgraphSuccessors.intersect(currSubgraphPredecessors).to(ListBuffer)

        val finalSubgraphNodes = commonNodes ++ subgraphNodes
        val subgraph = Graphs.inducedSubgraph(graphSm, (finalSubgraphNodes += graphSm.nodes().asScala.toList.find(_.id == 0).get).asJava)
        val Netsubgraph = NetGraph(subgraph, startNode)
        return Netsubgraph
      else
        val subgraph = Graphs.inducedSubgraph(graphSm, (subgraphNodes += graphSm.nodes().asScala.toList.find(_.id == 0).get).asJava)
        val Netsubgraph = NetGraph(subgraph, startNode)
        return Netsubgraph
    }

    bfsQueue.enqueue(graph.initState)

    while (bfsQueue.nonEmpty) {
      val currStartNode = bfsQueue.dequeue()
      val netsubgraph = bfsTraversal(currStartNode)
      subgraphs += netsubgraph
    }

    println("DepthMap:")
    getDepthMap(graph).foreach(pair => println(s"${pair._1.id} -> ${pair._2}"))
    println()
    return subgraphs.toList
  }

  def serializeShardsAsStrings(nGraphs: List[NetGraph], pGraphs: List[NetGraph], dir: String, fileName: String): Unit = {

    val writer = new BufferedWriter(new FileWriter(s"$dir$fileName.txt"))

    for (nGraph <- nGraphs) {
      for (pGraph <- pGraphs) {
        val ngsm = nGraph.sm
        val pgsm = pGraph.sm

        val allNGraphNodesAsString: String = ngsm.nodes().asScala.toList.toString()
        val allNGraphEdgesAsString: String = ngsm.edges().asScala.toList.toString()
        val allPGraphNodesAsString: String = pgsm.nodes().asScala.toList.toString()
        val allPGraphEdgesAsString: String = pgsm.edges().asScala.toList.toString()

        val nodesEdgesDelimiter = ":"
        val ngpgDelimiter = ";"

        val oneCombinedString = allNGraphNodesAsString + nodesEdgesDelimiter + allNGraphEdgesAsString + ngpgDelimiter + allPGraphNodesAsString + nodesEdgesDelimiter + allPGraphEdgesAsString
        //val oneShard = ShardContanier(fullnGraphAsList, fullpGraphAsList)
        writer.write(oneCombinedString)
        writer.newLine()
      }
    }
    writer.close()
  }

  case class Shard(allNnodes: List[NodeObject], allN_ParentMap: immutable.Map[NodeObject, List[NodeObject]], allPnodes: List[NodeObject], allP_ParentMap: immutable.Map[NodeObject, List[NodeObject]])

  def stringToNodeObject(strObj: String): NodeObject = {
    val regexPattern = """-?\d+(\.\d+)?""".r
    val nodeFields = regexPattern.findAllIn(strObj).toArray
    assert(nodeFields.length == 9)
    /*NodeObject(id: Int, children: Int, props: Int, currentDepth: Int = 1, propValueRange:Int,
     maxDepth:Int, maxBranchingFactor:Int, maxProperties:Int, storedValue: Double)*/

    val id = nodeFields(0).toInt
    val children = nodeFields(1).toInt
    val props = nodeFields(2).toInt
    val currentDepth = nodeFields(3).toInt
    val propValueRange = nodeFields(4).toInt
    val maxDepth = nodeFields(5).toInt
    val maxBranchingFactor = nodeFields(6).toInt
    val maxProperties = nodeFields(7).toInt
    val storedValue = nodeFields(8).toDouble

    NodeObject(id, children, props, currentDepth, propValueRange, maxDepth, maxBranchingFactor, maxProperties, storedValue)
  }

  def deserializeStringShard(shard_string: String): (Shard) = {

    val tempStrArr = shard_string.split(";").map(substr => substr.split(":"))

    val allNGraphNodesAsString = tempStrArr(0)(0).substring(5, tempStrArr(0)(0).length - 1)
    val allNGraphEdgesAsString = tempStrArr(0)(1).substring(5, tempStrArr(0)(1).length - 1)
    val allPGraphNodesAsString = tempStrArr(1)(0).substring(5, tempStrArr(1)(0).length - 1)
    val allPGraphEdgesAsString = tempStrArr(1)(1).substring(5, tempStrArr(1)(1).length - 1)

    val allNGraphNodesAsStringList = """NodeObject\([^)]+\)""".r.findAllIn(allNGraphNodesAsString).toList
    val allNGraphEdgesAsStringList = """NodeObject\([0-9.,-]+\) -> NodeObject\([0-9.,-]+\)""".r.findAllIn(allNGraphEdgesAsString).toList
    val allPGraphNodesAsStringList = """NodeObject\([^)]+\)""".r.findAllIn(allPGraphNodesAsString).toList
    val allPGraphEdgesAsStringList = """NodeObject\([0-9.,-]+\) -> NodeObject\([0-9.,-]+\)""".r.findAllIn(allPGraphEdgesAsString).toList


    val tempNGraphAdjacencyMap = Map[String, ListBuffer[String]]()
    allNGraphEdgesAsStringList.foreach { str =>
      val Array(parent, child) = str.split("->")
      tempNGraphAdjacencyMap.getOrElseUpdate(child, mutable.ListBuffer.empty) += parent
    }
    val NGraphAdjacencyMap: immutable.Map[String, List[String]] = tempNGraphAdjacencyMap.foldLeft(immutable.Map[String, List[String]]())((acc, m) => acc + (m._1 -> m._2.toList))

    val tempPGraphAdjacencyMap = Map[String, ListBuffer[String]]()
    allPGraphEdgesAsStringList.foreach { str =>
      val Array(parent, child) = str.split("->")
      tempPGraphAdjacencyMap.getOrElseUpdate(child, mutable.ListBuffer.empty) += parent
    }
    val PGraphAdjacencyMap: immutable.Map[String, List[String]] = tempPGraphAdjacencyMap.foldLeft(immutable.Map[String, List[String]]())((acc, m) => acc + (m._1 -> m._2.toList))


    val allNGraphNodes: List[NodeObject] = allNGraphNodesAsStringList.map(nodeStr => stringToNodeObject(nodeStr))
    val allPGraphNodes: List[NodeObject] = allPGraphNodesAsStringList.map(nodeStr => stringToNodeObject(nodeStr))
    val allNGraphParentMap: immutable.Map[NodeObject, List[NodeObject]] = NGraphAdjacencyMap.foldLeft(immutable.Map[NodeObject, List[NodeObject]]())((acc, m) => acc + (stringToNodeObject(m._1) -> m._2.map(nodeStr => stringToNodeObject(nodeStr))))
    val allPGraphParentMap: immutable.Map[NodeObject, List[NodeObject]] = PGraphAdjacencyMap.foldLeft(immutable.Map[NodeObject, List[NodeObject]]())((acc, m) => acc + (stringToNodeObject(m._1) -> m._2.map(nodeStr => stringToNodeObject(nodeStr))))

    Shard(allNGraphNodes, allNGraphParentMap, allPGraphNodes, allPGraphParentMap)

  }

  def deserializeMapperOutputValue(str: String): List[(NodeObject, Float)] = {
    val tempValueStrList = """NodeObject\([-?[0-9]*\.?[0-9]*,]+\),\s?[-?[0-9]*\.? [0-9]*]+""".r.findAllIn(str.substring(5, str.length - 1)).toList
    val nodevalpair_split_pattern = """(.+),\s?(.+)""".r

    val ValList = ListBuffer[(NodeObject, Float)]()

    for(nodevalpair <- tempValueStrList) {
      val result = nodevalpair match {
        case nodevalpair_split_pattern(substring1,substring2) => (substring1,substring2)
        case _ => ("NodeObject(-1,0,0,0,0,0,0,0,0)","-1.0")
      }

      ValList += ((stringToNodeObject(result._1), result._2.toFloat))
    }
    //ValList.foreach(i => println(s"${i._1} -> ${i._2}"))
    //println(ValList.toList)
    ValList.toList
  }

  def SimRankv_2(NgNodes: List[NodeObject], NgParentMap: immutable.Map[NodeObject, List[NodeObject]], PgNodes: List[NodeObject], PgParentMap: immutable.Map[NodeObject, List[NodeObject]]): immutable.Map[NodeObject, List[(NodeObject, Float)]] = {

    val SRMap = Map[(NodeObject, NodeObject), Float]()

    NgNodes.foreach(nNode =>
      PgNodes.foreach(pNode => {
        if nNode == pNode then {
          SRMap += (nNode, pNode) -> 1.0f
        }
        else {
          SRMap += (nNode, pNode) -> 0.0f
        }
      }
      )
    )
    NgNodes.foreach(nNode =>
      PgNodes.foreach(pNode =>

        if (nNode != pNode) {
          val nParentNodes = NgParentMap.get(nNode)
          val pParentNodes = PgParentMap.get(pNode)

          nParentNodes match
            case Some(nParentList) =>
              pParentNodes match
                case Some(pParentList) =>
                  /*if(nNode.id == 20 && pNode.id == 19)
                    println("got here!")*/
                  val coeffPart: Float = 1.0f / (nParentList.length * pParentList.length)

                  val combos = ListBuffer[(NodeObject, NodeObject)]()
                  nParentList.foreach(nParentNode => pParentList.foreach(pParentNode => combos += ((nParentNode, pParentNode))))

                  val sumPart = combos.foldLeft(0.0f) { case (acc, (nParentNode, pParentNode)) =>
                    acc + SRMap(nParentNode, pParentNode)
                  }
                  //assert(coeffPart * sumPart != 0)
                  SRMap((nNode, pNode)) = BigDecimal(coeffPart * sumPart).setScale(2, BigDecimal.RoundingMode.HALF_UP).toFloat
                case None =>
                  SRMap += (nNode, pNode) -> 0.0f
            case None =>
              SRMap += (nNode, pNode) -> 0.0f

        }
      )
    )

    val SROut: immutable.Map[NodeObject, List[(NodeObject, Float)]] = SRMap.groupBy { case ((node1, _), _) => node1}.map {
      case (node, entries) =>
        val entryList = entries.map { case ((_, node2), value) => (node2, value) }.toList
        (node, entryList)
      }
    addToDTLMap(SROut)
    return SROut
  }

  def findBestNodeMatch(NGNode: NodeObject, PGNodeMatches: List[(NodeObject, Float)]): (NodeObject, Float) = {
    for (matchpair <- PGNodeMatches) {
      if (NGNode == matchpair._1) then return matchpair
    }

    val scores = PGNodeMatches.foldLeft(Map[NodeObject, Int]())( (acc, ele) => acc + (ele._1 -> 0))

    PGNodeMatches.foreach((PGNode,_) => {
      if (NGNode.children == PGNode.children) then scores(PGNode) += 1
      if (NGNode.props == PGNode.props) then scores(PGNode) += 1
      if (NGNode.maxDepth == PGNode.maxDepth) then scores(PGNode) += 1
      if (NGNode.maxProperties == PGNode.maxProperties) then scores(PGNode) += 1
    }
    )
    println(scores)
    PGNodeMatches.find((n,_) => n == scores.toList.sortBy(-_._2).head._1) match
      case Some(matchpair) => matchpair
      case None => throw new Exception("best score element not found in Perturbed SubGraph Nodes!")
  }

  case class PerturbedNodes(modifiedNodeIds: List[(Int, Int)], addedNodeIds: List[Int], removedNodeIds: List[Int])
  case class PerturbedEdges(modifiedEdgeIds: immutable.Map[Int, Int], addedEdgeIds: immutable.Map[Int, Int], removedEdgeIds: immutable.Map[Int, Int])

  def decipherGraphEquivalence(matchMap: Map[NodeObject, NodeObject], NG: NetGraph, PG: NetGraph): Tuple4[PerturbedNodes,PerturbedEdges, List[(Int, Int)], immutable.Map[Int, Int]] = {
    val NG_sm = NG.sm
    val PG_sm = PG.sm

    val NGNodes = NG_sm.nodes().asScala.toList
    val PGNodes = PG_sm.nodes().asScala.toList

    val removedNodes = ListBuffer[NodeObject]()
    //val matchNodesMap = Map[NodeObject, NodeObject]()
    val modifiedNodesMap = Map[NodeObject, NodeObject]()
    val addedNodes = ListBuffer[NodeObject]()
    val unperturbedNodesMap = Map[NodeObject, NodeObject]()

    val unperturbedNodesMapValues: List[NodeObject] = unperturbedNodesMap.values.toList

    for(nodeMatch <- matchMap){
      val nodeU = nodeMatch._1
      val nodeV = nodeMatch._2
      if (nodeU == nodeV) then
        unperturbedNodesMap += nodeU -> nodeV
      else
        if (nodeV.id == -1) then
          removedNodes += nodeU
        else if(nodeU.id == nodeV.id) then
          modifiedNodesMap += nodeU -> nodeV
        else if (matchMap.contains(nodeV) && matchMap(nodeV).id == nodeV.id)  then
          removedNodes += nodeU
        else
          modifiedNodesMap += nodeU -> nodeV
    }

    PGNodes.filter(n => !unperturbedNodesMapValues.contains(n) && !modifiedNodesMap.values.toList.contains(n)).foreach(an => addedNodes += an)

    val removedAndAddedIds = removedNodes.map(rn => rn.id).intersect(addedNodes.map(an => an.id))
    val removedAndAddedMap = removedAndAddedIds.foldLeft(immutable.Map[NodeObject, NodeObject]())( (acc,raId) => acc + (removedNodes.find(rn => rn.id == raId).get -> addedNodes.find(an => an.id == raId).get))
    val updatedRemovedNodes = removedNodes.toList.diff(removedAndAddedMap.keys.toList)
    val updatedAddedNodes = addedNodes.toList.diff(removedAndAddedMap.values.toList)
    val updatedModifiedNodesMap = modifiedNodesMap ++ removedAndAddedMap
    

    val removedEdges = ListBuffer[(NodeObject, NodeObject)]()
    val addedEdges = ListBuffer[(NodeObject, NodeObject)]()
    val modifiedEdges = ListBuffer[(NodeObject, NodeObject)]()
    val unPerturbedEdges = Map[NodeObject, NodeObject]()

    for (rn <- removedNodes) {
      val associatedEdges = NG_sm.incidentEdges(rn)
      associatedEdges.asScala.toList.foreach(ae => removedEdges += ae.source() -> ae.target())
    }

    for (an <- addedNodes) {
      val associatedEdges = PG_sm.incidentEdges(an)
      associatedEdges.asScala.toList.foreach(ae => addedEdges += ae.source() -> ae.target())
    }

    val otherNodesMap = unperturbedNodesMap ++ modifiedNodesMap

    for (on <- otherNodesMap) {
      val uAssEdges = NG_sm.incidentEdges(on._1).asScala.toList
      val vAssEdges = PG_sm.incidentEdges(on._2).asScala.toList

      for (ue <- uAssEdges) {
        if !(removedNodes.contains(ue.source()) || removedNodes.contains(ue.target())) then
          //          val veCheck = vAssEdges.find(v => ue == v)
          val ueSourceMatchNode = otherNodesMap(ue.source())
          val ueTargetMatchNode = otherNodesMap(ue.target())
          val matchPair = PG_sm.hasEdgeConnecting(ueSourceMatchNode, ueTargetMatchNode)
          matchPair match {
            case true =>
              if (NG_sm.edgeValue(ue.source(), ue.target()) != PG_sm.edgeValue(ueSourceMatchNode, ueTargetMatchNode)) then
                modifiedEdges += ue.source() -> ue.target()
                addedEdges += ue.source() -> ue.target()
                removedEdges += ue.source() -> ue.target()
              else {
                unPerturbedEdges += ue.source() -> ue.target()
              }
            case false =>
              removedEdges += ue.source() -> ue.target()
          }
      }
      for (ve <- vAssEdges) {
        if !(addedNodes.contains(ve.source()) || addedNodes.contains(ve.target())) then
          val veSourceMatch = otherNodesMap.find { case (_, v) => v == ve.source() }.map(_._1)
          val veTargetMatch = otherNodesMap.find { case (_, v) => v == ve.target() }.map(_._1)
          veSourceMatch match {
            case Some(veSourceMatchNode) =>
              veTargetMatch match {
                case Some(veTargetMatchNode) =>
                  if !(NG_sm.hasEdgeConnecting(veSourceMatchNode, veTargetMatchNode)) then
                    addedEdges += ve.source() -> ve.target()
                case None => {}
              }
            case None => {}
          }
      }
    }
/*    println("NGNodes:")
    println(NGNodes)

    println("\nPGNodes:")
    println(PGNodes)

    println("\nMatchedNodesMap:")
//    println(unperturbedNodesMap)
    println(unperturbedNodesMap.map((n1, n2) => print(s"${n1.id} -> ${n2.id},")))

    println("\nmodifiedNodes:")
//    println(modifiedNodesMap)
    updatedModifiedNodesMap.map((n1, _) => print(s"${n1.id}, "))

    println("\nRemovedNodes:")
//    println(removedNodes)
    updatedRemovedNodes.map(n => print(s"${n.id}, "))
//
    println("\nAddedNodes:")
//    println(addedNodes)
    updatedAddedNodes.map(n => print(s"${n.id}, "))

    println("\n\nmodifiedEdges:")
//    println(modifiedEdges)
    modifiedEdges.toSet.map((n1, n2) => print(s"${n1.id}:${n2.id}, "))

    println("\nAddedEdges:")
//    println(addedEdges)
    addedEdges.toSet.map((n1, n2) => print(s"${n1.id}:${n2.id}, "))

    println("\nRemovedEdges:")
//    println(removedEdges)
    removedEdges.toSet.map((n1, n2) => print(s"${n1.id}:${n2.id}, "))*/

    val pn = PerturbedNodes(updatedModifiedNodesMap.map(e => (e._1.id -> e._2.id)).toList, updatedAddedNodes.map(an => an.id), updatedRemovedNodes.map(rn => rn.id))
    val pe = PerturbedEdges(modifiedEdges.toSet.map((n1, n2) => (n1.id, n2.id)).toMap, addedEdges.toSet.map((n1, n2) => (n1.id, n2.id)).toMap, removedEdges.toSet.map((n1, n2) => (n1.id, n2.id)).toMap)
    val upn = unperturbedNodesMap.map(ele => (ele._1.id -> ele._2.id)).toList
    val upe = unPerturbedEdges.map(ele => (ele._1.id -> ele._2.id)).toMap
    return (pn, pe, upn, upe)
  }

  def createYamlFile(dir: String, yamlFileName: String): Unit = {
    val yamlFilePath = dir.concat(yamlFileName).concat(".yaml")
    val yamlFile = new File(yamlFilePath)
    if (!yamlFile.exists()) {
      val allNodesMod: String = (List("Nodes:\n") ::: List("\tModified: [") ::: List(List.empty[Int].mkString(", ")) ::: List("]\n")
        ::: List("\tRemoved: [") ::: List(List.empty[Int].mkString(", ")) ::: List("]\n") ::: List("\tAdded: [") ::: List(List.empty[Int].mkString(", ")) ::: List("]\n")).mkString
      val allEdgesMod: String = (List("Edges:\n") ::: List("\tModified:\n") ::: List(s"\t\t\n") ::: List("\tRemoved:\n") ::: List(s"\t\t\n") ::: List("\tAdded:\n") ::: List(s"\t\t\n")).mkString

      val data = allNodesMod.mkString.concat(allEdgesMod.mkString)

      val fh = new PrintWriter(new File(yamlFilePath))
      try {
        fh.write(data)
        println(s"YAML file created at: $yamlFilePath")
      } finally {
        fh.close()
      }
    }
    else {
      println(s"Yaml File $yamlFileName.yaml already exists!")
    }
  }

  def replaceTabsWithSpaces(file: String): Unit = {
    val source = Source.fromFile(file)
    val lines = try source.getLines().map(_.replace("\t", "    ")).mkString("\n") finally source.close()

    val writer = new PrintWriter(new File(file))
    try writer.write(lines) finally writer.close()
  }

  def appendToYamlSubsection(dir: String, yamlFileName: String, section: String, subsection: String, newData: List[String]): Unit = {
    val yamlFilePath = dir.concat(yamlFileName).concat(".yaml")
    val yamlFile = new File(yamlFilePath)

    if (!yamlFile.exists()) {
      println(s"YAML File $yamlFileName.yaml does not exist.")
    }
    else
    {
      //replace tab in yaml with spaces
      replaceTabsWithSpaces(yamlFilePath)

      //load existing yaml file content as string
      val existingContent = Source.fromFile(yamlFilePath).mkString

      val yaml = new Yaml()

      //parse yaml into desired data structure
      val data:java.util.Map[String, java.util.Map[String, java.util.List[Int] | java.util.Map[Int, Int]]] = yaml.load(existingContent)

      val modifiedNodesList: List[String] = data.asScala("Nodes").asScala("Modified") match
        case e: java.util.List[Integer] => {
          print(s"elements:$e")
          if section == "Nodes" && subsection == "Modified" then
            (e.asScala.map(ele => String.valueOf(ele)).toList ::: newData).distinct
          else if !e.isEmpty then
            e.asScala.map(ele => String.valueOf(ele)).toList
          else
            List.empty[String]
        }

          //throw new Exception(s"data from yaml: $yamlFileName should be convertible into List[Int] by yaml parser!")
        case _ => {
          println("Yaml Nodes->Modified is probably empty")
          if newData.isEmpty then
            immutable.List.empty[String]
          else
            newData.distinct
        }

      val removedNodesList: List[String] = data.asScala("Nodes").asScala("Removed") match
        case e: java.util.List[Integer] => {
          if section == "Nodes" && subsection == "Removed" then
            (e.asScala.toList.map(ele => String.valueOf(ele)) ::: newData).distinct
          else
            e.asScala.toList.map(ele => String.valueOf(ele)).distinct
        }
        case _ => {
          println("Yaml Nodes->Removed was probably empty")
          if newData.isEmpty then
            immutable.List.empty[String]
          else
            newData.distinct
        }

      val addedNodesList: List[String] = data.asScala("Nodes").asScala("Added") match
        case e: java.util.List[Integer] => {
          if section == "Nodes" && subsection == "Added" then
            (e.asScala.toList.map(ele => String.valueOf(ele)) ::: newData).distinct
          else
            e.asScala.toList.map(ele => String.valueOf(ele)).distinct
        }
        case _ => {
          println("Yaml Nodes->Added was probably empty")
          if newData.isEmpty then
            immutable.List.empty[String]
          else
            newData.distinct
        }

      val modifiedEdgesMap: immutable.Map[String, String] = data.asScala("Edges").asScala("Modified") match
        case e: java.util.Map[Integer, Integer] => {
          if section == "Edges" && subsection == "Modified" then
            e.asScala.map((u, v) => String.valueOf(u) -> String.valueOf(v)).toMap ++ (newData.foldLeft(immutable.Map.empty[String, String]) { (acc, str) =>
              val items = str.split(":")
              acc + (items(0) -> items(1))
            })
          else
            e.asScala.map((u, v) => String.valueOf(u) -> String.valueOf(v)).toMap
        }
        case _ => {
          if section == "Edges" && subsection == "Modified" then
            println("Yaml Edges->Modified was probably empty")
            if newData.isEmpty then
              immutable.Map.empty[String, String]
            else
            (newData.foldLeft(immutable.Map.empty[String, String]) { (acc, str) =>
              val items = str.split(":")
              acc + (items(0) -> items(1))
            })
          else
            immutable.Map.empty[String, String]

          //throw new Exception(s"data from yaml: $yamlFileName should be convertible into Map[Int,Int] by yaml parser!")
        }

      val removedEdgesMap: immutable.Map[String, String] = data.asScala("Edges").asScala("Removed") match
        case e: java.util.Map[Integer, Integer] => {
            if section == "Edges" && subsection == "Removed" then
              e.asScala.map((u, v) => String.valueOf(u) -> String.valueOf(v)).toMap ++ (newData.foldLeft(immutable.Map.empty[String, String]) { (acc, str) =>
                val items = str.split(":")
                acc + (items(0) -> items(1))
              })
            else
              e.asScala.map((u, v) => String.valueOf(u) -> String.valueOf(v)).toMap
        }
        //case _ => throw new Exception(s"data from yaml: $yamlFileName should be convertible into Map[Int,Int] by yaml parser!")
        case _ => {
          if section == "Edges" && subsection == "Removed" then
            println("Yaml Edges->Removed is probably empty")
              if newData.isEmpty then
                immutable.Map.empty[String, String]
              else
                (newData.foldLeft(immutable.Map.empty[String, String]) { (acc, str) =>
                  val items = str.split(":")
                  acc + (items(0) -> items(1))
                })
          else
            immutable.Map.empty[String, String]
        }

      val addedEdgesMap: immutable.Map[String, String] = data.asScala("Edges").asScala("Added") match
        case e: java.util.Map[Integer, Integer] => {
          if section == "Edges" && subsection == "Added" then
            e.asScala.map((u, v) => String.valueOf(u) -> String.valueOf(v)).toMap ++ (newData.foldLeft(immutable.Map.empty[String, String]) { (acc, str) =>
              val items = str.split(":")
              acc + (items(0) -> items(1))
            })
          else
            e.asScala.map((u, v) => String.valueOf(u) -> String.valueOf(v)).toMap
        }
        //case _ => throw new Exception(s"data from yaml: $yamlFileName should be convertible into Map[Int,Int] by yaml parser!")
        case _ => {
          if section == "Edges" && subsection == "Added" then
            println("Yaml Edges->Added is probably empty")
            if newData.isEmpty then
              immutable.Map.empty[String, String]
            else
              (newData.foldLeft(immutable.Map.empty[String, String]) { (acc, str) =>
                val items = str.split(":")
                acc + (items(0) -> items(1))
              })
          else
            immutable.Map.empty[String, String]
        }

      val allNodesMod: String = (List("Nodes:\n") ::: List("\tModified: [") ::: List(modifiedNodesList.mkString(", ")) ::: List("]\n")
        ::: List("\tRemoved: [") ::: List(removedNodesList.mkString(", ")) ::: List("]\n") ::: List("\tAdded: [") ::: List(addedNodesList.mkString(", ")) ::: List("]\n")).mkString
      //    val allEdgesMod: String = (List("Edges:\n") ::: List("\tModified:\n") ::: List(s"\t\t\n") ::: List("\tRemoved:\n") ::: List(s"\t\t\n") ::: List("\tAdded:\n") ::: List(s"\t\t\n")).mkString

      val allEdgesMod: List[String] = List("Edges:\n") ::: modifiedEdgesMap.foldLeft(List[String]("\tModified:\n"))(
        (acc, elem) => acc ::: List(s"\t\t${elem._1}: ${elem._2}\n")
      ) ++ addedEdgesMap.foldLeft(List[String]("\tAdded:\n"))(
        (acc, elem) => acc ::: List(s"\t\t${elem._1}: ${elem._2}\n")
      ) ++ removedEdgesMap.foldLeft(List[String]("\tRemoved:\n"))(
        (acc, elem) => acc ::: List(s"\t\t${elem._1}: ${elem._2}\n")
      )
      val dataToWrite: String = allNodesMod.mkString.concat(allEdgesMod.mkString)

      val fh = new PrintWriter(new File(yamlFilePath))
      try {
        fh.write(dataToWrite)
        println(s"YAML file modified at: $yamlFilePath")
      } finally {
        fh.close()
      }
    }
  }

  def readNodeMatchHDFSFile(reducerOutputFilePath: String): immutable.Map[NodeObject, NodeObject]  = {

    val conf = new org.apache.hadoop.conf.Configuration()
    val fs = FileSystem.get(conf)

    val inputStream = fs.open(new Path(reducerOutputFilePath))
    val reader = new BufferedReader(new InputStreamReader(inputStream))

    val matchMap = mutable.Map[NodeObject, NodeObject]()
    try {
      var line: String = null
      while ( {
        line = reader.readLine();
        line != null
      }) {
        val nodePairStr = line.split("\t")
        val nodeU = stringToNodeObject(nodePairStr(0))
        val pattern = """NodeObject\([-?[0-9]*\.?[0-9]*,]+\)"""
        val nodeV = stringToNodeObject((pattern.r.findFirstIn(nodePairStr(1))).get)

        //println(s"nodeU: $nodeU -- nodeV: $nodeV")
        matchMap += nodeU -> nodeV
      }
    } finally {
      reader.close()
      inputStream.close()
    }

    val finalMatchMap = Map[NodeObject,NodeObject]()
    for (nodeMatch <- matchMap.filterNot(ele => ele._2.id == -1)) {
      val nodeU = nodeMatch._1
      val nodeV = nodeMatch._2
      if (nodeU.id == nodeV.id) || !(matchMap.contains(nodeV) && matchMap(nodeV).id == nodeV.id) then
        finalMatchMap += nodeU -> nodeV
    }
    println("FinalMatchMap:")
    println(finalMatchMap)
    return finalMatchMap.toMap
  }

  case class NodePerturbationInfo(modifiedNodesMap: immutable.Map[NodeObject, NodeObject], removedNodes: List[NodeObject], addedNodes: List[NodeObject], unperturbedNodesMap: immutable.Map[NodeObject, NodeObject])

  def decipherNodes(reducerOutputFilePath: String, NG: NetGraph, PG: NetGraph, newYamlFileDir: String, newYamlFileName: String): NodePerturbationInfo = {

    val conf = new org.apache.hadoop.conf.Configuration()
    val fs = FileSystem.get(conf)

    val inputStream = fs.open(new Path(reducerOutputFilePath))
    val reader = new BufferedReader(new InputStreamReader(inputStream))

    val matchMap = mutable.Map[NodeObject, NodeObject]()
    try {
      var line: String = null
      while ( {
        line = reader.readLine();
        line != null
      }) {
        val nodePairStr = line.split("\t")
        val nodeU = stringToNodeObject(nodePairStr(0))
        val pattern = """NodeObject\([-?[0-9]*\.?[0-9]*,]+\)"""
        val nodeV = stringToNodeObject((pattern.r.findFirstIn(nodePairStr(1))).get)

        //println(s"nodeU: $nodeU -- nodeV: $nodeV")
        matchMap += nodeU -> nodeV
      }
    } finally {
      reader.close()
      inputStream.close()
    }

    val removedNodes = ListBuffer[NodeObject]()
    val modifiedNodesMap = Map[NodeObject, NodeObject]()
    val addedNodes = ListBuffer[NodeObject]()
    val unperturbedNodesMap = Map[NodeObject, NodeObject]()

    for (nodeMatch <- matchMap) {
      val nodeU = nodeMatch._1
      val nodeV = nodeMatch._2
      if (nodeU == nodeV) then
        unperturbedNodesMap += nodeU -> nodeV
      else if (nodeV.id == -1) then
        removedNodes += nodeU
      else if (nodeU.id == nodeV.id) then
        modifiedNodesMap += nodeU -> nodeV
      else if (matchMap.contains(nodeV) && matchMap(nodeV).id == nodeV.id) then
        removedNodes += nodeU
      else
        modifiedNodesMap += nodeU -> nodeV
    }

    val unperturbedNodesMapValues: List[NodeObject] = unperturbedNodesMap.values.toList

    val PGNodes = PG.sm.nodes().asScala.toList
    PGNodes.filter(n => !unperturbedNodesMapValues.contains(n) && !modifiedNodesMap.values.toList.contains(n)).foreach(an => addedNodes += an)

    val removedAndAddedIds = removedNodes.map(rn => rn.id).intersect(addedNodes.map(an => an.id))
    val removedAndAddedMap = removedAndAddedIds.foldLeft(immutable.Map[NodeObject, NodeObject]())((acc, raId) => acc + (removedNodes.find(rn => rn.id == raId).get -> addedNodes.find(an => an.id == raId).get))
    val updatedRemovedNodes = removedNodes.toList.diff(removedAndAddedMap.keys.toList)
    val updatedAddedNodes = addedNodes.toList.diff(removedAndAddedMap.values.toList)
    val updatedModifiedNodesMap = (modifiedNodesMap ++ removedAndAddedMap)


    val updatedRemovedNodesStrList: List[String] = updatedRemovedNodes.map(node => node.id.toString)
    val updatedAddedNodesStrList: List[String] = updatedAddedNodes.map(node => node.id.toString)
    val updatedModifiedStrList: List[String] = updatedModifiedNodesMap.map((u,_)=>u.id.toString).toList

    createYamlFile(dir=newYamlFileDir, yamlFileName=newYamlFileName)

    appendToYamlSubsection(dir=newYamlFileDir, yamlFileName=newYamlFileName, section="Nodes", subsection="Removed", newData=updatedRemovedNodesStrList)
    appendToYamlSubsection(dir=newYamlFileDir, yamlFileName=newYamlFileName, section="Nodes", subsection="Added", newData=updatedAddedNodesStrList)
    appendToYamlSubsection(dir=newYamlFileDir, yamlFileName=newYamlFileName, section="Nodes", subsection="Modified", newData=updatedModifiedStrList)

    val removedEdges = updatedRemovedNodes.foldLeft(List.empty[(NodeObject, NodeObject)])( (acc, rn) => {
      val associatedEdges = NG.sm.incidentEdges(rn).asScala.toList
      acc ++ associatedEdges.map(ae => (ae.source(), ae.target()))
    }).map((u,v)=>u.id.toString.concat(":").concat(v.id.toString)).toList

    appendToYamlSubsection(dir=newYamlFileDir, yamlFileName=newYamlFileName, section="Edges", subsection="Removed", newData=removedEdges)

    val addedEdges = updatedAddedNodes.foldLeft(List.empty[(NodeObject, NodeObject)])((acc, rn) => {
      val associatedEdges = PG.sm.incidentEdges(rn).asScala.toList
      acc ++ associatedEdges.map(ae => (ae.source(), ae.target()))
    }).map((u,v)=>u.id.toString.concat(":").concat(v.id.toString)).toList

    appendToYamlSubsection(dir=newYamlFileDir, yamlFileName=newYamlFileName, section="Edges", subsection="Added", newData=addedEdges)

    return NodePerturbationInfo(updatedModifiedNodesMap.toMap, updatedRemovedNodes, updatedAddedNodes, unperturbedNodesMap.toMap)

  }

  def serializeEdgeInfo(nodePerturbationObj: NodePerturbationInfo, NG: NetGraph, PG: NetGraph) = {
    val edgesInfoTextFileDir: String = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/"
    val edgesInfoTextFileName: String = "edgeInfo"

    val writer = new BufferedWriter(new FileWriter(s"$edgesInfoTextFileDir$edgesInfoTextFileName.txt"))
    val NGraphPGraphDelimiter = ";"

    val otherNodesMap = nodePerturbationObj.modifiedNodesMap ++ nodePerturbationObj.unperturbedNodesMap

    for (nodeMatch <- otherNodesMap) {
      val nodeU = nodeMatch._1
      val nodeV = nodeMatch._2
      val nodeUEdgesInfo = NG.sm.incidentEdges(nodeU).asScala.toList.map(ep => (ep, NG.sm.edgeValue(ep.source(), ep.target()).get().cost)).filterNot(ele => (nodePerturbationObj.removedNodes.contains(ele._1.source()) || nodePerturbationObj.removedNodes.contains(ele._1.target())))
      val nodeVEdgesInfo = PG.sm.incidentEdges(nodeV).asScala.toList.map(ep => (ep, PG.sm.edgeValue(ep.source(), ep.target()).get().cost)).filterNot(ele => (nodePerturbationObj.addedNodes.contains(ele._1.source()) || nodePerturbationObj.addedNodes.contains(ele._1.target())))

      val oneCombinedString = nodeUEdgesInfo.toString() + NGraphPGraphDelimiter + nodeVEdgesInfo.toString()

      writer.write(oneCombinedString)
      writer.newLine()
    }
    writer.close()
    println("Edges Info written successfully!")
  }

  case class EdgesShard(allNEdgesInfo: List[((NodeObject, NodeObject), Double)], allPEdgesInfo: List[((NodeObject, NodeObject), Double)])

  def deserializeEdgeInfo(shard_string: String): EdgesShard = {
/*    val filePath = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/edgeInfo.txt"
    val firstLine: String = Source.fromFile(filePath).getLines().take(1).toList.headOption.get
    println(firstLine)*/

    val shardStrSplit: Array[String] = shard_string.split(';')

    val regex = """\(\<NodeObject\([-?[0-9]*\.?[0-9]*,]+\)\s?->\s?NodeObject\([-?[0-9]*\.?[0-9]*,]+\)\>,\s?[\d.]+\)""".r
    // Find all matches in the input string
    val nodeUEdgesStr = regex.findAllMatchIn(shardStrSplit(0)).toList
    val nodeVEdgesStr = regex.findAllMatchIn(shardStrSplit(1)).toList

    val nodeUEdgesInfo = ListBuffer[((NodeObject, NodeObject), Double)]()
    val nodeVEdgesInfo = ListBuffer[((NodeObject, NodeObject), Double)]()

    val nodeRegex = """NodeObject\([-?[0-9]*\.?[0-9]*,]+\)""".r

    for(nodeUEdgeStr <- nodeUEdgesStr){
      // Apply the regular expression to the input string
      val nodesArr = """NodeObject\([-?[0-9]*\.?[0-9]*,]+\)\s?->\s?NodeObject\([-?[0-9]*\.?[0-9]*,]+\)""".r.findFirstIn(nodeUEdgeStr.toString()).get.split("->")
      val nodesTuple = (stringToNodeObject(nodesArr(0)), stringToNodeObject(nodesArr(1)))
      val resultString = nodeRegex.replaceAllIn(nodeUEdgeStr.toString(), "")
      val doubleValue = """[\d.]+""".r.findFirstIn(resultString).get.toDouble
      nodeUEdgesInfo += ((nodesTuple, doubleValue))
    }
    for (nodeVEdgeStr <- nodeVEdgesStr) {
      // Apply the regular expression to the input string
      val nodesArr = """NodeObject\([-?[0-9]*\.?[0-9]*,]+\)\s?->\s?NodeObject\([-?[0-9]*\.?[0-9]*,]+\)""".r.findFirstIn(nodeVEdgeStr.toString()).get.split("->")
      val nodesTuple = (stringToNodeObject(nodesArr(0)), stringToNodeObject(nodesArr(1)))
      val resultString = nodeRegex.replaceAllIn(nodeVEdgeStr.toString(), "")
      val doubleValue = """[\d.]+""".r.findFirstIn(resultString).get.toDouble
      nodeVEdgesInfo += ((nodesTuple, doubleValue))
    }
    EdgesShard(nodeUEdgesInfo.toList, nodeVEdgesInfo.toList)
  }

  def readEdgeInfoHDFSFile(reducerOutputFilePath: String): immutable.Map[String, List[String]] = {
    val conf = new org.apache.hadoop.conf.Configuration()
    val fs = FileSystem.get(conf)

    val inputStream = fs.open(new Path(reducerOutputFilePath))
    val reader = new BufferedReader(new InputStreamReader(inputStream))

    val edgeSectionWiseInfoMap = mutable.Map[String, List[String]]()
    try {
      var line: String = null
      while ( {
        line = reader.readLine();
        line != null
      }) {
        val edgesInfo = line.split("\t")
        val sectionName = edgesInfo(0)
        val edgeIdPairs = edgesInfo(1).substring(10, edgesInfo(1).length-2).split(',').toList.map(str => str.strip())
        //println(s"nodeU: $nodeU -- nodeV: $nodeV")
        edgeSectionWiseInfoMap += sectionName -> edgeIdPairs
      }
    } finally {
      reader.close()
      inputStream.close()
    }

    return edgeSectionWiseInfoMap.toMap
  }

  def addToDTLMap(SRout: immutable.Map[NodeObject, List[(NodeObject, Float)]]) = {

    val updatedSRout = SRout.map(elem => elem._1 -> elem._2.filterNot(matchPair => matchPair._2 == 0.0))

    updatedSRout.foreach( elem => {
      if DTLMap.contains(elem._1) then
        DTLMap(elem._1) = (DTLMap(elem._1) ::: elem._2).distinct
      else
        DTLMap(elem._1) = elem._2
    }
    )

  }

  def updateDTLMap() = {

    DTLMap.keys.foreach( keyNode => {
      val intermediateResult = DTLMap(keyNode).filterNot(ele => ele._2 == 0.0).sortBy(-_._2)
      if(intermediateResult.length < 1) then
        println("empty intermediateresult !")

      else
        val result1 = intermediateResult.filter(ele => ele._2 == intermediateResult(0)._2)

        if (result1.length == 1) then
          ATLMap(keyNode) = result1.head
          DTLMap(keyNode) = intermediateResult.filterNot(pair => pair == ATLMap(keyNode))
        else if (result1.length > 1) then
          ATLMap(keyNode) = findBestNodeMatch(keyNode, result1)
          DTLMap(keyNode) = intermediateResult.filterNot(pair => pair == ATLMap(keyNode))
        else if (result1.length > 1)
          DTLMap(keyNode) = intermediateResult

      })
    val emptyDTLKeys = DTLMap.keys.filter( keyNode => DTLMap(keyNode).isEmpty)
    emptyDTLKeys.foreach(keyNode => DTLMap.remove(keyNode))
  }

  def main(args: Array[String]): Unit = {

    val outGraphFileName = if args.isEmpty then NGSConstants.OUTPUTFILENAME else args(0).concat(NGSConstants.DEFOUTFILEEXT)
    val perturbedOutGraphFileName = outGraphFileName.concat(".perturbed")
    val outputDirectory = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/Graph 50/"
    val existingGraph = java.io.File(s"$outputDirectory$outGraphFileName").exists
    val g: Option[NetGraph] = if existingGraph then
      logger.warn(s"File $outputDirectory$outGraphFileName is located, loading it up. If you want a new generated graph please delete the existing file or change the file name.")
      NetGraph.load(fileName = s"$outputDirectory$outGraphFileName")
    else
      /*val config = ConfigFactory.load()
      logger.info("for the main entry")
      config.getConfig("NGSimulator").entrySet().forEach(e => logger.info(s"key: ${e.getKey} value: ${e.getValue.unwrapped()}"))
      logger.info("for the NetModel entry")
      config.getConfig("NGSimulator").getConfig("NetModel").entrySet().forEach(e => logger.info(s"key: ${e.getKey} value: ${e.getValue.unwrapped()}"))
      NetModelAlgebra()*/
      throw new Exception("No such graph exists!")

    val inNetGraphFileName: String = outGraphFileName
    val perturbedGraphFileName: String = perturbedOutGraphFileName
    val inputDirectory = outputDirectory

    val loadedNetGraph: Option[NetGraph] = NetGraph.load(inNetGraphFileName, inputDirectory)
    val loadedPerturbedGraph: Option[NetGraph] = NetGraph.load(perturbedGraphFileName, inputDirectory)


    loadedNetGraph match
      case Some(netgraph: NetGraph) =>
        loadedPerturbedGraph match
          case Some(perturbedGraph: NetGraph) => {

            println("Making net subgraphs.....\n")
            val NGSubgraphs = cutGraph(netgraph, 2)
            println("Making perturbed subgraphs.....\n")
            val PGSubgraphs = cutGraph(perturbedGraph, 2)

            println("Net subgraphs:")
            NGSubgraphs.foreach(NGsg => println(NGsg.sm.nodes().asScala.toList.map(n => n.id)))
            println("Perturbed subgraphs:")
            PGSubgraphs.foreach(PGsg => println(PGsg.sm.nodes().asScala.toList.map(n => n.id)))

            println("Serializing ")
            serializeShardsAsStrings(NGSubgraphs, PGSubgraphs, dir="C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/", fileName="Input50")
            println("Input file saved successfully!")

//            val testStr = "List((NodeObject(3,3,9,1,42,3,1,1,0.5005844734614175),1.0), (NodeObject(5,4,18,1,29,0,3,18,0.15923190680934673),0.0), (NodeObject(9,6,10,1,13,3,2,13,0.9517234134142836),0.0), (NodeObject(10,1,2,1,39,2,4,8,0.5985405016913854),0.0), (NodeObject(6,0,5,1,21,3,1,3,0.3082692006957388),0.0), (NodeObject(0,5,17,1,11,2,1,4,0.010068705151044188),0.0), (NodeObject(7,3,11,1,57,3,6,2,0.5963008760476638),0.0))"
//            deserializeMapperOutputValue(testStr)

//            val nodeInfo = decipherNodes("C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/output100/part-00000", NG=netgraph, PG=perturbedGraph, newYamlFileDir="C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/", newYamlFileName="myYaml")
//            serializeEdgeInfo(nodeInfo._1, nodeInfo._2, nodeInfo._3, netgraph, perturbedGraph)
//            deserializeEdgeInfo("")

          }
          case None => println("Perturbed Graph Load failed!")
      case None => println("Net Graph Load failed!")


  }*/
