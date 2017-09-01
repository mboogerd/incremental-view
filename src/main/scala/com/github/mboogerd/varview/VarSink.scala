package com.github.mboogerd.varview

import akka.stream.{Attributes, Inlet, SinkShape}
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, InHandler}
import cats.Monoid
import com.twitter.util.{Updatable, Var}

/**
  *
  */
object VarSink {
  def apply[T](empty: T = null): VarSink[T] = new VarSink[T](empty)
  def apply[T](implicit m: Monoid[T]): VarSink[T] = new VarSink[T](m.empty)
}
class VarSink[T](initial: T) extends GraphStageWithMaterializedValue[SinkShape[T], Var[T]] {
  val in: Inlet[T] = Inlet("var-sink")
  val variable: Var[T] with Updatable[T] = Var(initial)
  override def shape: SinkShape[T] = SinkShape(in)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Var[T]) =
    (graphStageLogic, variable)

  lazy val graphStageLogic = new GraphStageLogic(shape) {

    // This requests one element at the Sink startup.
    override def preStart(): Unit = pull(in)

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        variable.update(grab(in))
        pull(in)
      }
    })
  }
}
