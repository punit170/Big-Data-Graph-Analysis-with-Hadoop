package Variables

import Variables.GraphConfigReader.config
import com.typesafe.config.ConfigFactory

object GraphConfigReader:
 val config = ConfigFactory.load()
 val NGSGraphDir = config.getString("GraphEq.ShardModel.NGSGraphDir")
 val originalGraphFileName = config.getString("GraphEq.ShardModel.originalGraphFileName")
 val perturbedGraphFileName = config.getString("GraphEq.ShardModel.perturbedGraphFileName")
 val mapReduce1Dir = config.getString("GraphEq.ShardModel.mapReduce1Dir")
 val mapReduce1outputDirPath = config.getString("GraphEq.ShardModel.mapReduce1outputDirPath")
 val mapReduce1InputFileName = config.getString("GraphEq.ShardModel.mapReduce1InputFileName")
 val graphInfoFileName = config.getString("GraphEq.ShardModel.graphInfoFileName")
 val mapReduce1outputFileName = config.getString("GraphEq.ShardModel.mapReduce1outputFileName")
 val mapReduce2Dir = config.getString("GraphEq.ShardModel.mapReduce2Dir")
 val mapReduce2outputDirPath = config.getString("GraphEq.ShardModel.mapReduce2outputDirPath")
 val mapReduce2InputFileName = config.getString("GraphEq.ShardModel.mapReduce2InputFileName")
 val mapReduce2outputFileName = config.getString("GraphEq.ShardModel.mapReduce2outputFileName")
 val predictedYamlFileDir = config.getString("GraphEq.ShardModel.predictedYamlFileDir")
 val predictedYamlFileName = config.getString("GraphEq.ShardModel.predictedYamlFileName")
 val hadoopFS = config.getString("GraphEq.Hadoop.hadoopFS")
 val goldenYamlFileDir = config.getString("GraphEq.ShardModel.goldenYamlFileDir")
 val scoresYamlFileName = config.getString("GraphEq.ShardModel.scoresYamlFileName")



