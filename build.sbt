import SonatypeKeys._
import sbtassembly.Plugin._
import AssemblyKeys._

sonatypeSettings

organization := "org.scassandra"

name := "scassandra-server"

version := "0.7.0-SNAPSHOT"

scalaVersion := "2.10.4"

val sprayVersion = "1.2.1"

val akkaVersion = "2.2.4"

addArtifact(Artifact("scassandra-server", "assembly"), sbtassembly.Plugin.AssemblyKeys.assembly)

resolvers += "Sonatype snapshots repo" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers += Resolver.mavenLocal

test in assembly := {}

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.0.13",
  "com.typesafe.akka" % "akka-actor_2.10" % akkaVersion,
  "com.typesafe.akka" % "akka-remote_2.10" % akkaVersion,
  "io.spray" %% "spray-json" % "1.2.5",
  "io.spray" % "spray-can" % sprayVersion,
  "io.spray" % "spray-routing" % sprayVersion,
  "com.typesafe" %% "scalalogging-slf4j" % "1.0.1",
  "com.google.guava" % "guava" % "17.0",
  ("org.apache.cassandra" % "cassandra-all" % "2.1.2").exclude("org.antlr", "stringtemplate"),
  "org.scassandra" % "cql-antlr" % "0.1.0"
)

// Read here for optional dependencies:
// http://etorreborre.github.io/specs2/guide/org.specs2.guide.Runners.html#Dependencies

resolvers ++= Seq(
  "snapshots oss" at "http://oss.sonatype.org/content/repositories/snapshots",
  "releases oss" at "http://oss.sonatype.org/content/repositories/releases",
  "spray repo" at "http://repo.spray.io"
)

publishArtifact in Test := false

pomExtra := {
<url>http://www.scassandra.org</url>
<licenses>
  <license>
    <name>Apache 2</name>
    <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
    <distribution>repo</distribution>
  </license>
</licenses>
  <scm>
    <connection>scm:git:github.com/scassandra/scassandra-server</connection>
    <developerConnection>scm:git:git@github.com:scassandra/scassandra-server</developerConnection>
    <url>github.com/scassandra/scassandra-server</url>
  </scm>
<developers>
  <developer>
    <email>christopher.batey@gmail.com</email>
    <name>Christopher Batey</name>
    <url>https://github.com/chbatey</url>
    <id>chbatey</id>
  </developer>
  <developer>
    <email>tepafoo@gmail.com</email>
    <name>Dogan Narinc</name>
    <url>https://github.com/tepafoo</url>
    <id>tepafoo</id>
  </developer>
</developers>
}

/*
 *
 * test specific config
 *
 */
libraryDependencies ++= Seq(
  "com.typesafe.akka" % "akka-testkit_2.10" % akkaVersion % "test",
  "io.spray" % "spray-testkit" % sprayVersion % "test",
  "com.datastax.cassandra" % "cassandra-driver-core" % "2.0.9" % "test" exclude("com.google.guava", "guava"),
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0" % "test",
  "org.scalatest" %% "scalatest" % "2.2.3" % "test",
  "org.pegdown" % "pegdown" % "1.4.2", // added as a hack to get scala test html reports working, was getting a NoClassDef
  "org.mockito" % "mockito-core" % "1.9.5" % "test"
)

scalacOptions in Test ++= Seq("-Yrangepos")

(testOptions in Test) += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/report")

parallelExecution in Test := false
/*
 *
 * end of test specific config
 *
 */
