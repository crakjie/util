import sbt.Keys._
import sbt._

object Util extends Build {
  val libVersion = "6.20.0"
  val zkVersion = "3.3.4"
  val zkDependency = "org.apache.zookeeper" % "zookeeper" % zkVersion excludeAll(
    ExclusionRule("com.sun.jdmk", "jmxtools"),
    ExclusionRule("com.sun.jmx", "jmxri"),
    ExclusionRule("javax.jms", "jms")
  )

  val scalatestTest = scalaVersion(sv => sv match {
    case "2.9.2" => "org.scalatest" %% "scalatest" % "1.9.2" % "test"
    case _       => "org.scalatest" %% "scalatest" % "2.1.3" % "test"
  })

  val scalatest = scalaVersion(sv => sv match {
    case "2.9.2" => "org.scalatest" %% "scalatest" % "1.9.2"
    case _       => "org.scalatest" %% "scalatest" % "2.1.3"
  })

  val scalacheck = scalaVersion(sv => sv match {
    case "2.9.2" => "org.scalacheck" %% "scalacheck" % "1.10.1" % "test"
    case _       => "org.scalacheck" %% "scalacheck" % "1.11.5" % "test"
  })

  val parserCombinators = scalaVersion(sv => sv match {
    case v: String if v startsWith "2.11" =>
      Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2")
    case _      =>
      Nil
  })

  lazy val publishM2Configuration =
    TaskKey[PublishConfiguration]("publish-m2-configuration",
      "Configuration for publishing to the .m2 repository.")

  lazy val publishM2 =
    TaskKey[Unit]("publish-m2",
      "Publishes artifacts to the .m2 repository.")

  lazy val m2Repo =
    Resolver.file("publish-m2-local",
      Path.userHome / ".m2" / "repository")

  val sharedSettings = Seq(
    version := libVersion,
    organization := "com.twitter",
    crossScalaVersions := Seq("2.9.2", "2.10.4", "2.11.2"),
    // Workaround for a scaladoc bug which causes it to choke on
    // empty classpaths.
    unmanagedClasspath in Compile += Attributed.blank(new java.io.File("doesnotexist")),
    libraryDependencies <+= scalatestTest,
    libraryDependencies ++= Seq(
      "junit" % "junit" % "4.8.1" % "test",
      "org.mockito" % "mockito-all" % "1.8.5" % "test"
    ),

    resolvers += "twitter repo" at "http://maven.twttr.com",

    publishM2Configuration <<= (packagedArtifacts, checksums in publish, ivyLoggingLevel) map { (arts, cs, level) =>
      Classpaths.publishConfig(arts, None, resolverName = m2Repo.name, checksums = cs, logging = level)
    },
    publishM2 <<= Classpaths.publishTask(publishM2Configuration, deliverLocal),
    otherResolvers += m2Repo,

    scalacOptions ++= Seq("-encoding", "utf8"),
    scalacOptions += "-deprecation",

    javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),
    javacOptions in doc := Seq("-source", "1.6"),

    // This is bad news for things like com.twitter.util.Time
    parallelExecution in Test := false,

