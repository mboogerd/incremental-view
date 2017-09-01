package com.github.mboogerd.varview

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import cats.Monoid
import com.github.mboogerd.varview.VarSubscriber.Changed
import com.twitter.util.{Updatable, Var}

/**
  *
  */
object VarSubscriber {
  private def apply[T](updatable: Updatable[T]): Props = Props(new VarSubscriber[T](updatable))
  def apply[T: Monoid](implicit system: ActorSystem): (ActorRef, Var[T]) = {
    val variable = Var(implicitly[Monoid[T]].empty)
    val actor = system.actorOf(VarSubscriber(variable))
    (actor, variable)
  }
  case class Changed[T](key: String)(val value: T)
}
class VarSubscriber[T](updatable: Updatable[T]) extends Actor {
  override def receive: Receive = {
    case changed: Changed[T @unchecked] ⇒
      updatable.update(changed.value)
  }
}
