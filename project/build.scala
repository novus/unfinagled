import sbt._
import Keys._

object Build extends sbt.Build {

  def local(name: String) = LocalProject("unfinagled-" + name)

  lazy val root =
    project(id = "unfinagled",
            base = file(".")) aggregate(core, scalatest)

  lazy val core =
    project(id = "unfinagled-core",
            base = file("unfinagled-core"),
            settings = Seq(
              unmanagedClasspath in (local("core"), Test) <++= (fullClasspath in (local("scalatest"), Test)),
              libraryDependencies ++= Seq(
                "com.twitter" %% "finagle-http" % "6.5.1",
                "net.databinder" %% "unfiltered-netty" % "0.6.8"
              )
            ))

  lazy val scalatest =
    project(id = "unfinagled-scalatest",
            base = file("unfinagled-scalatest"),
            settings = Seq(
              unmanagedClasspath in (local("scalatest"), Compile) <++= (fullClasspath in (local("core"), Compile)),
              libraryDependencies ++= Seq(
                "com.twitter" %% "finagle-http" % "6.5.1",
                "net.databinder" %% "unfiltered-scalatest" % "0.6.8"
              )
            ))
            
  def project(id: String, base: File, settings: Seq[Project.Setting[_]] = Nil) =
    Project(id = id,
            base = base,
            settings = Project.defaultSettings ++ Shared.settings ++ sbtrelease.ReleasePlugin.releaseSettings ++ settings)
}

object Shared {

  val settings = Seq(
    organization := "com.novus",
    scalaVersion := "2.10.1",
    crossScalaVersions := Seq("2.9.2", "2.10.1"),
    scalacOptions := Seq("-deprecation", "-unchecked"),
    resolvers ++= Seq("Novus Nexus Public" at "https://nexus.novus.com:65443/nexus/content/groups/public/"),
    initialCommands := "import com.novus.unfinagled._",
    shellPrompt := ShellPrompt.buildShellPrompt,
    publishTo <<= (version) { version: String =>
      val sfx =
        if (version.trim.endsWith("SNAPSHOT")) "snapshots"
        else "releases"
      val nexus = "https://nexus.novus.com:65443/nexus/content/repositories/"
      Some("Novus " + sfx at nexus + sfx + "/")
    },
    credentials += Credentials(Path.userHome / ".ivy2" / ".novus_nexus")
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
        currProject, currBranch /*, BuildSettings.buildVersion*/
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