    // Sonatype publishing
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    publishMavenStyle := true,
    pomExtra := (
      <url>https://github.com/twitter/util</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0</url>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:twitter/util.git</url>
        <connection>scm:git:git@github.com:twitter/util.git</connection>
      </scm>
      <developers>
        <developer>
          <id>twitter</id>
          <name>Twitter Inc.</name>
          <url>https://www.twitter.com/</url>
        </developer>
      </developers>),
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    }
  )

  lazy val util = Project(
    id = "util",
    base = file("."),
    settings = Project.defaultSettings ++
      sharedSettings ++
      Unidoc.settings
  ) aggregate(
    utilCore, utilCodec, utilCollection, utilReflect,
    utilLogging, utilThrift, utilHashing, utilJvm, utilZk,
    utilZkCommon, utilClassPreloader, utilBenchmark, utilApp
  )

  lazy val utilCore = Project(
    id = "util-core",
    base = file("util-core"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "util-core",
    libraryDependencies += "com.twitter.common" % "objectsize" % "0.0.10" % "test",
    libraryDependencies <+= scalacheck,
    libraryDependencies <++= parserCombinators,
    resourceGenerators in Compile <+=
      (resourceManaged in Compile, name, version) map { (dir, name, ver) =>
        val file = dir / "com" / "twitter" / name / "build.properties"
        val buildRev = Process("git" :: "rev-parse" :: "HEAD" :: Nil).!!.trim
        val buildName = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date)
        val contents = (
          "name=%s\nversion=%s\nbuild_revision=%s\nbuild_name=%s"
        ).format(name, ver, buildRev, buildName)
        IO.write(file, contents)
        Seq(file)
      }
  )

  lazy val utilEval = Project(
    id = "util-eval",
    base = file("util-eval"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "util-eval",
    crossScalaVersions ~= { versions => versions filter (_ != "2.11.2") },
    libraryDependencies <+= scalaVersion { "org.scala-lang" % "scala-compiler" % _ % "compile" }
  ).dependsOn(utilCore)

  lazy val utilCodec = Project(
    id = "util-codec",
    base = file("util-codec"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "util-codec",
    libraryDependencies ++= Seq(
      "commons-codec" % "commons-codec" % "1.6"
    )
  ).dependsOn(utilCore)

  lazy val utilCollection = Project(
    id = "util-collection",
    base = file("util-collection"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "util-collection",
    libraryDependencies ++= Seq(
      // NB: guava has a `provided` dep on jsr/javax packages, so we include them manually
      "com.google.code.findbugs" % "jsr305"              % "1.3.9",
      "javax.inject"             % "javax.inject"        % "1",
      "com.google.guava"         % "guava"               % "16.0.1",
      "commons-collections"      % "commons-collections" % "3.2.1"
    )
  ).dependsOn(utilCore)

  lazy val utilReflect = Project(
    id = "util-reflect",
    base = file("util-reflect"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "util-reflect",
    libraryDependencies ++= Seq(
      "asm"   % "asm"         % "3.3.1",
      "asm"   % "asm-util"    % "3.3.1",
      "asm"   % "asm-commons" % "3.3.1",
      "cglib" % "cglib"       % "2.2"
    )
  ).dependsOn(utilCore)

  lazy val utilLogging = Project(
    id = "util-logging",
    base = file("util-logging"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "util-logging",
    libraryDependencies <+= scalatest
  ).dependsOn(utilCore, utilApp)

  lazy val utilThrift = Project(
    id = "util-thrift",
    base = file("util-thrift"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "util-thrift",
    libraryDependencies ++= Seq(
      "thrift"                     % "libthrift"        % "0.5.0",
      "org.slf4j"                  % "slf4j-nop"        % "1.5.8" % "provided",
      "com.fasterxml.jackson.core" % "jackson-core"     % "2.3.1",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.3.1"
    )
  ).dependsOn(utilCodec)

  lazy val utilHashing = Project(
    id = "util-hashing",
    base = file("util-hashing"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "util-hashing",
    libraryDependencies ++= Seq(
      "commons-codec" % "commons-codec" % "1.6" % "test"
    )
  ).dependsOn(utilCore % "test")

  lazy val utilJvm = Project(
    id = "util-jvm",
    base = file("util-jvm"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "util-jvm"
  ).dependsOn(utilApp, utilCore, utilLogging % "test")

  lazy val utilZk = Project(
    id = "util-zk",
    base = file("util-zk"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "util-zk",
    libraryDependencies ++= Seq(
      zkDependency
    )
  ).dependsOn(utilCore, utilCollection, utilLogging)

  lazy val utilZkCommon = Project(
    id = "util-zk-common",
    base = file("util-zk-common"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "util-zk-common",
    libraryDependencies ++= Seq(
      "com.twitter.common.zookeeper" % "client"     % "0.0.60",
      "com.twitter.common.zookeeper" % "group"      % "0.0.78",
      "com.twitter.common.zookeeper" % "server-set" % "1.0.83",
      zkDependency
    )
  ).dependsOn(utilCore, utilLogging, utilZk,
    // These are dependended on to provide transitive dependencies
    // that would otherwise cause incompatibilities. See above comment.
    utilCollection, utilHashing
  )

  lazy val utilClassPreloader = Project(
    id = "util-class-preloader",
    base = file("util-class-preloader"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "util-class-preloader"
  ).dependsOn(utilCore)

  lazy val utilBenchmark = Project(
    id = "util-benchmark",
    base = file("util-benchmark"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "util-benchmark",
    libraryDependencies ++= Seq(
      "com.google.caliper" % "caliper" % "0.5-rc1"
    )
  ).dependsOn(utilCore, utilJvm)

  lazy val utilApp = Project(
    id = "util-app",
    base = file("util-app"),
    settings = Project.defaultSettings ++
      sharedSettings
  ).settings(
    name := "util-app"
  ).dependsOn(utilCore)
}
