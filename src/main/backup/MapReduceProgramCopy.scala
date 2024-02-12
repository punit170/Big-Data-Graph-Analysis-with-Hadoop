/*
import NetGraphAlgebraDefs.{Action, GraphPerturbationAlgebra, NetGraph, NetGraphComponent, NetModelAlgebra, NodeObject}
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
import com.grapheq.Main.{EdgesShard, NodePerturbationInfo, Shard, SimRankv_2, createYamlFile, decipherNodes, deserializeEdgeInfo, deserializeMapperOutputValue, deserializeStringShard, findBestNodeMatch, originalGraph, perturbedGraph, readNodeMatchHDFSFile, serializeEdgeInfo, serializeShardsAsStrings, stringToNodeObject, appendToYamlSubsection, readEdgeInfoHDFSFile, updateDTLMap, DTLMap, ATLMap}
import org.apache.hadoop.io.*

import java.io.DataInput
import java.io.DataOutput
import scala.collection.immutable

object MapReduceProgram:

class Map extends MapReduceBase with Mapper[LongWritable, Text, Text, Text] :

private val MapperOutputKey = new Text()
private val MapperOutputValue = new Text()

@throws[IOException]
override def map(key: LongWritable, value: Text, output: OutputCollector[Text, Text], reporter: Reporter): Unit = {
  val line: String = value.toString
  print(line)
  val curr_shard = deserializeStringShard(line)

  val currSRMap = SimRankv_2(curr_shard.allNnodes, curr_shard.allN_ParentMap, curr_shard.allPnodes, curr_shard.allP_ParentMap)

  currSRMap.foreach(kvpair => {
    MapperOutputKey.set(kvpair._1.toString())
    MapperOutputValue.set(kvpair._2.toString())
    //println(s"mapper output: ${kvpair._1.toString()} ------${kvpair._2.toString()}")
    output.collect(MapperOutputKey, MapperOutputValue)
  })

  println()
}


class Reduce extends MapReduceBase with Reducer[Text, Text, Text, Text] :

private val reducerOutputVal = new Text()

override def reduce(key: Text, values: util.Iterator[Text], output: OutputCollector[Text, Text], reporter: Reporter): Unit = {
  val NodeU: NodeObject = stringToNodeObject(key.toString)

  val tempStringList = ListBuffer[String]()
  values.asScala.foreach(text =>
    tempStringList += text.toString
  )

  val mergedList: List[(NodeObject, Float)] = tempStringList.foldLeft(List[(NodeObject, Float)]())((acc, aStrList) =>
    acc ++ deserializeMapperOutputValue(aStrList)
  )
  if (mergedList.isEmpty)
    return

  val groupedByNode: immutable.Map[NodeObject, List[(NodeObject, Float)]] = mergedList.groupBy(_._1)

  val intermediateResult: List[(NodeObject, Float)] = groupedByNode.map {
    case (_, entries) =>
      val maxFloatValue = entries.map(_._2).max
      entries.find(_._2 == maxFloatValue).get
  }.toList.filterNot(ele => ele._2 == 0.0).sortBy(-_._2)

  val result1 = intermediateResult.filter(ele => ele._2 == intermediateResult(0)._2)

  if (result1.length == 0)
    reducerOutputVal.set("List(List(NodeObject(-1,0,0,0,0,0,0,0,0),-1.0))")
  else if (result1.length == 1)
    reducerOutputVal.set("List(" + result1.head.toString + ")")
  else if (result1.length > 1)
    reducerOutputVal.set("List(" + findBestNodeMatch(NodeU, result1).toString + ")")
  output.collect(key, reducerOutputVal)
  return
}

def postMapReduce1(): immutable.Map[NodeObject, NodeObject] = {
  val nodePerturbationInfoObj = decipherNodes("C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/output50/part-00000", NG = originalGraph.get, PG = perturbedGraph.get, newYamlFileDir = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/", newYamlFileName = "myYaml")
  serializeEdgeInfo(nodePerturbationInfoObj, originalGraph.get, perturbedGraph.get)
  return nodePerturbationInfoObj.unperturbedNodesMap ++ nodePerturbationInfoObj.modifiedNodesMap
}

class Map2 extends MapReduceBase with Mapper[LongWritable, Text, Text, Text] :

private val MapperOutputKey = new Text()
private val MapperOutputValue = new Text()
private val matchMap: immutable.Map[NodeObject, NodeObject] = readNodeMatchHDFSFile("C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/output50/part-00000")

@throws[IOException]
override def map(key: LongWritable, value: Text, output: OutputCollector[Text, Text], reporter: Reporter): Unit = {
  val line: String = value.toString
  println("curr mapper read line:")
  println(line)
  val currEdgeShard: EdgesShard = deserializeEdgeInfo(line)
  val allNEdgesList = currEdgeShard.allNEdgesInfo
  val allPEdgesList = currEdgeShard.allPEdgesInfo

  allNEdgesList.foreach {
    case ((sourceN, targetN), valN) =>
      val ueSourceMatchNode = matchMap(sourceN)
      val ueTargetMatchNode = matchMap(targetN)
      val matchPair = allPEdgesList.find {
        case ((sourceP, targetP), _) => sourceP == ueSourceMatchNode && targetP == ueTargetMatchNode
      }

      val outputValue = s"${sourceN.id}:${targetN.id}"
      MapperOutputValue.set(outputValue)

      matchPair match {
        case Some(((_, _), valP)) =>

          if valN != valP then
            MapperOutputKey.set("Modified")
          output.collect(MapperOutputKey, MapperOutputValue)
          println(s"mapperKey: ${MapperOutputKey.toString} - mapperVal: ${MapperOutputValue.toString}")

          MapperOutputKey.set("Removed")
          output.collect(MapperOutputKey, MapperOutputValue)
          println(s"mapperKey: ${MapperOutputKey.toString} - mapperVal: ${MapperOutputValue.toString}")

          MapperOutputKey.set("Added")
          output.collect(MapperOutputKey, MapperOutputValue)
          println(s"mapperKey: ${MapperOutputKey.toString} <-> mapperVal: ${MapperOutputValue.toString}")
      else {
      MapperOutputKey.set ("Unperturbed")
      output.collect (MapperOutputKey, MapperOutputValue)
      println (s"mapperKey: ${
      MapperOutputKey.toString
      } - mapperVal: ${
      MapperOutputValue.toString
      }")
      }
        case None =>
          MapperOutputKey.set("Removed")
          output.collect(MapperOutputKey, MapperOutputValue)
          println(s"mapperKey: ${MapperOutputKey.toString} - mapperVal: ${MapperOutputValue.toString}")
      }
  }

  allPEdgesList.foreach {
    case ((sourceP, targetP), valP) =>
      //          if sourceP.id == 47 && targetP.id == 37 then
      //            println("node 47 -----------37")
      val veSourceMatch = matchMap.find { case (_, v) => v == sourceP }.map(_._1)
      val veTargetMatch = matchMap.find { case (_, v) => v == targetP }.map(_._1)

      veSourceMatch match {
        case Some(veSourceMatchNode) =>
          veTargetMatch match {
            case Some(veTargetMatchNode) =>
              val matchPair = allNEdgesList.find(edgeE => (edgeE._1._1 == veSourceMatchNode && edgeE._1._2 == veTargetMatchNode))

              matchPair match {
                case Some(((_, _), _)) => {}
                case None =>
                  val outputValue = s"${sourceP.id}:${targetP.id}"
                  MapperOutputValue.set(outputValue)
                  MapperOutputKey.set("Added")
                  output.collect(MapperOutputKey, MapperOutputValue)
                  println(s"mapperKey: ${MapperOutputKey.toString} - mapperVal: ${MapperOutputValue.toString}")
              }
            case None => {}
          }
        case None => {}
      }
  }

}


class Reduce2 extends MapReduceBase with Reducer[Text, Text, Text, Text] :

private val reducerOutputVal = new Text()

override def reduce(key: Text, values: util.Iterator[Text], output: OutputCollector[Text, Text], reporter: Reporter): Unit = {
  val tempStringList = ListBuffer[String]()
  values.asScala.foreach(text =>
    tempStringList += text.toString
  )
  val mergedList: List[String] = tempStringList.distinct.toList
  println(s"\n\nmergedList: $mergedList")

  if (mergedList.isEmpty)
    println("Reducer- merged list is empty!")
  return
  else
  reducerOutputVal.set(mergedList.toString())
  output.collect(key, reducerOutputVal)
  println(s"\nreducerKey: $key -- reducerVal: ${reducerOutputVal.toString}")
  return
}

@main def runMapReduce() =
  val inputPath = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/Input50.txt"
  val outputPath = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/output50/"
  val conf: JobConf = new JobConf(this.getClass)
  conf.setJobName("NodesEquivalence")
  conf.set("fs.defaultFS", "local")
  conf.set("mapreduce.job.maps", "1")
  conf.set("mapreduce.job.reduces", "1")
  conf.setNumReduceTasks(1)
  conf.setOutputKeyClass(classOf[Text])
  conf.setOutputValueClass(classOf[Text])
  conf.setMapperClass(classOf[Map])
  conf.setCombinerClass(classOf[Reduce])
  conf.setReducerClass(classOf[Reduce])
  conf.setInputFormat(classOf[TextInputFormat])
  conf.setOutputFormat(classOf[TextOutputFormat[Text, Text]])
  FileInputFormat.setInputPaths(conf, new Path(inputPath))
  FileOutputFormat.setOutputPath(conf, new Path(outputPath))
  JobClient.runJob(conf)

  val yamlDir = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/"
  val yamlFileName = "myYaml"

  createYamlFile(dir = yamlDir, yamlFileName = yamlFileName)

  val nodePerturbationInfoObj = decipherNodes("C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/output50/part-00000", NG = originalGraph.get, PG = perturbedGraph.get, newYamlFileDir = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/", newYamlFileName = "myYaml")
  serializeEdgeInfo(nodePerturbationInfoObj, originalGraph.get, perturbedGraph.get)

  val inputPath2 = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/edgeInfo.txt"
  val outputPath2 = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/edgeOutput50/"
  val conf2: JobConf = new JobConf(this.getClass)
  conf2.setJobName("EdgesEquivalence")
  conf2.set("fs.defaultFS", "local")
  conf2.set("mapreduce.job.maps", "1")
  conf2.set("mapreduce.job.reduces", "1")
  conf2.setNumReduceTasks(1)
  conf2.setOutputKeyClass(classOf[Text])
  conf2.setOutputValueClass(classOf[Text])
  conf2.setMapperClass(classOf[Map2])
  conf2.setCombinerClass(classOf[Reduce2])
  conf2.setReducerClass(classOf[Reduce2])
  conf2.setInputFormat(classOf[TextInputFormat])
  conf2.setOutputFormat(classOf[TextOutputFormat[Text, Text]])
  FileInputFormat.setInputPaths(conf2, new Path(inputPath2))
  FileOutputFormat.setOutputPath(conf2, new Path(outputPath2))
  JobClient.runJob(conf2)

  val edgeInfoMap = readEdgeInfoHDFSFile(s"${outputPath2}part-00000")
  println(edgeInfoMap)

  edgeInfoMap.foreach(edgeSectionInfo => appendToYamlSubsection(dir = yamlDir, yamlFileName = yamlFileName, section = "Edges", subsection = edgeSectionInfo._1, newData = edgeSectionInfo._2))

  println("DTLMap:")
  DTLMap.map(entry => println(s"${entry._1.id} - ${entry._2.map(ele => s"${ele._1.id}:${ele._2}")}"))
  println("\nATLMap:")
  ATLMap.map(entry => println(s"${entry._1.id}" + "-" + s"${entry._2._1.id}:${entry._2._2}"))

  updateDTLMap()
  println("DTLMap:")
  DTLMap.map(entry => println(s"${entry._1.id} - ${entry._2.map(ele => s"${ele._1.id}:${ele._2}")}"))
  println("\nATLMap:")
  ATLMap.map(entry => println(s"${entry._1.id}" + "-" + s"${entry._2._1.id}:${entry._2._2}"))

  println(s"ATLMapCount: ${ATLMap.count(_ => true)}")
  println(s"DTLMapCount: ${DTLMap.count(_ => true)}")
*/
