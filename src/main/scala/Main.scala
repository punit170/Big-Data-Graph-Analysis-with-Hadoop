package com.grapheq

import Variables.GraphConfigReader.*
import LoggingUtil.GraphUtil.CreateLogger
import NetGraphAlgebraDefs.{Action, GraphPerturbationAlgebra, NetGraph, NetGraphComponent, NetModelAlgebra, NodeObject}
import NetModelAnalyzer.Analyzer
import Utilz.NGSConstants
import com.google.common.graph.{EndpointPair, Graphs, MutableValueGraph, Traverser, ValueGraph, ValueGraphBuilder}
import com.grapheq.Main.logger
import org.slf4j.Logger

import scala.collection.{immutable, mutable}
import scala.language.postfixOps
import scala.collection.mutable.{ListBuffer, Map, Queue, Set}
import scala.util.Try
import scala.io.Source
import scala.jdk.CollectionConverters.*
import org.yaml.snakeyaml.{DumperOptions, Yaml}

import java.io.OutputStreamWriter
import java.net.URI

//import java.io._
import java.io.{BufferedWriter, FileInputStream, FileWriter, PrintWriter, BufferedReader,File, FileReader, InputStreamReader}
import java.util.Base64
import java.nio.charset.StandardCharsets.UTF_8
import org.apache.hadoop.fs.{FileSystem, Path}

object Main {

  val logger: Logger = CreateLogger(classOf[Main.type])
  //  val originalGraph = NetGraph.load(fileName = "Graph50.ngs", dir = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/Graph 50/")
  //  val perturbedGraph = NetGraph.load(fileName = "Graph50.ngs.perturbed", dir = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/Graph 50/")

  val originalGraph: Option[NetGraph] = NetGraph.load(fileName = originalGraphFileName, dir = NGSGraphDir)
  val perturbedGraph: Option[NetGraph] = NetGraph.load(fileName = perturbedGraphFileName, dir = NGSGraphDir)

  val ATLMap: mutable.Map[NodeObject, (NodeObject, Float)] = mutable.Map[NodeObject, (NodeObject, Float)]()
  val DTLMap: mutable.Map[NodeObject, List[(NodeObject, Float)]] = mutable.Map[NodeObject, List[(NodeObject, Float)]]()

  case class PerturbedNodes(modifiedNodeIds: List[(Int, Int)], addedNodeIds: List[Int], removedNodeIds: List[Int])
  case class PerturbedEdges(modifiedEdgeIds: immutable.Map[Int, Int], addedEdgeIds: immutable.Map[Int, Int], removedEdgeIds: immutable.Map[Int, Int])

