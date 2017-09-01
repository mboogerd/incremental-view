package com.twitter.util

import java.util.concurrent.atomic.{AtomicReference, AtomicReferenceArray}
import java.util.function.UnaryOperator

import cats.Foldable
import cats.kernel.CommutativeGroup
import cats.syntax.foldable._

/**
  *
  */
object IncrementalOps {

  /**
    * This implicit class exposes pipelineable aggregation operators. I.e. it allows incremental aggregations.
    *
    * @param vars
    * @param ev$1
    * @param ev$2
    * @tparam CC
    * @tparam T
    */
  implicit class IncrementalOps[CC[_]: Foldable, T: CommutativeGroup](vars: CC[_ <: Var[T]]) {

    private val grpT: CommutativeGroup[T] = implicitly[CommutativeGroup[T]]
    import grpT._

    /**
      * Reduces `vars` to a single value. Intermediate updates of `Var`s it depends on are reflected incrementally, i.e.
      * it uses the properties of `T`s CommutativeGroup to first cancel the previous value (combine inverted) and then
      * combine the updated value. This allows efficient maintenance of an aggregate over some collection of variables.
      *
      * @return
      */
    def reduceIncremental: Var[T] = {
      Var.async(empty) { v =>
        val N: Int = vars.size.toInt
        val previousValues = new AtomicReferenceArray[T](N)
        val currentValue = new AtomicReference[T](grpT.empty)

        @volatile var filling = true

        def publish(i: Int, newValue: T): Unit = {
          val oldValue = Option(previousValues.get(i)).getOrElse(empty)
          previousValues.set(i, newValue)
          currentValue.getAndUpdate(new UnaryOperator[T] {
            override def apply(t: T): T = combine(combine(t, inverse(oldValue)), newValue)
          })
          if (!filling) v.update(currentValue.get())
        }

        val closes = new Array[Closable](N)
        var i = 0
        for (v <- vars.toList) {
          val j = i
          closes(j) = v.observe(t ⇒ publish(j, t))
          i += 1
        }

        filling = false
        v.update(currentValue.get())

        Closable.all(closes: _*)
      }
    }
  }
}
