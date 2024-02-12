ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.2.2"

//Global / excludeLintKeys += idePackagePrefix
//Global / excludeLintKeys += test / fork
//Global / excludeLintKeys += run / mainClass

val scalaTestVersion = "3.2.11"
val guavaVersion = "31.1-jre"
val typeSafeConfigVersion = "1.4.2"
val logbackVersion = "1.2.10"
val sfl4sVersion = "2.0.0-alpha5"
val graphVizVersion = "0.18.1"
val netBuddyVersion = "1.14.4"
val catsVersion = "2.9.0"
val apacheCommonsVersion = "2.13.0"
val jGraphTlibVersion = "1.5.2"
val scalaParCollVersion = "1.0.4"
val guavaAdapter2jGraphtVersion = "1.5.2"



lazy val commonDependencies = Seq(
  "org.scala-lang.modules" %% "scala-parallel-collections" % scalaParCollVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "org.scalatestplus" %% "mockito-4-2" % "3.2.12.0-RC2" % Test,
  "com.typesafe" % "config" % typeSafeConfigVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "net.bytebuddy" % "byte-buddy" % netBuddyVersion
)
lazy val root = (project in file("."))
  .settings(
    name := "GraphEquivalence",
    //idePackagePrefix := Some("com.grapheq"),
    libraryDependencies ++= commonDependencies ++ Seq(
      "com.google.guava" % "guava" % guavaVersion,
      "guru.nidi" % "graphviz-java" % graphVizVersion,
      "org.typelevel" %% "cats-core" % catsVersion,
      "commons-io" % "commons-io" % apacheCommonsVersion,
      "org.jgrapht" % "jgrapht-core" % jGraphTlibVersion,
      "org.jgrapht" % "jgrapht-guava" % guavaAdapter2jGraphtVersion,
      "org.apache.hadoop" % "hadoop-common" % "3.3.4",
      "org.apache.hadoop" % "hadoop-mapreduce-client-core" % "3.3.4",
      "org.apache.hadoop" % "hadoop-mapreduce-client-jobclient" % "3.3.4",
      "org.yaml" % "snakeyaml" % "1.28",
      "org.scalatest" %% "scalatest" % "3.2.13" % "test",
      "org.scalactic" %% "scalactic" % "3.2.13",
    )
  ).dependsOn(Utilities)

lazy val Utilities = (project in file("Utilities"))
  .settings(
    scalaVersion := "3.2.2",
    name := "Utilities",
    libraryDependencies ++= commonDependencies
  )

scalacOptions ++= Seq(
  "-deprecation", // emit warning and location for usages of deprecated APIs
  "--explain-types", // explain type errors in more detail
  "-feature" // emit warning and location for usages of features that should be imported explicitly
)

unmanagedBase := baseDirectory.value / "lib"

val jarName = "GraphEquivalence.jar"
assembly/assemblyJarName := jarName

//Merging strategies
ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}


//compileOrder := CompileOrder.JavaThenScala
//test / fork := false
//run / fork := true
//run / javaOptions ++= Seq(
//  "-Xms8G",
//  "-Xmx100G",
//  "-XX:+UseG1GC"
//)
//
//Compile / mainClass := Some("com.grapheq.Main")
//run / mainClass := Some("com.graph.Main")
//