  //takes a NetGraph, and cuts it into a list of subgraphs apprimately equal to the numOfParts mentioned.
  def cutGraph(graph: NetGraph, numOfParts: Int): List[NetGraph] = {

    def getDepthMap(graph: NetGraph): mutable.Map[NodeObject, Int] = {
      val allNodes = graph.sm.nodes().asScala.toSet
      val visitedNodes = mutable.Set[NodeObject]()
      val depthMap = mutable.Map[NodeObject, Int]()

      //required to calculate parents of nodes, so that whn subgraph is split, the linked nodes are present in both the subgraphs 
      def calculateDepthMap(currNode: NodeObject, currDepth: Int): Unit = {
        // Process the current node here
        if !visitedNodes.contains(currNode) then {
          visitedNodes += currNode
          depthMap += (currNode -> currDepth)

          // Get successors and recursively process them
          val successors = graph.sm.successors(currNode).asScala
          successors.foreach(successor => calculateDepthMap(successor, currDepth + 1))
        }
      }

      val node0 = allNodes.find(_.id == 0).get
      calculateDepthMap(node0, 0)
      allNodes.foreach(n => if !visitedNodes.contains(n) then calculateDepthMap(n, 0))

      depthMap
    }

    val graphSm = graph.sm
    val capacityPerSubgraph: Int = graph.sm.nodes().size() / numOfParts

    val subgraphs = ListBuffer[NetGraph]()
    val visitedNodes = mutable.Set[NodeObject]()
    val bfsQueue = mutable.Queue[NodeObject]()
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
        if bfsQueue.isEmpty then {
          val remainingUnvisitedNodes = graphSm.nodes().asScala.diff(visitedNodes)
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
      if subgraphs.nonEmpty then
        val lastSubgraphNodes = subgraphs.last.sm.nodes().asScala.toList
        val lastSubgraphSuccessors = lastSubgraphNodes.foldLeft(mutable.Set[NodeObject]())((acc, n) => acc ++ graphSm.successors(n).asScala.toSet)
        val currSubgraphPredecessors = subgraphNodes.foldLeft(mutable.Set[NodeObject]())((acc, n) => acc ++ graphSm.predecessors(n).asScala.toSet)
        val commonNodes = lastSubgraphSuccessors.intersect(currSubgraphPredecessors).to(ListBuffer)

        val finalSubgraphNodes = commonNodes ++ subgraphNodes

        val subgraph = Graphs.inducedSubgraph(graphSm, (finalSubgraphNodes += graphSm.nodes().asScala.toList.find(_.id == 0).get).asJava)
        val Netsubgraph = NetGraph(subgraph, startNode)
        Netsubgraph
      else
        val subgraph = Graphs.inducedSubgraph(graphSm, (subgraphNodes += graphSm.nodes().asScala.toList.find(_.id == 0).get).asJava)
        val Netsubgraph = NetGraph(subgraph, startNode)
        Netsubgraph
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
    subgraphs.toList
  }

  //Created mapper1inputfile. Serializes the subgraph pairs as a string into the file, one pair in each line. 
  def serializeShardsAsStrings(nGraphs: List[NetGraph], pGraphs: List[NetGraph], dir: String, fileName: String): Unit = {

    val writer = new BufferedWriter(new FileWriter(s"$dir$fileName"))

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

  //converts a node object string back to a node object. 
  def stringToNodeObject(strObj: String): NodeObject = {
    //val regexPattern = """-?\d+(\.\d+[E\-\d+]?)?""".r
    val regexPattern = """-?[\d.]+,\s?-?[\d.]+,\s?-?[\d.]+,\s?-?[\d.]+,\s?-?[\d.]+,\s?-?[\d.]+,\s?-?[\d.]+,\s?-?[\d.]+,\s?-?[\d.E-]+""".r
//    val nodeFields = regexPattern.findAllIn(strObj).toArray
    val nodeFields: Array[String] = regexPattern.findFirstIn(strObj).get.split(',')
    if(nodeFields.length != 9){throw new Exception(s"NodeStr: $strObj doesn't have 9 fields!")}
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

  //required to deserializ a shard to the mapper1's map input back to node objects.
  def deserializeStringShard(shard_string: String): Shard = {

    val tempStrArr = shard_string.split(";").map(substr => substr.split(":"))

    val allNGraphNodesAsString = tempStrArr(0)(0).substring(5, tempStrArr(0)(0).length - 1)
    val allNGraphEdgesAsString = tempStrArr(0)(1).substring(5, tempStrArr(0)(1).length - 1)
    val allPGraphNodesAsString = tempStrArr(1)(0).substring(5, tempStrArr(1)(0).length - 1)
    val allPGraphEdgesAsString = tempStrArr(1)(1).substring(5, tempStrArr(1)(1).length - 1)

    val allNGraphNodesAsStringList = """NodeObject\([^)]+\)""".r.findAllIn(allNGraphNodesAsString).toList
    val allNGraphEdgesAsStringList = """NodeObject\([0-9.,-]+\) -> NodeObject\([0-9.,-]+\)""".r.findAllIn(allNGraphEdgesAsString).toList
    val allPGraphNodesAsStringList = """NodeObject\([^)]+\)""".r.findAllIn(allPGraphNodesAsString).toList
    val allPGraphEdgesAsStringList = """NodeObject\([0-9.,-]+\) -> NodeObject\([0-9.,-]+\)""".r.findAllIn(allPGraphEdgesAsString).toList


    val tempNGraphAdjacencyMap = mutable.Map[String, ListBuffer[String]]()
    allNGraphEdgesAsStringList.foreach { str =>
      val Array(parent, child) = str.split("->")
      tempNGraphAdjacencyMap.getOrElseUpdate(child, mutable.ListBuffer.empty) += parent
    }
    val NGraphAdjacencyMap: immutable.Map[String, List[String]] = tempNGraphAdjacencyMap.foldLeft(immutable.Map[String, List[String]]())((acc, m) => acc + (m._1 -> m._2.toList))

    val tempPGraphAdjacencyMap = mutable.Map[String, ListBuffer[String]]()
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

  //required to deserialise a line from map-reduce job 1 output file, post map-reduce1 job
  def deserializeMapperOutputValue(str: String): List[(NodeObject, Float)] = {
    val tempValueStrList = """NodeObject\([-?[0-9]*\.?[0-9]*,]+\),\s?[-?[0-9]*\.? [0-9]*]+""".r.findAllIn(str.substring(5, str.length - 1)).toList
    val nodevalpair_split_pattern = """(.+),\s?(.+)""".r

    val ValList = ListBuffer[(NodeObject, Float)]()

    for (nodevalpair <- tempValueStrList) {
      val result = nodevalpair match {
        case nodevalpair_split_pattern(substring1, substring2) => (substring1, substring2)
        case _ => ("NodeObject(-1,0,0,0,0,0,0,0,0)", "-1.0")
      }

      ValList += ((stringToNodeObject(result._1), result._2.toFloat))
    }
    //ValList.foreach(i => println(s"${i._1} -> ${i._2}"))
    //println(ValList.toList)
    ValList.toList
  }

  //nodes matching simrank algorithm
  def SimRankv_2(NgNodes: List[NodeObject], NgParentMap: immutable.Map[NodeObject, List[NodeObject]], PgNodes: List[NodeObject], PgParentMap: immutable.Map[NodeObject, List[NodeObject]]): immutable.Map[NodeObject, List[(NodeObject, Float)]] = {

    val SRMap = mutable.Map[(NodeObject, NodeObject), Float]()

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

    val SROut: immutable.Map[NodeObject, List[(NodeObject, Float)]] = SRMap.groupBy { case ((node1, _), _) => node1 }.map {
      case (node, entries) =>
        val entryList = entries.map { case ((_, node2), value) => (node2, value) }.toList
        (node, entryList)
    }
    addToDTLMap(SROut)
    SROut
  }

  //to find best match when a node has more than 1 matches with >0.0 score
  def findBestNodeMatch(NGNode: NodeObject, PGNodeMatches: List[(NodeObject, Float)]): (NodeObject, Float) = {
    for (matchpair <- PGNodeMatches) {
      if NGNode == matchpair._1 then return matchpair
    }

    val scores = PGNodeMatches.foldLeft(mutable.Map[NodeObject, Int]())((acc, ele) => acc + (ele._1 -> 0))

    PGNodeMatches.foreach((PGNode, _) => {
      if NGNode.children == PGNode.children then scores(PGNode) += 1
      if NGNode.props == PGNode.props then scores(PGNode) += 1
      if NGNode.maxDepth == PGNode.maxDepth then scores(PGNode) += 1
      if NGNode.maxProperties == PGNode.maxProperties then scores(PGNode) += 1
    }
    )
    println(scores)
    PGNodeMatches.find((n, _) => n == scores.toList.sortBy(-_._2).head._1) match
      case Some(matchpair) => matchpair
      case None => throw new Exception("best score element not found in Perturbed SubGraph Nodes!")
  }

  //to create a yaml in local fs
  def createYamlFileInLocal(dir: String, yamlFileName: String): Unit = {
    val yamlFilePath = dir.concat(yamlFileName)
    val yamlFile = new File(yamlFilePath)

    if (!yamlFile.exists) {
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
      println(s"Yaml File $yamlFileName already exists!")
    }
  }

  //create a yaml in hdfs file system
  def createYamlFileInHDFS(dir: String, yamlFileName: String): Unit = {
    val yamlFilePath = dir.concat(yamlFileName)
    //    val yamlFile = new File(yamlFilePath)
    val conf = new org.apache.hadoop.conf.Configuration()
    val fs = FileSystem.get(new URI(hadoopFS), conf)
    val hdfsFilePath = new Path(yamlFilePath)

    if (!fs.exists(hdfsFilePath)) {
      val allNodesMod: String = (List("Nodes:\n") ::: List("\tModified: [") ::: List(List.empty[Int].mkString(", ")) ::: List("]\n")
        ::: List("\tRemoved: [") ::: List(List.empty[Int].mkString(", ")) ::: List("]\n") ::: List("\tAdded: [") ::: List(List.empty[Int].mkString(", ")) ::: List("]\n")).mkString
      val allEdgesMod: String = (List("Edges:\n") ::: List("\tModified:\n") ::: List(s"\t\t\n") ::: List("\tRemoved:\n") ::: List(s"\t\t\n") ::: List("\tAdded:\n") ::: List(s"\t\t\n")).mkString

      val data = allNodesMod.mkString.concat(allEdgesMod.mkString)

      val outputStream = fs.create(hdfsFilePath)
      val writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(outputStream))
      //      val fh = new PrintWriter(new File(yamlFilePath))
      try {
        //        fh.write(data)
        writer.write(data)
        println(s"YAML file created at: $yamlFilePath")
      } finally {
        //        fh.close()
        writer.close()
        outputStream.close()
      }
    }
    else {
      println(s"Yaml File $yamlFileName already exists!")
    }
  }

  //to replace tabs in local yaml file
  def replaceTabsWithSpacesInLocalFS(fileStr: String): Unit = {
    val source = Source.fromFile(fileStr)
    val lines = try source.getLines().map(_.replace("\t", "    ")).mkString("\n") finally source.close()

    val writer = new PrintWriter(new File(fileStr))
    try writer.write(lines) finally writer.close()
  }

  //to replace tabs in local hdfs yaml file
  def replaceTabsWithSpacesInHDFS(fileStr: String): Unit = {
    val conf = new org.apache.hadoop.conf.Configuration()
    val fs = FileSystem.get(new URI(hadoopFS), conf)
    val hdfsFilePath = new Path(fileStr)

    if (fs.exists(hdfsFilePath)) {
      val source = Source.fromInputStream(fs.open(hdfsFilePath))
      val lines = try source.getLines().map(_.replace("\t", "    ")).mkString("\n") finally source.close()

      val outputStream = fs.create(hdfsFilePath)
      val writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(outputStream))

      try {
        writer.write(lines)
        logger.info(s"Tabs replaced with spaces in file and saved to: $hdfsFilePath")
      } finally {
        writer.close()
      }
    }
  }

  //to append data to a section of yaml
  //section- "Nodes", "Edges". only these 2 choices allowed
  //subsection- "Modified", "Added", "Removed"- only these 3 choices allowed
  def appendToYamlSubsection(dir: String, yamlFileName: String, section: String, subsection: String, newData: List[String]): Unit = {
    val yamlFilePath = dir.concat(yamlFileName)
    val existingContent: String = {
      if hadoopFS == "local" then
        val yamlFile = new File(yamlFilePath)
        if !yamlFile.exists then
          println(s"YAML File $yamlFileName does not exist.")
          val x = "null"
          x
        else
          replaceTabsWithSpacesInLocalFS(yamlFilePath)
          val source = Source.fromFile(yamlFilePath)
          val content = source.mkString
          source.close()
          content
      else if hadoopFS.contains("hdfs://") then
        val conf = new org.apache.hadoop.conf.Configuration()
        val fs = FileSystem.get(new URI(hadoopFS), conf)
        val hdfsFilePath = new Path(yamlFilePath)
        if !fs.exists(hdfsFilePath) then
          logger.info(s"YAML File $yamlFileName does not exist.")
          "null"
        else
          replaceTabsWithSpacesInHDFS(yamlFilePath)
          val source = Source.fromInputStream(fs.open(hdfsFilePath))
          val content = source.mkString
          source.close()
          content
      else
        "null"
     }

    if existingContent != null then
      val yaml = new Yaml()

      //parse yaml into desired data structure
      val data: java.util.Map[String, java.util.Map[String, java.util.List[Int] | java.util.Map[Int, Int]]] = yaml.load(existingContent)

      val modifiedNodesList: List[String] = data.asScala("Nodes").asScala("Modified") match
        case e: java.util.List[Integer] =>
          //          print(s"elements:$e")
          if section == "Nodes" && subsection == "Modified" then
            (e.asScala.map(ele => String.valueOf(ele)).toList ::: newData).distinct
          else if !e.isEmpty then
            e.asScala.map(ele => String.valueOf(ele)).toList
          else
            List.empty[String]
        //throw new Exception(s"data from yaml: $yamlFileName should be convertible into List[Int] by yaml parser!")
        case _ =>
          logger.warn("Yaml File: Section: Nodes->Modified-> is probably empty. It should contain List[Integer] to be parsed correctly by yaml parser.")
          println("Yaml Nodes->Modified is probably empty")
          if newData.isEmpty then
            immutable.List.empty[String]
          else
            newData.distinct

      val removedNodesList: List[String] = data.asScala("Nodes").asScala("Removed") match
        case e: java.util.List[Integer] =>
          if section == "Nodes" && subsection == "Removed" then
            (e.asScala.toList.map(ele => String.valueOf(ele)) ::: newData).distinct
          else
            e.asScala.toList.map(ele => String.valueOf(ele)).distinct
        case _ =>
          println("Yaml Nodes->Removed was probably empty")
          if newData.isEmpty then
            immutable.List.empty[String]
          else
            newData.distinct

      val addedNodesList: List[String] = data.asScala("Nodes").asScala("Added") match
        case e: java.util.List[Integer] =>
          if section == "Nodes" && subsection == "Added" then
            (e.asScala.toList.map(ele => String.valueOf(ele)) ::: newData).distinct
          else
            e.asScala.toList.map(ele => String.valueOf(ele)).distinct
        case _ =>
          println("Yaml Nodes->Added was probably empty")
          if newData.isEmpty then
            immutable.List.empty[String]
          else
            newData.distinct

      val modifiedEdgesMap: immutable.Map[String, String] = data.asScala("Edges").asScala("Modified") match
        case e: java.util.Map[Integer, Integer] =>
          if section == "Edges" && subsection == "Modified" then
            e.asScala.map((u, v) => String.valueOf(u) -> String.valueOf(v)).toMap ++ newData.foldLeft(immutable.Map.empty[String, String]) { (acc, str) =>
              val items = str.split(":")
              acc + (items(0) -> items(1))
            }
          else
            e.asScala.map((u, v) => String.valueOf(u) -> String.valueOf(v)).toMap
        case _ =>
          if section == "Edges" && subsection == "Modified" then
            println("Yaml Edges->Modified was probably empty")
            if newData.isEmpty then
              immutable.Map.empty[String, String]
            else
              newData.foldLeft(immutable.Map.empty[String, String]) { (acc, str) =>
                val items = str.split(":")
                acc + (items(0) -> items(1))
              }
          else
            immutable.Map.empty[String, String]

      //throw new Exception(s"data from yaml: $yamlFileName should be convertible into Map[Int,Int] by yaml parser!")

      val removedEdgesMap: immutable.Map[String, String] = data.asScala("Edges").asScala("Removed") match
        case e: java.util.Map[Integer, Integer] =>
          if section == "Edges" && subsection == "Removed" then
            e.asScala.map((u, v) => String.valueOf(u) -> String.valueOf(v)).toMap ++ newData.foldLeft(immutable.Map.empty[String, String]) { (acc, str) =>
              val items = str.split(":")
              acc + (items(0) -> items(1))
            }
          else
            e.asScala.map((u, v) => String.valueOf(u) -> String.valueOf(v)).toMap
        //case _ => throw new Exception(s"data from yaml: $yamlFileName should be convertible into Map[Int,Int] by yaml parser!")
        case _ =>
          if section == "Edges" && subsection == "Removed" then
            println("Yaml Edges->Removed is probably empty")
            if newData.isEmpty then
              immutable.Map.empty[String, String]
            else
              newData.foldLeft(immutable.Map.empty[String, String]) { (acc, str) =>
                val items = str.split(":")
                acc + (items(0) -> items(1))
              }
          else
            immutable.Map.empty[String, String]

      val addedEdgesMap: immutable.Map[String, String] = data.asScala("Edges").asScala("Added") match
        case e: java.util.Map[Integer, Integer] =>
          if section == "Edges" && subsection == "Added" then
            e.asScala.map((u, v) => String.valueOf(u) -> String.valueOf(v)).toMap ++ newData.foldLeft(immutable.Map.empty[String, String]) { (acc, str) =>
              val items = str.split(":")
              acc + (items(0) -> items(1))
            }
          else
            e.asScala.map((u, v) => String.valueOf(u) -> String.valueOf(v)).toMap
        //case _ => throw new Exception(s"data from yaml: $yamlFileName should be convertible into Map[Int,Int] by yaml parser!")
        case _ =>
          if section == "Edges" && subsection == "Added" then
            println("Yaml Edges->Added is probably empty")
            if newData.isEmpty then
              immutable.Map.empty[String, String]
            else
              newData.foldLeft(immutable.Map.empty[String, String]) { (acc, str) =>
                val items = str.split(":")
                acc + (items(0) -> items(1))
              }
          else
            immutable.Map.empty[String, String]

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

      if hadoopFS == "local" then
        val dataToWrite: String = allNodesMod.mkString.concat(allEdgesMod.mkString)

        val fh = new PrintWriter(new File(yamlFilePath))
        try {
          fh.write(dataToWrite)
          println(s"YAML file modified at: $yamlFilePath")
        } finally {
          fh.close()
        }
      else
        val conf = new org.apache.hadoop.conf.Configuration()
        val fs = FileSystem.get(new URI(hadoopFS), conf)
        val outputStream = fs.create(new Path(yamlFilePath))
        val writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(outputStream))
        try {
          writer.write(dataToWrite)
          println(s"YAML file modified at: $yamlFilePath")
        } finally {
          writer.close()
          outputStream.close()
        }
  }

  //read reducer output file of the map-reduce1 job in hdfs fs. It is also used insider mapper 1 to get the nodes matching information.
  def readNodeMatchHDFSFile(reducerOutputFilePath: String): immutable.Map[NodeObject, NodeObject] = {

    val conf = new org.apache.hadoop.conf.Configuration()
//    val fs = FileSystem.get(conf)
    val fs = {
      if hadoopFS.contains("hdfs://localhost") || hadoopFS.contains("s3://") then
        FileSystem.get(new URI(hadoopFS), conf)
      else
        FileSystem.get(conf)
    }

    val inputStream = fs.open(new Path(reducerOutputFilePath))
    val reader = new BufferedReader(new InputStreamReader(inputStream))

    val matchMap = mutable.Map[NodeObject, NodeObject]()
    try {
      var line: String = null
      while ( {
        line = reader.readLine()
        line != null
      }) {
        val nodePairStr = line.split("\t")
        val nodeU = stringToNodeObject(nodePairStr(0))
        val pattern = """NodeObject\([-?[0-9]*\.?[0-9]*,]+\)"""
        val nodeV = stringToNodeObject(pattern.r.findFirstIn(nodePairStr(1)).get)

        //println(s"nodeU: $nodeU -- nodeV: $nodeV")
        matchMap += nodeU -> nodeV
      }
    } finally {
      reader.close()
      inputStream.close()
    }

    val finalMatchMap = mutable.Map[NodeObject, NodeObject]()
    for (nodeMatch <- matchMap.filterNot(ele => ele._2.id == -1)) {
      val nodeU = nodeMatch._1
      val nodeV = nodeMatch._2
      if (nodeU == nodeV) || !(matchMap.contains(nodeV) && matchMap(nodeV) == nodeV) then
        finalMatchMap += nodeU -> nodeV
    }
    println("FinalMatchMap:")
    println(finalMatchMap)
    finalMatchMap.toMap
  }

  case class NodePerturbationInfo(modifiedNodesMap: immutable.Map[NodeObject, NodeObject], removedNodes: List[NodeObject], addedNodes: List[NodeObject], unperturbedNodesMap: immutable.Map[NodeObject, NodeObject])

  // categorize matched nodes into added, removed, modified post map-reduce1 job
  def decipherNodes(reducer1OutputFilePath: String, NG: NetGraph, PG: NetGraph, newYamlFileDir: String, newYamlFileName: String): NodePerturbationInfo = {

    val conf = new org.apache.hadoop.conf.Configuration()
//    val fs = FileSystem.get(conf)
    val fs = {
      if hadoopFS.contains("hdfs://localhost") || hadoopFS.contains("s3://") then
        FileSystem.get(new URI(hadoopFS), conf)
      else
        FileSystem.get(conf)
    }

    val inputStream = fs.open(new Path(reducer1OutputFilePath))
    val reader = new BufferedReader(new InputStreamReader(inputStream))

    val matchMap = mutable.Map[NodeObject, NodeObject]()
    try {
      var line: String = null
      while ( {
        line = reader.readLine()
        line != null
      }) {
        val nodePairStr = line.split("\t")
        val nodeU = stringToNodeObject(nodePairStr(0))
        val pattern = """NodeObject\([-?[0-9]*\.?[0-9]*,]+\)"""
        val nodeV = stringToNodeObject(pattern.r.findFirstIn(nodePairStr(1)).get)

        //println(s"nodeU: $nodeU -- nodeV: $nodeV")
        matchMap += nodeU -> nodeV
      }
    } finally {
      reader.close()
      inputStream.close()
    }

    val removedNodes = ListBuffer[NodeObject]()
    val modifiedNodesMap = mutable.Map[NodeObject, NodeObject]()
    val addedNodes = ListBuffer[NodeObject]()
    val unperturbedNodesMap = mutable.Map[NodeObject, NodeObject]()

/*    for (nodeMatch <- matchMap) {
      val nodeU = nodeMatch._1
      val nodeV = nodeMatch._2
      if nodeU == nodeV then
        unperturbedNodesMap += nodeU -> nodeV
      else if nodeV.id == -1 then
        removedNodes += nodeU
      else if nodeU.id == nodeV.id then
        modifiedNodesMap += nodeU -> nodeV
      else if matchMap.contains(nodeV) && matchMap(nodeV).id == nodeV.id then
        removedNodes += nodeU
      else
        modifiedNodesMap += nodeU -> nodeV
    }*/
    for (nodeMatch <- matchMap) {
      val nodeU = nodeMatch._1
      val nodeV = nodeMatch._2
      if(nodeU.id==191) then
        println("debug here")
      if nodeU == nodeV then
        unperturbedNodesMap += nodeU -> nodeV
      else if nodeV.id == -1 || (matchMap.contains(nodeV) && matchMap(nodeV) == nodeV) then
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
    val updatedModifiedNodesMap = modifiedNodesMap ++ removedAndAddedMap

    val updatedRemovedNodesStrList: List[String] = updatedRemovedNodes.map(node => node.id.toString)
    val updatedAddedNodesStrList: List[String] = updatedAddedNodes.map(node => node.id.toString)
    val updatedModifiedNodesStrList: List[String] = updatedModifiedNodesMap.map((u, _) => u.id.toString).toList

/*    val RemovedNodesStrList: List[String] = removedNodes.map(node => node.id.toString).toList
    val AddedNodesStrList: List[String] = addedNodes.map(node => node.id.toString).toList
    val ModifiedNodesStrList: List[String] = modifiedNodesMap.map((u, _) => u.id.toString).toList */

    if hadoopFS == "local" then
      createYamlFileInLocal(dir = newYamlFileDir, yamlFileName = newYamlFileName)
    else
      createYamlFileInHDFS(dir = newYamlFileDir, yamlFileName = newYamlFileName)

    appendToYamlSubsection(dir = newYamlFileDir, yamlFileName = newYamlFileName, section = "Nodes", subsection = "Removed", newData = updatedRemovedNodesStrList)
    appendToYamlSubsection(dir = newYamlFileDir, yamlFileName = newYamlFileName, section = "Nodes", subsection = "Added", newData = updatedAddedNodesStrList)
    appendToYamlSubsection(dir = newYamlFileDir, yamlFileName = newYamlFileName, section = "Nodes", subsection = "Modified", newData = updatedModifiedNodesStrList)

    val removedEdges = removedNodes.foldLeft(List.empty[(NodeObject, NodeObject)])((acc, rn) => {
      val associatedEdges = NG.sm.incidentEdges(rn).asScala.toList
      acc ++ associatedEdges.map(ae => (ae.source(), ae.target()))
    }).map((u, v) => u.id.toString.concat(":").concat(v.id.toString)).toList

    appendToYamlSubsection(dir = newYamlFileDir, yamlFileName = newYamlFileName, section = "Edges", subsection = "Removed", newData = removedEdges)

    val addedEdges = addedNodes.foldLeft(List.empty[(NodeObject, NodeObject)])((acc, rn) => {
      val associatedEdges = PG.sm.incidentEdges(rn).asScala.toList
      acc ++ associatedEdges.map(ae => (ae.source(), ae.target()))
    }).map((u, v) => u.id.toString.concat(":").concat(v.id.toString)).toList

    appendToYamlSubsection(dir = newYamlFileDir, yamlFileName = newYamlFileName, section = "Edges", subsection = "Added", newData = addedEdges)

    NodePerturbationInfo(modifiedNodesMap.toMap, removedNodes.toList, addedNodes.toList, unperturbedNodesMap.toMap)
  }

  //to create mapper2 input txt file
  def serializeEdgeInfo(edgesInfoTextFileDir:String, edgesInfoTextFileName: String, nodePerturbationObj: NodePerturbationInfo, NG: NetGraph, PG: NetGraph) = {
   /* val edgesInfoTextFileDir: String = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/"
    val edgesInfoTextFileName: String = "edgeInfo"
*/
    val conf = new org.apache.hadoop.conf.Configuration()
    val fs = {
      if hadoopFS.contains("hdfs://localhost") || hadoopFS.contains("s3://")then
        FileSystem.get(new URI(hadoopFS), conf)
      else
        FileSystem.get(conf)
    }
    val fullPath = new Path(s"$edgesInfoTextFileDir$edgesInfoTextFileName")
    val outputStream = fs.create(fullPath)
    val writer = new BufferedWriter(new OutputStreamWriter(outputStream))
//    val writer = new BufferedWriter(new FileWriter(s"$edgesInfoTextFileDir$edgesInfoTextFileName"))
    val NGraphPGraphDelimiter = ";"

    val otherNodesMap = nodePerturbationObj.modifiedNodesMap ++ nodePerturbationObj.unperturbedNodesMap

    for (nodeMatch <- otherNodesMap) {
      val nodeU = nodeMatch._1
      val nodeV = nodeMatch._2
      val nodeUEdgesInfo = NG.sm.incidentEdges(nodeU).asScala.toList.map(ep => (ep, NG.sm.edgeValue(ep.source(), ep.target()).get().cost)).filterNot(ele => nodePerturbationObj.removedNodes.contains(ele._1.source()) || nodePerturbationObj.removedNodes.contains(ele._1.target()))
      val nodeVEdgesInfo = PG.sm.incidentEdges(nodeV).asScala.toList.map(ep => (ep, PG.sm.edgeValue(ep.source(), ep.target()).get().cost)).filterNot(ele => nodePerturbationObj.addedNodes.contains(ele._1.source()) || nodePerturbationObj.addedNodes.contains(ele._1.target()))

      val oneCombinedString = nodeUEdgesInfo.toString() + NGraphPGraphDelimiter + nodeVEdgesInfo.toString()

      writer.write(oneCombinedString)
      writer.newLine()
    }
    writer.close()
    outputStream.close()
    println("Edges Info written successfully!")
  }

  case class EdgesShard(allNEdgesInfo: List[((NodeObject, NodeObject), Double)], allPEdgesInfo: List[((NodeObject, NodeObject), Double)])

  //to deserialize mapper2 input inside map-reduce2 job
  def deserializeEdgeInfo(shard_string: String): EdgesShard = {

    val shardStrSplit: Array[String] = shard_string.split(';')

    val regex = """\(\<NodeObject\([-?[0-9]*\.?[0-9]*,]+\)\s?->\s?NodeObject\([-?[0-9]*\.?[0-9]*,]+\)\>,\s?[\d.]+\)""".r
    // Find all matches in the input string
    val nodeUEdgesStr = regex.findAllMatchIn(shardStrSplit(0)).toList
    val nodeVEdgesStr = regex.findAllMatchIn(shardStrSplit(1)).toList

    val nodeUEdgesInfo = ListBuffer[((NodeObject, NodeObject), Double)]()
    val nodeVEdgesInfo = ListBuffer[((NodeObject, NodeObject), Double)]()

    val nodeRegex = """NodeObject\([-?[0-9]*\.?[0-9]*,]+\)""".r

    for (nodeUEdgeStr <- nodeUEdgesStr) {
      // Apply the regular expression to the input string
      val nodesArr = """NodeObject\([-?[0-9]*\.?[0-9]*,]+\)\s?->\s?NodeObject\([-?[0-9]*\.?[0-9]*,]+\)""".r.findFirstIn(nodeUEdgeStr.toString()).get.split("->")
      val nodesTuple = (stringToNodeObject(nodesArr(0)), stringToNodeObject(nodesArr(1)))
      if nodesTuple._1.id == 191 || nodesTuple._2.id == 191 then
        println("debug here")
        println(shard_string)
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

  // read output file of map-reduce2 job for hdfs
  def readEdgeInfoHDFSFile(reducerOutputFilePath: String): immutable.Map[String, List[String]] = {
    val conf = new org.apache.hadoop.conf.Configuration()
    val fs = {
        if hadoopFS.contains("hdfs://localhost") || hadoopFS.contains("s3://") then
          FileSystem.get(new URI(hadoopFS), conf)
        else
          FileSystem.get(conf)
    }
    val inputStream = fs.open(new Path(reducerOutputFilePath))
    val reader = new BufferedReader(new InputStreamReader(inputStream))

    val edgeSectionWiseInfoMap = mutable.Map[String, List[String]]()
    try {
      var line: String = null
      while ( {
        line = reader.readLine()
        line != null
      }) {
        val edgesInfo = line.split("\t")
        val sectionName = edgesInfo(0)
        val edgeIdPairs = edgesInfo(1).substring(10, edgesInfo(1).length - 2).split(',').toList.map(str => str.trim())
        //println(s"nodeU: $nodeU -- nodeV: $nodeV")
        edgeSectionWiseInfoMap += sectionName -> edgeIdPairs
      }
    } finally {
      reader.close()
      inputStream.close()
    }

    edgeSectionWiseInfoMap.toMap
  }

  def addToDTLMap(SRout: immutable.Map[NodeObject, List[(NodeObject, Float)]]): Unit = {

    val updatedSRout = SRout.map(elem => elem._1 -> elem._2.filterNot(matchPair => matchPair._2 == 0.0))

    updatedSRout.foreach(elem => {
      if DTLMap.contains(elem._1) then
        DTLMap(elem._1) = (DTLMap(elem._1) ::: elem._2).distinct
      else
        DTLMap(elem._1) = elem._2
    }
    )

  }

  //to update DTLMap and ATL Map- remove all TLs whose score < 0.0
  def updateDTLMap(): Unit = {

    DTLMap.keys.foreach(keyNode => {
      val intermediateResult = DTLMap(keyNode).filterNot(ele => ele._2 == 0.0).sortBy(-_._2)
      if intermediateResult.length < 1 then
        println("empty intermediateresult !")

      else
        val result1 = intermediateResult.filter(ele => ele._2 == intermediateResult.head._2)

        if result1.length == 1 then
          ATLMap(keyNode) = result1.head
          DTLMap(keyNode) = intermediateResult.filterNot(pair => pair == ATLMap(keyNode))
        else if result1.length > 1 then
          ATLMap(keyNode) = findBestNodeMatch(keyNode, result1)
          DTLMap(keyNode) = intermediateResult.filterNot(pair => pair == ATLMap(keyNode))
        else if (result1.length > 1)
          DTLMap(keyNode) = intermediateResult

    })
    val emptyDTLKeys = DTLMap.keys.filter(keyNode => DTLMap(keyNode).isEmpty)
    emptyDTLKeys.foreach(keyNode => DTLMap.remove(keyNode))
  }

  def main(args: Array[String]): Unit = {
    
    //load net graph
    val netGraph = originalGraph.getOrElse({
      logger.error(s"Couldn't load original graph File at $NGSGraphDir$originalGraphFileName")
      throw new Exception("Couldn't load original graph File!")
    })
    
    //load perturbed graph
    val pertGraph = perturbedGraph.getOrElse({
      logger.error(s"Couldn't load perturbed graph File at $NGSGraphDir$perturbedGraphFileName")
      throw new Exception("Couldn't load perturbed graph File!")
    })

    val numOfnetGraphNodes: Int = netGraph.sm.nodes().asScala.toList.length
    val numOfperturbedGraphNodes: Int = pertGraph.sm.nodes().asScala.toList.length

    val numOfParts:Int = {
      if numOfnetGraphNodes <= 1 || numOfperturbedGraphNodes <= 1 then 1
      else if numOfnetGraphNodes <= 20 || numOfperturbedGraphNodes <= 20 then 2
      else if numOfnetGraphNodes <= 50 || numOfperturbedGraphNodes <= 50 then 5
      else 10
    }

    // create subgraphs
      //println("Making net subgraphs.....\n")
      logger.info("Creating net subgraphs.....\n")
      val NGSubgraphs = cutGraph(netGraph, numOfParts)
      logger.info("Creating perturbed subgraphs.....\n")
      val PGSubgraphs = cutGraph(pertGraph, numOfParts)

      //println("Net subgraphs:")
      logger.info("All Net subgraphs are as follows:")
      NGSubgraphs.foreach(NGsg => logger.info(s"Net SubGraph:${NGsg.sm.nodes().asScala.toList.map(n => n.id).toString}"))
      logger.info("All Perturbed subgraphs are as follows::")
      PGSubgraphs.foreach(PGsg => logger.info(s"Perturbed SubGraph:${PGsg.sm.nodes().asScala.toList.map(n => n.id).toString}"))

    
      //serialize subgraphs for map-reduce job1 input. Will save the file in mapReduce1Dir
      logger.info("Serializing SubGraphs...:")
      serializeShardsAsStrings(NGSubgraphs, PGSubgraphs, dir=mapReduce1Dir, fileName=mapReduce1InputFileName)
      logger.info(s"Subgraphs File saved successfully at! $mapReduce1Dir/$mapReduce1InputFileName")

  }
}



