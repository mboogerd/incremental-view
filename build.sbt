import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

lazy val root = project.in(file("."))
  .settings(
    inThisBuild(List(
      organization := "com.github.mboogerd",
      scalaVersion := "2.12.2",
      version := "0.1.0-SNAPSHOT"
    )),
    name := "varview")
  .settings(GenericConf.settings())
  .settings(DependenciesConf.common)
  .settings(DependenciesConf.akka)
  .settings(multiJvmSettings: _*)
  .configs(MultiJvm)
  .settings(LicenseConf.settings)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(TutConf.settings)
  .enablePlugins(TutPlugin)