import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import net.virtualvoid.sbt.graph.Plugin._
import sbt._
import sbt.Keys._
import sbtassembly.MergeStrategy

val scalaV = "2.11.8"

val toukNexusGroups = "http://nexus.touk.pl/nexus/content/groups/"
val toukNexusRepositories = "http://nexus.touk.pl/nexus/content/repositories/"

val defaultVersion = "0.1-SNAPSHOT"

version in ThisBuild := sys.props.getOrElse("espEngineToukVersion", defaultVersion)

credentials in ThisBuild += Credentials("Sonatype Nexus Repository Manager", "nexus.touk.pl", "deployment", "deployment123")

publishTo in ThisBuild := {
  if (isSnapshot.value)
    Some("snapshots" at toukNexusRepositories + "snapshots")
  else
    Some("releases"  at toukNexusRepositories + "public")
}

def numberUtilsStrategy: String => MergeStrategy = {
  case PathList(ps@_*) if ps.last == "NumberUtils.class" => MergeStrategy.first
  case x => MergeStrategy.defaultMergeStrategy(x)
}

val commonSettings =
  graphSettings ++
  Seq(
    organization  := "pl.touk.esp",
    scalaVersion  := scalaV,
    resolvers ++= Seq(
      "local" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
      "touk repo" at "http://nexus.touk.pl/nexus/content/groups/public",
      "touk snapshots" at "http://nexus.touk.pl/nexus/content/groups/public-snapshots",
      "spring milestone" at "https://repo.spring.io/milestone"
    ),
    scalacOptions := Seq(
      "-unchecked",
      "-deprecation",
      "-encoding", "utf8",
      "-Xfatal-warnings",
      "-feature",
      "-language:postfixOps",
      "-language:existentials",
      "-target:jvm-1.8"
    ),
    sources in (Compile, doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false,
    assemblyMergeStrategy in assembly := numberUtilsStrategy
  )

//mamy te wersje akki bo flink jej wymaga
val akkaV = "2.3.7"
val flinkV = "1.3.1"
val kafkaV = "0.9.0.1"
val springV = "5.0.0.M1"
val scalaTestV = "3.0.0-M15"
val logbackV = "1.1.3"
val argonautShapelessV = "1.2.0-M1"
val argonautMajorV = "6.2"
val argonautV = s"$argonautMajorV-M3"
val catsV = "0.7.0"
val monocleV = "1.2.2"
val scalaParsersV = "1.0.4"
val dispatchV = "0.11.3"
val slf4jV = "1.7.21"
val scalaLoggingV = "3.4.0"
val ficusV = "1.2.6"
val configV = "1.3.0"
val commonsLangV = "3.3.2"
val dropWizardV = "3.1.0"

val perfTestSampleName = "esp-perf-test-sample"

lazy val perf_test = (project in file("perf-test")).
  configs(IntegrationTest). // po dodaniu własnej konfiguracji, IntellijIdea nie rozpoznaje zależności dla niej
  settings(commonSettings).
  settings(Defaults.itSettings).
  settings(
    name := "esp-perf-test",
    Keys.test in IntegrationTest <<= (Keys.test in IntegrationTest).dependsOn(
      publishLocal in (assembly in Compile) in perf_test_sample
    ),
    libraryDependencies ++= {
      Seq(
        "org.scalatest" %% "scalatest" % scalaTestV % "it,test",
        "org.slf4j" % "jul-to-slf4j" % slf4jV,
        "org.apache.flink" %% "flink-clients" % flinkV % "provided",
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "runtime", // na potrzeby optymalizacji procesów
        "com.iheart" %% "ficus" % ficusV
      )
    }
  ).
  dependsOn(management, interpreter, kafkaFlinkUtil, kafkaTestUtil)


lazy val perf_test_sample = (project in file("perf-test/sample")).
  settings(commonSettings).
  settings(
    name := perfTestSampleName,
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "com.iheart" %% "ficus" % ficusV
      )
    },
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.copy(`classifier` = Some("assembly"))
    }
  ).
  settings(addArtifact(artifact in (Compile, assembly), assembly)).
  dependsOn(flinkUtil, kafkaFlinkUtil, process % "runtime")

val akkaHttpV = "2.0.3"

