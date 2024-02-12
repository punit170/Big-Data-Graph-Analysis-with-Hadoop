import NetGraphAlgebraDefs.{Action, GraphPerturbationAlgebra, NetGraph, NetGraphComponent, NetModelAlgebra, NodeObject}
import com.lsc.HelperFunctions.*
import ModelAccuracyCheck.*
import MapReduceProgram.*
import org.yaml.snakeyaml.Yaml

//import Utilz.CreateLogger
import LoggingUtil.GraphUtil.CreateLogger
import org.apache.hadoop.fs.Path
import org.apache.hadoop.conf.*
import org.apache.hadoop.io.*
import org.apache.hadoop.util.*
import org.apache.hadoop.mapred.*

import java.io.IOException
import java.util
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, Map}
import scala.jdk.CollectionConverters.*
import com.lsc.HelperFunctions.{ATLMap, DTLMap, EdgesShard, NodePerturbationInfo, Shard, SimRankv_2, appendToYamlSubsection, createYamlFileInHDFS, createYamlFileInLocal, decipherNodes, deserializeEdgeInfo, deserializeMapperOutputValue, deserializeStringShard, findBestNodeMatch, readEdgeInfoHDFSFile, readNodeMatchHDFSFile, serializeEdgeInfo, serializeShardsAsStrings, stringToNodeObject, updateDTLMap, logger}
import org.apache.hadoop.io.*

import java.io.DataInput
import java.io.DataOutput
import scala.collection.immutable
import Variables.GraphConfigReader.*
import org.slf4j.Logger


object Main {
  lazy val originalGraph: Option[NetGraph] = load_graph(fileName = originalGraphFileName, dir = NGSGraphDir,  FS=hadoopFS)
  lazy val perturbedGraph: Option[NetGraph] = load_graph(fileName = perturbedGraphFileName, dir = NGSGraphDir, FS=hadoopFS)

