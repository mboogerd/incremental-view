package com.github.mboogerd.varview

import akka.actor.{ActorRef, ActorSystem}
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler}
import com.twitter.util.{Extractable, Updatable, Var}
import org.scalatest._

import scala.collection.immutable.Seq
import cats.instances.list._
import cats.instances.int._
import org.scalatest.concurrent.Eventually

class VarviewSpec extends FlatSpec with Matchers with Eventually {

  behavior of "varview"

  it should "be updatable and extractable" in {
    val variable: Var[Long] with Updatable[Long] with Extractable[Long] = Var(1L)

    variable() shouldBe 1L
    variable.update(2L)
    variable() shouldBe 2L
  }

  it should "be functorial" in {
    val variable: Var[Long] with Updatable[Long] with Extractable[Long] = Var(1L)
    val mapped: Var[Long] = variable.map(_ * 5)

    mapped.sample() shouldBe 5L

    variable.update(2L)
    mapped.sample() shouldBe 10L
  }

  it should "be monadic" in {
    val var1: Var[Long] with Updatable[Long] with Extractable[Long] = Var(1L)
    val var2: Var[Long] with Updatable[Long] with Extractable[Long] = Var(1L)
    val flatMapped: Var[Long] = for {
      v1 ← var1
      v2 ← var2
    } yield v1 * v2

    flatMapped.sample() shouldBe 1L

    var1.update(2L)
    flatMapped.sample() shouldBe 2L

    var2.update(2L)
    flatMapped.sample() shouldBe 4L
  }


  it should "allow incremental folds" in {
    val zeroToNine = 0 to 9
    val variables: List[Var[Int] with Updatable[Int] with Extractable[Int]] = zeroToNine.map(i ⇒ Var.apply(i)).toList

    import com.twitter.util.IncrementalOps._

    val reduced = variables.reduceIncremental
    reduced.sample() shouldBe zeroToNine.sum

    variables(5).update(100)
    reduced.sample() shouldBe (zeroToNine.sum - 5 + 100)
  }


  it should "allow incremental folds of maps" in {
    import Implicits._
    import com.twitter.util.IncrementalOps._

    type View = Map[String, Map[String, Int]]
    val v1 = Var(Map("service1" → Map("version1" → 10, "version2" → 20)))
    val v2 = Var(Map("service2" → Map("version1" → 10, "version2" → 20)))
    val v3 = Var(Map("service1" → Map("version1" → 1, "version2" → 2), "service2" → Map("version1" → 1, "version2" → 2)))
    val variables: List[Var[View] with Updatable[View]] = List(v1, v2, v3)

    val reduced = variables.reduceIncremental
    reduced.sample() shouldBe Map(
      "service1" -> Map("version1" -> 11, "version2" -> 22),
      "service2" -> Map("version1" -> 11, "version2" -> 22)
    )

    // Remove service1, remove a version of service2, update another version of service2, and add a version also
    v3.update(Map(
      "service2" → Map("version2" → 20, "version3" → 30),
      "service3" → Map.empty)
    )
    reduced.sample() shouldBe Map(
      "service1" -> Map("version1" -> 10, "version2" -> 20),
      "service2" -> Map("version1" -> 10, "version2" -> 40, "version3" -> 30),
      "service3" -> Map()
    )
  }

  it should "work for set once trivially translated to multi-set" in {
    import Implicits._
    import com.twitter.util.IncrementalOps._

    // We represent a Set[K] as a MultiSet[K], implemented as Map[K, Int] (where value means instance-count)
    type View = Map[String, Set[Int]]
    val v1 = Var(Map("service1" → Set(1100, 1200, 1500)))
    val v2 = Var(Map("service1" → Set(1000, 1300, 1500)))
    val variables: List[Var[View] with Updatable[View]] = List(v1, v2)

    val reduced = variables.map(_.map(_.mapValues(asMultiSet)))
      .reduceIncremental
      // the map is not incremental... however, a Set view could be provided in O(1) on a MultiSet if we sort the set
      // by arity
      .map(_.mapValues(_.filter(_._2 > 0).keySet))

    reduced.sample() shouldBe Map("service1" → Set(
      1000,
      1100,
      1200,
      1300,
      1500
    ))

    v1.update(Map("service1" → Set(1100, 1200)))
    reduced.sample()("service1") should contain (1500)

    v2.update(Map("service1" → Set(1000, 1300)))
    reduced.sample()("service1") should not contain 1500
  }

  it should "?" in {
    // allow count of values (increments when inserting, decrements when removing)
    // allow sum of values (updates when inserting or removing, updates when updating)
    // allow multiplication of values (idem.)
    // allow an arbitrary polynomial? (idem.)
  }



  it should "integrate via akka streams" in {
    implicit val system = ActorSystem("test")
    implicit val mat = ActorMaterializer()

    def actorIntSource: Source[Int, ActorRef] = Source.actorRef[Int](1, OverflowStrategy.dropBuffer)
    val (actorRef, variable) = actorIntSource.toMat(VarSink[Int])(Keep.both).run()

    actorRef ! 1
    eventually(variable.sample() shouldBe 1)

    actorRef ! 2
    eventually(variable.sample() shouldBe 2)
  }
}