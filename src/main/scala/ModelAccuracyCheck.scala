import NetGraphAlgebraDefs.{NetGraph, NodeObject}
import com.lsc.HelperFunctions.replaceTabsWithSpacesInLocalFS
import org.apache.hadoop.fs.{FileSystem, Path}

import org.yaml.snakeyaml.{Yaml, DumperOptions}
import java.io.{File, FileWriter}
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets

import java.net.URI
//import Utilz.CreateLogger
import LoggingUtil.GraphUtil.CreateLogger
import com.lsc.HelperFunctions.{ATLMap, DTLMap, PerturbedEdges, PerturbedNodes, replaceTabsWithSpacesInHDFS, stringToNodeObject, updateDTLMap, logger}
import Main.{originalGraph,perturbedGraph}

import scala.collection.mutable.{ListBuffer, Map}
import scala.collection.immutable
import scala.io.Source
import java.io.{File, PrintWriter}

import scala.jdk.CollectionConverters.*
import Variables.GraphConfigReader.*
import org.slf4j.Logger

object ModelAccuracyCheck {
  //val logger: Logger = CreateLogger(classOf[ModelAccuracyCheck.type])

  //to read yaml file content for local yaml
  def getYamlFileContentFromLocal(yamlDir: String, yamlFileName: String): String = {
    val yamlFilePath = yamlDir.concat(yamlFileName)
    val yamlFile = new File(yamlFilePath)

    if (!yamlFile.exists) {
      logger.info(s"No such Yaml File exists: $yamlDir$yamlFileName!\n")
      throw new Exception(s"YAML File $yamlFileName does not exist.")
    }
    else {
      //replace tab in yaml with spaces
      replaceTabsWithSpacesInLocalFS(yamlFilePath)

      //load existing yaml file content as string
      val source = Source.fromFile(yamlFilePath)
      val existingContent = source.mkString
      source.close()

      existingContent
    }
  }

  //to read yaml file content for hdfs yaml
  def getYamlFileContentInHDFS(yamlDir: String, yamlFileName: String): String = {
    val yamlFilePath = yamlDir.concat(yamlFileName)
    val conf = new org.apache.hadoop.conf.Configuration()
    val fs = FileSystem.get(new URI(hadoopFS), conf)
    val hdfsFilePath = new Path(yamlFilePath)
    replaceTabsWithSpacesInHDFS(yamlFilePath)
    val source = Source.fromInputStream(fs.open(hdfsFilePath))
    val content = source.mkString
    source.close()
    content
  }


