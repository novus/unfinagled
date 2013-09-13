import sbt._
import Keys._

object Build extends sbt.Build {

  lazy val root =
    project(id = "unfinagled",
            base = file(".")) aggregate(core, scalatest, server)

  lazy val core =
    project(id = "unfinagled-core",
            base = file("unfinagled-core"),
            settings = Seq(
              unmanagedClasspath in (local("core"), Test) <++= (fullClasspath in (local("scalatest"), Test)),
              libraryDependencies <<= (scalaVersion) { sv =>
                Seq(
                  finagleDep(sv),
                  "net.databinder" %% "unfiltered-netty" % "0.6.8"
                )
              }
            ))

  lazy val scalatest =
    project(id = "unfinagled-scalatest",
            base = file("unfinagled-scalatest"),
            settings = Seq(
              unmanagedClasspath in (local("scalatest"), Compile) <++= (fullClasspath in (local("core"), Compile)),
              libraryDependencies <<= (scalaVersion) { sv =>
                Seq(
                  finagleDep(sv),
                  "net.databinder" %% "unfiltered-scalatest" % "0.6.8"
                )
              }
            )) dependsOn(server)

  lazy val server =
    project(id = "unfinagled-server",
      base = file("unfinagled-server"),
      settings = Seq(
        libraryDependencies <<= (scalaVersion) { sv =>
          Seq(
            finagleDep(sv),
            "net.databinder" %% "unfiltered-util" % "0.6.8"
          )
        }
      )) dependsOn(core)

  def finagleDep(scalaVersion: String) =
    if(scalaVersion startsWith "2.9.")
      "com.twitter" % "finagle-http_2.9.2" % "6.5.1"
    else
      "com.twitter" %% "finagle-http" % "6.5.1"

  def local(name: String) = LocalProject("unfinagled-" + name)
            
  def project(id: String, base: File, settings: Seq[Project.Setting[_]] = Nil) =
    Project(id = id,
            base = base,
            settings = Project.defaultSettings ++ Shared.settings ++ sbtrelease.ReleasePlugin.releaseSettings ++ settings)
}

object Shared {

  val settings = Seq(
    organization := "com.novus",
    scalaVersion := "2.10.2",
    crossScalaVersions := Seq("2.9.3", "2.10.1", "2.10.2"),
    scalacOptions := Seq("-deprecation", "-unchecked"),
    initialCommands := "import com.novus.unfinagled._",
    shellPrompt := ShellPrompt.buildShellPrompt,
    pomIncludeRepository := { _ => false },
    licenses := Seq("The MIT License (MIT)" -> url("http://www.opensource.org/licenses/mit-license.php")),
    homepage := Some(url("https://github.com/novus/unfinagled")),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomExtra := (
        <scm>
          <url>git@github.com:novus/unfinagled.git</url>
          <connection>scm:git:git@github.com:novus/unfinagled.git</connection>
        </scm>
        <developers>
          <developer>
            <id>chris</id>
            <name>Chris Lewis</name>
            <url>http://www.thegodcode.net/</url>
          </developer>
        </developers>),
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    credentials += Credentials(Path.userHome / ".ivy2" / ".sonatype")
  ) ++ net.virtualvoid.sbt.graph.Plugin.graphSettings ++ Format.settings
  
}

// Shell prompt which show the current project, git branch and build version
object ShellPrompt {
  object devnull extends ProcessLogger {
    def info (s: => String) {}
    def error (s: => String) { }
    def buffer[T] (f: => T): T = f
  }
  def currBranch = (
    ("git status -sb" lines_! devnull headOption)
      getOrElse "-" stripPrefix "## "
    )

  val buildShellPrompt = {
    (state: State) => {
      val currProject = Project.extract (state).currentProject.id
      "[%s](%s)$ ".format (
        currProject, currBranch
      )
    }
  }
}

object Format {

  import com.typesafe.sbtscalariform.ScalariformPlugin
  import ScalariformPlugin._

  lazy val settings = scalariformSettings ++ Seq(
    ScalariformKeys.preferences := formattingPreferences
  )

  lazy val formattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences().
      setPreference(AlignParameters, true).
      setPreference(AlignSingleLineCaseStatements, true).
      setPreference(CompactControlReadability, true).
      setPreference(CompactStringConcatenation, false).
      setPreference(DoubleIndentClassDeclaration, true).
      setPreference(FormatXml, true).
      setPreference(IndentLocalDefs, true).
      setPreference(IndentPackageBlocks, true).
      setPreference(IndentSpaces, 2).
      setPreference(MultilineScaladocCommentsStartOnFirstLine, true).
      setPreference(PreserveSpaceBeforeArguments, false).
      setPreference(PreserveDanglingCloseParenthesis, false).
      setPreference(RewriteArrowSymbols, false).
      setPreference(SpaceBeforeColon, false).
      setPreference(SpaceInsideBrackets, false).
      setPreference(SpacesWithinPatternBinders, true)
  }
}
