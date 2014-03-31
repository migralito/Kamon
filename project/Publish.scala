import sbt._
import sbt.Keys._

object Publish {

  lazy val settings = Seq(
    crossPaths := false,
    pomExtra := kamonPomExtra,
    publishTo <<= kamonPublishTo,
    organization := "io.kamon",
    pomIncludeRepository := { x => false },
    publishMavenStyle := true,
    publishArtifact in Test := false
  )

  def kamonPublishTo: Def.Initialize[Option[Resolver]] = isSnapshot { isSnapshot =>
      if (isSnapshot)
        Some(Resolver.sftp("Kamon Snapshots Repository", "snapshots.kamon.io", "/var/local/snapshots-repo"))
      else
        Some(Resolver.sftp("Kamon Repository", "repo.kamon.io", "/var/local/releases-repo"))
    }

  def kamonPomExtra = {
    <url>http://kamon.io</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        </license>
      </licenses>
      <scm>
        <url>git://github.com/kamon-io/Kamon.git</url>
        <connection>scm:git:git@github.com:kamon-io/Kamon.git</connection>
      </scm>
      <developers>
        <developer><id>ivantopo</id><name>Ivan Topolnjak</name><url>https://twitter.com/ivantopo</url></developer>
        <developer><id>dpsoft</id><name>Diego Parra</name><url>https://twitter.com/diegolparra</url></developer>
      </developers>
  }
}
