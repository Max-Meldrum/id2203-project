name := "project18"
organization in ThisBuild := "se.kth.id2203"
version in ThisBuild := "1.0-SNAPSHOT"
scalaVersion in ThisBuild := "2.12.4"

// PROJECTS

lazy val global = project
  .in(file("."))
  .settings(settings)
  .aggregate(
    common,
    server,
    client
  )

lazy val common = (project in file("common"))
  .settings(
    name := "common",
    settings,
    libraryDependencies ++= commonDependencies
  )

lazy val server = (project in file("server"))
  .settings(
    name := "server",
    settings,
    assemblySettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      deps.logback,
      deps.kSim % "test"
    )
  )
  .dependsOn(
    common
  )

lazy val client = (project in file("client"))
  .settings(
    name := "client",
    settings,
    assemblySettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      deps.log4j,
      deps.log4jSlf4j,
      deps.jline,
      deps.fastparse
    )
  )
  .dependsOn(
    common
  )

// DEPENDENCIES

lazy val deps =
  new {
    val logbackV        = "1.2.3"
    val scalaLoggingV   = "3.7.2"
    val scalatestV      = "3.0.4"
    val kompicsV        = "1.0.0"
    val commonUtilsV    = "2.0.0"
    val scallopV        = "3.1.1"
    val jlineV          = "3.5.1"
    val log4jV          = "1.2.17"
    val slf4jV          = "1.7.25"
    val fastparseV      = "1.0.0"

    val logback        = "ch.qos.logback"             %  "logback-classic"                 % logbackV
    val scalaLogging   = "com.typesafe.scala-logging" %% "scala-logging"                   % scalaLoggingV
    val scalatest      = "org.scalatest"              %% "scalatest"                       % scalatestV
    val kompics        = "se.sics.kompics"            %% "kompics-scala"                   % kompicsV
    val kNetwork       = "se.sics.kompics.basic"      %  "kompics-port-network"            % kompicsV
    val nettyNetwork   = "se.sics.kompics.basic"      %  "kompics-component-netty-network" % kompicsV
    val kTimer         = "se.sics.kompics.basic"      %  "kompics-port-timer"              % kompicsV
    val javaTimer      = "se.sics.kompics.basic"      %  "kompics-component-java-timer"    % kompicsV
    val kSim           = "se.sics.kompics"            %% "kompics-scala-simulator"         % kompicsV
    val commonUtils    = "com.larskroll"              %% "common-utils-scala"              % commonUtilsV
    val scallop        = "org.rogach"                 %% "scallop"                         % scallopV
    val jline          = "org.jline"                  %  "jline"                           % jlineV
    val log4j          = "log4j"                      %  "log4j"                           % log4jV
    val log4jSlf4j     = "org.slf4j"                  %  "slf4j-log4j12"                   % slf4jV
    val fastparse      = "com.lihaoyi"                %% "fastparse"                       % fastparseV
  }

lazy val commonDependencies = Seq(
  deps.scalaLogging,
  deps.kompics,
  deps.kNetwork,
  deps.nettyNetwork,
  deps.kTimer,
  deps.javaTimer,
  deps.commonUtils,
  deps.scallop,
  deps.scalatest  % "test"
)

// SETTINGS
lazy val compilerOptions = Seq(
  "-unchecked",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-deprecation",
  "-encoding",
  "utf8"
)

lazy val settings = Seq(
  scalacOptions ++= compilerOptions,
  resolvers ++= Seq(
    "Kompics Releases" at "http://kompics.sics.se/maven/repository/",
    "Kompics Snapshots" at "http://kompics.sics.se/maven/snapshotrepository/",
    Resolver.mavenLocal
  )
)

lazy val assemblySettings = Seq(
  assemblyJarName in assembly := name.value + ".jar",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case _                             => MergeStrategy.first
  }
)