lazy val engineStandalone = (project in file("engine-standalone")).
  settings(commonSettings).
  settings(
    name := "esp-engine-standalone",
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = true, level = Level.Debug),
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.copy(`classifier` = Some("assembly"))
    },
    libraryDependencies ++= {
      Seq(
        "org.typelevel" %% "cats-core" % catsV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,

        "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpV force(),
        "com.typesafe.akka" %% "akka-http-testkit-experimental" % akkaHttpV % "test" force(),
        "io.argonaut" %% "argonaut" % argonautV,
        "com.github.alexarchambault" %% s"argonaut-shapeless_$argonautMajorV" % argonautShapelessV,

        "org.scalatest" %% "scalatest" % scalaTestV % "test",
        "ch.qos.logback" % "logback-classic" % logbackV % "test"
      )
    }
  ).
  settings(addArtifact(artifact in (Compile, assembly), assembly)).
  dependsOn(interpreter, standaloneUtil, argonautUtils)

lazy val management = (project in file("management")).
  configs(IntegrationTest).
  settings(commonSettings).
  settings(Defaults.itSettings).
  settings(
    name := "esp-management",
    Keys.test in IntegrationTest <<= (Keys.test in IntegrationTest).dependsOn(
      publishLocal in (assembly in Compile) in management_sample
    ),
    //jest problem we flinku jesli sie naraz deployuje i puszcza testy :|
    parallelExecution in IntegrationTest := false,
    libraryDependencies ++= {
      Seq(
        "org.typelevel" %% "cats-core" % catsV,
        "org.apache.flink" %% "flink-clients" % flinkV % "provided",
        "org.apache.flink" % "flink-shaded-curator-recipes" % flinkV % "provided",
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",

        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,

        "org.scalatest" %% "scalatest" % scalaTestV % "it,test"
      )
    }
  ).dependsOn(interpreter, queryableState, kafkaTestUtil % "it,test")

val managementSampleName = "esp-management-sample"

lazy val management_sample = (project in file("management/sample")).
  settings(commonSettings).
  settings(
    name := managementSampleName,
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false, level = Level.Debug),
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    },
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.copy(`classifier` = Some("assembly"))
    }
  ).
  settings(addArtifact(artifact in (Compile, assembly), assembly)).
  dependsOn(flinkUtil, kafka, kafkaFlinkUtil, process % "runtime,test", flinkTestUtil % "test", kafkaTestUtil % "test")

lazy val example = (project in file("example")).
  settings(commonSettings).
  settings(
    name := "esp-example",
    fork := true, // without this there are some classloading issues
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.scalatest" %% "scalatest" % scalaTestV % "test",
        "ch.qos.logback" % "logback-classic" % logbackV % "test"
      )
    }
  ).dependsOn(process, kafkaFlinkUtil, kafkaTestUtil % "test", flinkTestUtil % "test")

lazy val process = (project in file("process")).
  settings(commonSettings).
  settings(
    name := "esp-process",
    fork := true, // without this there are some classloading issues
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV,
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).dependsOn(flinkApi, flinkUtil, interpreter, kafka % "test", kafkaTestUtil % "test", kafkaFlinkUtil % "test", flinkTestUtil % "test")

lazy val interpreter = (project in file("interpreter")).
  settings(commonSettings).
  settings(
    name := "esp-interpreter",
    libraryDependencies ++= {
      Seq(
        "org.apache.commons" % "commons-lang3" % commonsLangV,
        "org.springframework" % "spring-expression" % springV,
        "com.github.alexarchambault" %% s"argonaut-shapeless_$argonautMajorV" % argonautShapelessV,
        "ch.qos.logback" % "logback-classic" % logbackV % "test",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version),
    buildInfoKeys ++= Seq[BuildInfoKey] (
      "buildTime" -> java.time.LocalDateTime.now().toString,
      "gitCommit" -> git.gitHeadCommit.value.getOrElse("")
    ),
    buildInfoPackage := "pl.touk.esp.engine.version",
    buildInfoOptions ++= Seq(BuildInfoOption.ToMap)
  ).
  dependsOn(util)

lazy val kafka = (project in file("kafka")).
  settings(commonSettings).
  settings(
    name := "esp-kafka",
    libraryDependencies ++= {
      Seq(
        "org.apache.kafka" % "kafka-clients" % kafkaV
      )
    }
  ).
  dependsOn(util)

lazy val kafkaFlinkUtil = (project in file("kafka-flink-util")).
  settings(commonSettings).
  settings(
    name := "esp-kafka-flink-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-connector-kafka-0.9" % flinkV,
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).
  dependsOn(flinkApi, kafka, flinkUtil, kafkaTestUtil % "test")

lazy val kafkaTestUtil = (project in file("kafka-test-util")).
  settings(commonSettings).
  settings(
    name := "esp-kafka-test-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.kafka" %% "kafka" % kafkaV,
        "org.scalatest" %% "scalatest" % scalaTestV
      )
    }
  )