  def main(args: Array[String]): Unit = {

    /***********************************Stage1 Start- Graph Cut***************************************/
    val netGraph = originalGraph.getOrElse({
      logger.error(s"Couldn't load original graph File at $NGSGraphDir$originalGraphFileName")
      throw new Exception("Couldn't load original graph File!")
    })

    //val netGraph = load_graph(fileName = originalGraphFileName, dir = NGSGraphDir)

    //load perturbed graph
    val pertGraph = perturbedGraph.getOrElse({
      logger.error(s"Couldn't load perturbed graph File at $NGSGraphDir$perturbedGraphFileName")
      throw new Exception("Couldn't load perturbed graph File!")
    })

    //val pertGraph = load_graph(fileName = perturbedGraphFileName,  dir= NGSGraphDir)

    val numOfnetGraphNodes: Int = netGraph.sm.nodes().asScala.toList.length
    val numOfperturbedGraphNodes: Int = pertGraph.sm.nodes().asScala.toList.length

    val numOfParts: Int = {
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
    serializeShardsAsStrings(NGSubgraphs, PGSubgraphs, dir = mapReduce1Dir, fileName = mapReduce1InputFileName)
    logger.info(s"Subgraphs File saved successfully at! $mapReduce1Dir/$mapReduce1InputFileName")

    logger.info("Serializing entire graphInfo...:")
    serializeAllNodesEdges(netGraph, pertGraph, dir = mapReduce1Dir, fileName = graphInfoFileName)
    logger.info(s"Subgraphs File saved successfully at! $mapReduce1Dir/$graphInfoFileName")

    /** *********************************Stage 1 Over************************************************ */

    /** *********************************Stage 2 Start- MapReduce********************************** */

    val inputPath1 = s"$mapReduce1Dir$mapReduce1InputFileName"
    val outputPath1 = s"$mapReduce1Dir$mapReduce1outputDirPath"

    val conf: JobConf = new JobConf(this.getClass)
    conf.setJobName("NodesEquivalence")
    conf.set("fs.defaultFS", hadoopFS)
    conf.set("mapreduce.job.maps", "1")
    conf.set("mapreduce.job.reduces", "1")
    conf.setNumReduceTasks(1)
    conf.setOutputKeyClass(classOf[Text])
    conf.setOutputValueClass(classOf[Text])
    conf.setMapperClass(classOf[MapReduceProgram.Map])
    conf.setCombinerClass(classOf[MapReduceProgram.Reduce])
    conf.setReducerClass(classOf[MapReduceProgram.Reduce])
    conf.setInputFormat(classOf[TextInputFormat])
    conf.setOutputFormat(classOf[TextOutputFormat[Text, Text]])
    FileInputFormat.setInputPaths(conf, new Path(inputPath1))
    FileOutputFormat.setOutputPath(conf, new Path(outputPath1))
    JobClient.runJob(conf)

    //create yaml according to local fs/hdfs
    if hadoopFS == "local" then
      createYamlFileInLocal(dir = predictedYamlFileDir, yamlFileName = predictedYamlFileName)
    else if hadoopFS.contains("hdfs://") || hadoopFS.contains("s3://") then
      createYamlFileInHDFS(dir = predictedYamlFileDir, yamlFileName = predictedYamlFileName)
    else
      throw new Exception("hadoopFS should be set to either on local path, hdfs localhost path or s3 bucket path")

    //update yaml with nodes
    //    val nodePerturbationInfoObj = decipherNodes(s"$outputPath1/$mapReduce1outputFileName", NG = originalGraph.get, PG = perturbedGraph.get, newYamlFileDir = predictedYamlFileDir, newYamlFileName = predictedYamlFileName)
    val nodePerturbationInfoObj = decipherNodes(s"$outputPath1/$mapReduce1outputFileName", newYamlFileDir = predictedYamlFileDir, newYamlFileName = predictedYamlFileName)

    //use original yaml nodes information + original&perturbed graph nodes ingo to serialize edge information of modified/unperturbed nodes
    serializeEdgeInfo(mapReduce2Dir, mapReduce2InputFileName, nodePerturbationInfoObj)


    val inputPath2 = s"$mapReduce2Dir$mapReduce2InputFileName"
    val outputPath2 = s"$mapReduce2Dir$mapReduce2outputDirPath"

    //map-reduce2 configuration
    val conf2: JobConf = new JobConf(this.getClass)
    conf2.setJobName("EdgesEquivalence")
    conf2.set("fs.defaultFS", hadoopFS)
    conf2.set("mapreduce.job.maps", "1")
    conf2.set("mapreduce.job.reduces", "1")
    conf2.setNumReduceTasks(1)
    conf2.setOutputKeyClass(classOf[Text])
    conf2.setOutputValueClass(classOf[Text])
    conf2.setMapperClass(classOf[MapReduceProgram.Map2])
    conf2.setCombinerClass(classOf[MapReduceProgram.Reduce2])
    conf2.setReducerClass(classOf[MapReduceProgram.Reduce2])
    conf2.setInputFormat(classOf[TextInputFormat])
    conf2.setOutputFormat(classOf[TextOutputFormat[Text, Text]])
    FileInputFormat.setInputPaths(conf2, new Path(inputPath2))
    FileOutputFormat.setOutputPath(conf2, new Path(outputPath2))
    JobClient.runJob(conf2)

    //read map-reduce job2 output
    val edgeInfoMap = readEdgeInfoHDFSFile(s"$outputPath2$mapReduce2outputFileName")
    logger.info("Reducer2 Output- EdgeInfos:")
    logger.info(edgeInfoMap.toString)

    //update yaml
    edgeInfoMap.foreach(edgeSectionInfo => appendToYamlSubsection(dir = predictedYamlFileDir, yamlFileName = predictedYamlFileName, section = "Edges", subsection = edgeSectionInfo._1, newData = edgeSectionInfo._2))

    logger.info("DTLMap till now:")
    DTLMap.foreach(entry => println(s"${entry._1.id} - ${entry._2.map(ele => s"${ele._1.id}:${ele._2}")}"))
    logger.info("\nATLMap till now:")
    ATLMap.foreach(entry => println(s"${entry._1.id}" + "-" + s"${entry._2._1.id}:${entry._2._2}"))
    logger.info("Now updating DTL and ATL Maps:\n")
    updateDTLMap()
    logger.info("DTLMap: ")
    DTLMap.foreach(entry => logger.info(s"originalNodeID: ${entry._1.id} - matchIDs: ${entry._2.map(ele => s"${ele._1.id}:${ele._2}")}"))
    println("\nATLMap:")
    logger.info("ATLMap: ")
    ATLMap.foreach(entry => logger.info(s"originalNodeID: ${entry._1.id}" + "- matchIDs:" + s"${entry._2._1.id}:${entry._2._2}"))

    logger.info(s"\nATLMapCount: ${ATLMap.count(_ => true)}")
    logger.info(s"\nDTLMapCount: ${DTLMap.count(_ => true)}")

    /***********************************Stage 2 Over*************************************************/

    /***********************************Stage 3 Start-Model Accuracy Check****************************/

    val goldenFileDir = goldenYamlFileDir
    val goldenFileName = originalGraphFileName.concat(".yaml")

    //gen for- generated
    val genYamlFileDir = predictedYamlFileDir
    val genYamlFileName = predictedYamlFileName


    // Read the YAML files
    val goldenContent = {
      if hadoopFS == "local" || hadoopFS == "file:///" then
        getYamlFileContentFromLocal(yamlDir = goldenFileDir, yamlFileName = goldenFileName)
      else if hadoopFS.startsWith("hdfs://") || hadoopFS.startsWith("s3://") then
        getYamlFileContentInHDFS(yamlDir = goldenFileDir, yamlFileName = goldenFileName)
      else
        throw new Exception("hadoopFS should be set to either on local path, hdfs localhost path or s3 bucket path")
    }

    val genYamlContent = {
      if hadoopFS == "local" || hadoopFS == "file:///" then
        getYamlFileContentFromLocal(yamlDir = genYamlFileDir, yamlFileName = genYamlFileName)
      else if hadoopFS.startsWith("hdfs://") || hadoopFS.startsWith("s3://") then
        getYamlFileContentInHDFS(yamlDir = genYamlFileDir, yamlFileName = genYamlFileName)
      else
        throw new Exception("hadoopFS should be set to either on local path, hdfs localhost path or s3 bucket path")
    }


    // Parse the YAML content using SnakeYAML
    val goldenYaml = new Yaml()
    val goldenData: java.util.Map[String, java.util.Map[String, Any]] = goldenYaml.load(goldenContent)

    val goldenScalaData = goldenData.asScala.map {
      case (key, nestedMap: java.util.Map[String, Any]) =>
        key -> nestedMap.asScala.map {
          case (nestedKey, innerData: java.util.HashMap[Integer, Integer]) =>
            nestedKey -> innerData.asScala.toMap
          case (nestedKey, innerList: java.util.List[Integer]) =>
            nestedKey -> innerList.asScala.toList
          case (nestedKey, _) =>
            nestedKey -> List.empty[Int]
        }.toMap
    }.toMap

    // Parse the YAML content using SnakeYAML
    val genYaml = new Yaml()
    val genYamlData: java.util.Map[String, java.util.Map[String, Any]] = genYaml.load(genYamlContent)

    val genYamlScalaData = genYamlData.asScala.map {
      case (key, nestedMap: java.util.Map[String, Any]) =>
        key -> nestedMap.asScala.map {
          case (nestedKey, innerData: java.util.HashMap[Integer, Integer]) =>
            nestedKey -> innerData.asScala.toMap
          case (nestedKey, innerList: java.util.List[Integer]) =>
            nestedKey -> innerList.asScala.toList
          case (nestedKey, _) =>
            nestedKey -> List.empty[Int]
        }.toMap
    }.toMap

    val actualModifedNodes: List[(Int, Int)] = goldenScalaData("Nodes")("Modified") match
      case nodeIds: List[Int] => List.empty[(Int, Int)] ::: nodeIds.map(nid => (nid, nid))
      case _: List[(Int, Int)] => List.empty[(Int, Int)]

    val actualRemovedNodes: List[Int] = goldenScalaData("Nodes")("Removed") match
      case nodeIds: List[Int] => List.empty[Int] ::: nodeIds
      case _: List[(Int, Int)] => List.empty[Int]

    val actualAddedNodes: List[Int] = goldenScalaData("Nodes")("Added") match
      case nodeIds: List[Int] => List.empty[Int] ::: nodeIds
      //      case nodeIds: immutable.HashMap[Int, Int] => List.empty[Int] ::: nodeIds.toList.map(entry => entry._2)
      case nodeIds: immutable.Map[Int, Int] => List.empty[Int] ::: nodeIds.toList.map(entry => entry._2)

    val actualModifedEdges: immutable.Map[Int, Int] = goldenScalaData("Edges")("Modified") match
      case edgeIds: immutable.Map[Int, Int] => immutable.Map.empty[Int, Int] ++ edgeIds
      //      case edgeIds: immutable.HashMap[Int, Int] => immutable.Map.empty[Int, Int] ++ edgeIds
      case _ => immutable.Map.empty[Int, Int]

    val actualRemovedEdges: immutable.Map[Int, Int] = goldenScalaData("Edges")("Removed") match
      case edgeIds: immutable.Map[Int, Int] => immutable.Map.empty[Int, Int] ++ edgeIds
      //      case edgeIds: immutable.HashMap[Int, Int] => immutable.Map.empty[Int, Int] ++ edgeIds
      case _ => immutable.Map.empty[Int, Int]

    val actualAddedEdges: immutable.Map[Int, Int] = goldenScalaData("Edges")("Added") match
      case edgeIds: immutable.Map[Int, Int] => immutable.Map.empty[Int, Int] ++ edgeIds
      //      case edgeIds: immutable.HashMap[Int, Int] => immutable.Map.empty[Int, Int] ++ edgeIds
      case _ => immutable.Map.empty[Int, Int]

    val predictedModifedNodes: List[(Int, Int)] = genYamlScalaData("Nodes")("Modified") match
      case nodeIds: List[Int] => List.empty[(Int, Int)] ::: nodeIds.map(nid => (nid, nid))
      case _: List[(Int, Int)] => List.empty[(Int, Int)]

    val predictedRemovedNodes: List[Int] = genYamlScalaData("Nodes")("Removed") match
      case nodeIds: List[Int] => List.empty[Int] ::: nodeIds
      case _: List[(Int, Int)] => List.empty[Int]

    val predictedAddedNodes: List[Int] = genYamlScalaData("Nodes")("Added") match
      case nodeIds: List[Int] => List.empty[Int] ::: nodeIds
      case nodeIds: immutable.Map[Int, Int] => List.empty[Int] ::: nodeIds.toList.map(entry => entry._2)
    //      case nodeIds: immutable.HashMap[Int, Int] => List.empty[Int] ::: nodeIds.toList.map(entry => entry._2)

    val predictedModifedEdges: immutable.Map[Int, Int] = genYamlScalaData("Edges")("Modified") match
      case edgeIds: immutable.Map[Int, Int] => immutable.Map.empty[Int, Int] ++ edgeIds
      //      case edgeIds: immutable.HashMap[Int, Int] => immutable.Map.empty[Int, Int] ++ edgeIds
      case _ => immutable.Map.empty[Int, Int]

    val predictedRemovedEdges: immutable.Map[Int, Int] = genYamlScalaData("Edges")("Removed") match
      case edgeIds: immutable.Map[Int, Int] => immutable.Map.empty[Int, Int] ++ edgeIds
      //      case edgeIds: immutable.HashMap[Int, Int] => immutable.Map.empty[Int, Int] ++ edgeIds
      case _ => immutable.Map.empty[Int, Int]

    val predictedAddedEdges: immutable.Map[Int, Int] = genYamlScalaData("Edges")("Added") match
      case edgeIds: immutable.Map[Int, Int] => immutable.Map.empty[Int, Int] ++ edgeIds
      //      case edgeIds: immutable.HashMap[Int, Int] => immutable.Map.empty[Int, Int] ++ edgeIds
      case _ => immutable.Map.empty[Int, Int]

    logger.info(s"Original Modified Nodes: ${goldenScalaData("Nodes")("Modified").toList}")
    logger.info(s"Predicted Modified Nodes: ${genYamlScalaData("Nodes")("Modified").toList}")
    logger.info(s"\nOriginal Removed Nodes: ${goldenScalaData("Nodes")("Removed").toList}")
    logger.info(s"Predicted Removed Nodes: ${genYamlScalaData("Nodes")("Removed").toList}")
    logger.info(s"\nOriginal Added Nodes: ${goldenScalaData("Nodes")("Added").toList}")
    logger.info(s"Predicted Added Nodes: ${genYamlScalaData("Nodes")("Added").toList}")
    logger.info(s"\n\nOriginal Modified Edges: ${goldenScalaData("Edges")("Modified")}")
    logger.info(s"Predicted Modified Edges: ${genYamlScalaData("Edges")("Modified")}")
    logger.info(s"\nOriginal Removed Edges: ${goldenScalaData("Edges")("Removed")}")
    logger.info(s"Predicted Removed Edges: ${genYamlScalaData("Edges")("Removed")}")
    logger.info(s"\nOriginal Added Edges: ${goldenScalaData("Edges")("Added")}")
    logger.info(s"Predicted Added Edges: ${genYamlScalaData("Edges")("Added")}")

    val originalPertubedNodes = PerturbedNodes(actualModifedNodes, actualAddedNodes, actualRemovedNodes)
    val originalPertubedEdges = PerturbedEdges(actualModifedEdges, actualAddedEdges, actualRemovedEdges)

    val predictedPertubedNodes = PerturbedNodes(predictedModifedNodes, predictedAddedNodes, predictedRemovedNodes)
    val predictedPertubedEdges = PerturbedEdges(predictedModifedEdges, predictedAddedEdges, predictedRemovedEdges)

    //calculating Statistics
    val allScores = calculateModelAccuracy(originalPertubedNodes, originalPertubedEdges, predictedPertubedNodes, predictedPertubedEdges)

    writeYamlFile(allScores, predictedYamlFileDir, scoresYamlFileName)

    /***********************************Stage 3 End*******************************************************/


  }

}