  //to calculate prediction statisitcs and store them in log file
  def calculateModelAccuracy(origPN: PerturbedNodes, origPE: PerturbedEdges, predPN: PerturbedNodes, predPE: PerturbedEdges):  immutable.Map[String, immutable.Map[String, Double]] = {
    logger.info("Starting statistical calculations:\n")
    //origUnPN, origUnPE, predUnPN, predUnPE
    val origUnPN = originalGraph.get.sm.nodes().asScala.toList.map(n => n.id).diff(origPN.modifiedNodeIds).diff(origPN.removedNodeIds).map(nid => (nid, nid))
    val origUnPE = originalGraph.get.sm.edges.asScala.toList.map(ep => ep.source().id -> ep.target().id).diff(origPE.modifiedEdgeIds.toList).diff(origPE.addedEdgeIds.toList).diff(origPE.removedEdgeIds.toList).toMap
    val predUnPN = originalGraph.get.sm.nodes().asScala.toList.map(n => n.id).diff(predPN.modifiedNodeIds).diff(predPN.removedNodeIds).map(nid => (nid, nid))
    val predUnPE = originalGraph.get.sm.edges.asScala.toList.map(ep => ep.source().id -> ep.target().id).diff(predPE.modifiedEdgeIds.toList).diff(predPE.addedEdgeIds.toList).diff(predPE.removedEdgeIds.toList).toMap

    //DEFINITIONS and their comprehensions
    /*RTL = BTL+ GTL
      /*BTL = CTL + WTL
        //CTL(false neg) = number of correct TLs that are mistakenly discarded by your algorithm
        //WTL(false pos) = number of wrong TLs that the your algorithm accepts
      GTL= ATL + DTL
          //ATL(true pos) = number of correct TLs that are correctly accepted by your algorithm
          //DTL(true neg) = number of wrong TLs that are correctly discarded by your algorithm
    Quality measures
      //ACC = ATL / RTL
      //BLTR = WTL / RTL
      //VPR =  (GTL - BTL)/(2×RTL) + 0.5
    */
    */

    val ModifiedNodesCTL = origPN.modifiedNodeIds.diff(predPN.modifiedNodeIds).length
    val ModifiedNodesATL = origPN.modifiedNodeIds.intersect(predPN.modifiedNodeIds).length
    val ModifiedNodesWTL = predPN.modifiedNodeIds.diff(origPN.modifiedNodeIds).length
//    println(s"ModifiedNodesCTL: $ModifiedNodesCTL\nModifiedNodesATL: $ModifiedNodesATL\nModifiedNodesWTL: $ModifiedNodesWTL\n")
    logger.info(s"ModifiedNodesCTL: $ModifiedNodesCTL  ModifiedNodesATL: $ModifiedNodesATL  ModifiedNodesWTL: $ModifiedNodesWTL\n")

    val AddedNodesCTL = origPN.addedNodeIds.diff(predPN.addedNodeIds).length
    val AddedNodesATL = origPN.addedNodeIds.intersect(predPN.addedNodeIds).length
    val AddedNodesWTL = predPN.addedNodeIds.diff(origPN.addedNodeIds).length
//    println(s"AddedNodesCTL: $AddedNodesCTL\nAddedNodesATL: $AddedNodesATL\nAddedNodesWTL: $AddedNodesWTL\n")
    logger.info(s"AddedNodesCTL: $AddedNodesCTL  AddedNodesATL: $AddedNodesATL  AddedNodesWTL: $AddedNodesWTL\n")

    val RemovedNodesCTL = origPN.removedNodeIds.diff(predPN.removedNodeIds).length
    val RemovedNodesATL = origPN.removedNodeIds.intersect(predPN.removedNodeIds).length
    val RemovedNodesWTL = predPN.removedNodeIds.diff(origPN.removedNodeIds).length
//    println(s"RemovedNodesCTL: $RemovedNodesCTL\nRemovedNodesATL: $RemovedNodesATL\nRemovedNodesWTL: $RemovedNodesWTL\n")
    logger.info(s"RemovedNodesCTL: $RemovedNodesCTL  RemovedNodesATL: $RemovedNodesATL  RemovedNodesWTL: $RemovedNodesWTL\n")

    val unPerturbedNodesCTL = origUnPN.diff(predUnPN).length
    val unPerturbedNodesATL = origUnPN.intersect(predUnPN).length
    val unPerturbedNodesWTL = predUnPN.diff(origUnPN).length
    logger.info(s"unPerturbedNodesCTL: $unPerturbedNodesCTL  unPerturbedNodesATL: $unPerturbedNodesATL  unPerturbedNodesWTL: $unPerturbedNodesWTL\n")

    val ModifiedEdgesCTL = origPE.modifiedEdgeIds.toList.diff(predPE.modifiedEdgeIds.toList).length
    val ModifiedEdgesATL = origPE.modifiedEdgeIds.toList.intersect(predPE.modifiedEdgeIds.toList).length
    val ModifiedEdgesWTL = predPE.modifiedEdgeIds.toList.diff(origPE.modifiedEdgeIds.toList).length
//    println(s"ModifiedEdgesCTL: $ModifiedEdgesCTL\nModifiedEdgesATL: $ModifiedEdgesATL\nModifiedEdgesWTL: $ModifiedEdgesWTL\n")
    logger.info(s"ModifiedEdgesCTL: $ModifiedEdgesCTL  ModifiedEdgesATL: $ModifiedEdgesATL  ModifiedEdgesWTL: $ModifiedEdgesWTL\n")

    val AddedEdgesCTL = origPE.addedEdgeIds.toList.diff(predPE.addedEdgeIds.toList).length
    val AddedEdgesATL = origPE.addedEdgeIds.toList.intersect(predPE.addedEdgeIds.toList).length
    val AddedEdgesWTL = predPE.addedEdgeIds.toList.diff(origPE.addedEdgeIds.toList).length
    //println(s"AddedEdgesCTL: $AddedEdgesCTL\nAddedEdgesATL: $AddedEdgesATL\nAddedEdgesWTL: $AddedEdgesWTL\n")
    logger.info(s"AddedEdgesCTL: $AddedEdgesCTL  AddedEdgesATL: $AddedEdgesATL  AddedEdgesWTL: $AddedEdgesWTL  ")

    val RemovedEdgesCTL = origPE.removedEdgeIds.toList.diff(predPE.removedEdgeIds.toList).length
    val RemovedEdgesATL = origPE.removedEdgeIds.toList.intersect(predPE.removedEdgeIds.toList).length
    val RemovedEdgesWTL = predPE.removedEdgeIds.toList.diff(origPE.removedEdgeIds.toList).length
//    println(s"RemovedEdgesCTL: $RemovedEdgesCTL\nRemovedEdgesATL: $RemovedEdgesATL\nRemovedEdgesWTL: $RemovedEdgesWTL\n")
    logger.info(s"RemovedEdgesCTL: $RemovedEdgesCTL  RemovedEdgesATL: $RemovedEdgesATL  RemovedEdgesWTL: $RemovedEdgesWTL  ")

    val unPerturbedEdgesCTL = origUnPE.toList.diff(predUnPE.toList).length
    val unPerturbedEdgesATL = origUnPE.toList.intersect(predUnPE.toList).length
    val unPerturbedEdgesWTL = predUnPE.toList.diff(origUnPE.toList).length

    val total_TLs = ListBuffer[(Int, Int)]()
    originalGraph.get.sm.nodes().asScala.toList.map(n1 => n1.id).foreach(uid => perturbedGraph.get.sm.nodes().asScala.toList.map(n2 => n2.id).foreach(vid => total_TLs += ((uid, vid))))

    //val NodesDTL = total_TLs.toList.diff(origUnPN).diff(origPN.modifiedNodeIds).intersect(total_TLs.toList.diff(predUnPN).diff(predPN.modifiedNodeIds)).length
    //val EdgesDTL: Int = ???

    val NodesDTL = DTLMap.foldLeft(0)((acc, ele) => acc + ele._2.distinct.length)
    val NodesATL = ModifiedNodesATL + AddedNodesATL + RemovedNodesATL + unPerturbedNodesATL
    val NodesCTL = ModifiedNodesCTL + AddedNodesCTL + RemovedNodesCTL + unPerturbedNodesCTL
    val NodesWTL = ModifiedNodesWTL + AddedNodesWTL + RemovedNodesWTL + unPerturbedNodesWTL

    val NodesGTL = NodesATL + NodesDTL
    val NodesBTL = NodesCTL + NodesWTL

    val NodesRTL = NodesGTL + NodesBTL
//    println(s"\nNodes ATL: $NodesATL\nNodes DTL: $NodesDTL\nNodes CTL: $NodesCTL\nNodes WTL: $NodesWTL\n Nodes RTL: $NodesRTL")
    //println(s"\nNodes GTL+BTL: ${NodesGTL+NodesBTL}, RTL: ${origUnPN.length + origPN.removedNodeIds.length + origPN.addedNodeIds.length + origPN.removedNodeIds.length}")
    logger.info(s"Nodes GTL+BTL: ${NodesGTL+NodesBTL}\n")

    val NodesACC: Float = (ModifiedNodesATL + AddedNodesATL + RemovedNodesATL + unPerturbedNodesATL).toFloat / NodesRTL.toFloat
    val NodesBLTR: Float = (ModifiedNodesWTL + AddedNodesWTL + RemovedNodesWTL + unPerturbedNodesWTL).toFloat / NodesRTL.toFloat
    val NodesVPR: Float = ((NodesGTL - NodesBTL).toFloat / (2 * NodesRTL).toFloat) + 0.5f
//    println(s"\nNodesACC = $NodesACC, NodesBLTR = $NodesBLTR, NodesVPR= $NodesVPR")
    logger.info(s"NodesACC = $NodesACC,  NodesBLTR = $NodesBLTR,  NodesVPR= $NodesVPR\n")


//    val  EdgesDTL: Int = 0
    val EdgesATL: Int = ModifiedEdgesATL + AddedEdgesATL + RemovedEdgesATL + unPerturbedEdgesATL
    val EdgesCTL: Int = ModifiedEdgesCTL + AddedEdgesCTL + RemovedEdgesCTL + unPerturbedEdgesCTL
    val EdgesWTL: Int = ModifiedEdgesWTL + AddedEdgesWTL + RemovedEdgesWTL + unPerturbedEdgesWTL
    val EdgesGTL = EdgesATL
    val EdgesBTL = EdgesCTL + EdgesWTL
    val EdgesRTL = EdgesGTL + EdgesBTL
//    println(s"\nEdges ATL: $EdgesATL\nEdges DTL: 0\nEdges CTL: $EdgesCTL\nNodes WTL: $EdgesWTL\n Edges RTL: $EdgesRTL")
    logger.info(s"\nEdges ATL: $EdgesATL\nEdges DTL: 0\nEdges CTL: $EdgesCTL\nEdges WTL: $EdgesWTL\n Edges RTL: $EdgesRTL")
    //println(s"\nEdges GTL+BTL: ${EdgesGTL + EdgesBTL}")

    val EdgesACC: Float = (ModifiedEdgesATL + AddedEdgesATL + RemovedEdgesATL + unPerturbedEdgesATL).toFloat / EdgesRTL.toFloat
    val EdgesBLTR: Float = (ModifiedEdgesWTL + AddedEdgesWTL + RemovedEdgesWTL + unPerturbedEdgesWTL).toFloat / EdgesRTL.toFloat
    val EdgesVPR: Float = ((EdgesGTL - EdgesBTL).toFloat / (2 * EdgesRTL).toFloat) + 0.5f
//    println(s"\nEdgesACC = $EdgesACC, EdgesBLTR = $EdgesBLTR, EdgesVPR= $EdgesVPR")
    logger.info(s"\nEdgesACC = $EdgesACC, EdgesBLTR = $EdgesBLTR, EdgesVPR= $EdgesVPR")

    val OverallACC = (NodesACC + EdgesACC) / 2.0
    val OverallBLTR = (NodesBLTR + EdgesBLTR) / 2.0
    val OverallVPR = (NodesVPR + EdgesVPR) / 2.0
//    println(s"OverallACC = $OverallACC, OverallBLTR = $OverallBLTR, OverallVPR= $OverallVPR")
    logger.info(s"OverallACC = $OverallACC, OverallBLTR = $OverallBLTR, OverallVPR= $OverallVPR")

    val allScores: immutable.Map[String, immutable.Map[String, Double]] = immutable.ListMap("Nodes" -> immutable.Map("NodesATL" -> NodesATL, "NodesDTL" -> NodesDTL, "NodesCTL" -> NodesCTL, "NodesWTL" -> NodesWTL, "NodesACC"-> NodesACC, "NodesBLTR"->NodesBLTR, "NodesVPR" -> NodesVPR), "Edges" -> immutable.Map("EdgesATL" -> EdgesATL, "EdgesDTL" -> 0, "EdgesCTL" -> EdgesCTL, "EdgesWTL" -> EdgesWTL,"EdgesACC"-> EdgesACC, "EdgesBLTR"->EdgesBLTR, "NodesVPR" -> EdgesVPR), "OverAll"-> immutable.Map("OverallACC"->OverallACC, "OverallBLTR"->OverallBLTR,"OverallVPR"->OverallVPR))
    allScores
  }