lazy val util = (project in file("util")).
  settings(commonSettings).
  settings(
    name := "esp-util",
    libraryDependencies ++= {
      Seq(
        "com.iheart" %% "ficus" % ficusV
      )
    }
  ).dependsOn(api, httpUtils)



lazy val flinkUtil = (project in file("flink-util")).
  settings(commonSettings).
  settings(
    name := "esp-flink-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" % "flink-metrics-dropwizard" % flinkV
      )
    }
  ).dependsOn(util, flinkApi)

lazy val flinkTestUtil = (project in file("flink-test-util")).
  settings(commonSettings).
  settings(
    name := "esp-flink-test-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" %% "flink-test-utils" % flinkV,
        "org.apache.flink" % "flink-metrics-dropwizard" % flinkV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "org.scalatest" %% "scalatest" % scalaTestV
      )
    }
  ).dependsOn(queryableState)

lazy val standaloneUtil = (project in file("standalone-util")).
  settings(commonSettings).
  settings(
    name := "esp-standalone-util",
    libraryDependencies ++= {
      Seq(
        "io.dropwizard.metrics" % "metrics-core" % dropWizardV,
        "io.dropwizard.metrics" % "metrics-graphite" % dropWizardV
      )
    }
  ).dependsOn(util)




lazy val api = (project in file("api")).
  settings(commonSettings).
  settings(
    name := "esp-api",
    libraryDependencies ++= {
      Seq(
        //TODO: czy faktycznie tak chcemy??
        "com.github.alexarchambault" %% s"argonaut-shapeless_$argonautMajorV" % argonautShapelessV,
        "org.typelevel" %% "cats-core" % catsV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.typesafe" % "config" % configV

      )
    }
  )

lazy val flinkApi = (project in file("flink-api")).
  settings(commonSettings).
  settings(
    name := "esp-flink-api",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-java" % flinkV % "provided",
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided"

      )
    }
  ).dependsOn(api)

lazy val processReports = (project in file("processReports")).
  settings(commonSettings).
  settings(
    name := "esp-process-reports",
    libraryDependencies ++= {
      Seq(
        "com.typesafe" % "config" % "1.3.0",
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).dependsOn(httpUtils)

lazy val httpUtils = (project in file("httpUtils")).
  settings(commonSettings).
  settings(
    name := "esp-http-utils",
    libraryDependencies ++= {
      Seq(
        "net.databinder.dispatch" %% "dispatch-core" % dispatchV,// % "optional",
        "org.scala-lang.modules" %% "scala-parser-combinators" % scalaParsersV, // scalaxb deps
        "io.argonaut" %% "argonaut" % argonautV,
        "com.github.alexarchambault" %% s"argonaut-shapeless_$argonautMajorV" % argonautShapelessV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV
      )
    }
  )

lazy val argonautUtils = (project in file("argonautUtils")).
  settings(commonSettings).
  settings(
    name := "esp-argonaut-utils",
    libraryDependencies ++= {
      Seq(
        "io.argonaut" %% "argonaut" % argonautV,
        "com.github.alexarchambault" %% s"argonaut-shapeless_$argonautMajorV" % argonautShapelessV,
        "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpV force()
      )
    }
  )

//osobny modul bo chcemy uzyc klienta do testowania w management_sample
lazy val queryableState = (project in file("queryableState")).
  settings(commonSettings).
  settings(
    name := "esp-queryable-state",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.typesafe" % "config" % configV
      )
    }
  ).dependsOn(api)

publishArtifact := false

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,              // : ReleaseStep
  inquireVersions,                        // : ReleaseStep
  runTest,                                // : ReleaseStep
  setReleaseVersion,                      // : ReleaseStep
  commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
  tagRelease,                             // : ReleaseStep
  setNextVersion,                         // : ReleaseStep
  commitNextVersion,                      // : ReleaseStep
  pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
)
