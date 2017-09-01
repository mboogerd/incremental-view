package com.github.mboogerd.varview

import cats.kernel.CommutativeGroup
import cats.kernel.instances.MapMonoid

/**
  *
  */
object Implicits {

  implicit def commutativeGroupMap[K, V: CommutativeGroup]: CommutativeGroup[Map[K, V]] = new MapMonoid[K, V] with CommutativeGroup[Map[K, V]] {
    override def inverse(a: Map[K, V]): Map[K, V] = a.mapValues(implicitly[CommutativeGroup[V]].inverse)
  }

  implicit def asMultiSet[E](set: Set[E]): Map[E, Int] = set.map(_ → 1).toMap
}