  def writeYamlFile(data: immutable.Map[String, immutable.Map[String, Double]], dir: String, fileName: String): Unit = {

    // Function to convert Map to ListMap
    def convertToOrderedMap(input: immutable.Map[String, Any]): java.util.Map[String, Any] = {
      val result = new java.util.LinkedHashMap[String, Any]()
      input.foreach {
        case (key, value: immutable.Map[String, Any]) => result.put(key, convertToOrderedMap(value))
        case (key, value) => result.put(key, value)
      }
      result
    }

    val filePath = dir.concat(fileName)
    val options = new DumperOptions()
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
    val yaml = new Yaml(options)
    val fileWriter =
      if (hadoopFS == "local" || hadoopFS == "file:///") {
      new FileWriter(filePath)
    } else if (hadoopFS.startsWith("hdfs://") || hadoopFS.startsWith("s3://")) {
      val conf = new org.apache.hadoop.conf.Configuration()
      val fs = FileSystem.get(URI(hadoopFS),conf)
      val hdfsPath = new Path(filePath)
      val outputStream = fs.create(hdfsPath)
      new BufferedWriter(new java.io.OutputStreamWriter(outputStream, StandardCharsets.UTF_8))
    } else {
      throw new IllegalArgumentException("Unsupported HadoopFS parameter")
    }

    try {
      yaml.dump(convertToOrderedMap(data), fileWriter)
      println(s"Data written to $filePath")
    } finally {
      fileWriter.close()
    }
  }





}

