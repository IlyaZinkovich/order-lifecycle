package app

import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import app.Order.{Assign, CourierId, OrderId}
import app.OrderManagementSystem.PlaceOrder

import scala.concurrent.duration._

object Main extends App {

  val system: ActorSystem[OrderManagementSystem.Command] =
    ActorSystem(OrderManagementSystem(), "oms")

  val orderId: OrderId = UUID.randomUUID().toString
  val courierId: CourierId = UUID.randomUUID().toString
  //  system ! PlaceOrder(orderId)
  //  system ! ForwardToOrder(orderId, Assign(courierId))
  (1 to 10000) foreach { i =>
    system ! PlaceOrder(i.toString)
    system ! OrderManagementSystem.ForwardToOrder(i.toString, Assign(UUID.randomUUID().toString))
  }
}

object OrderManagementSystem {

  sealed trait Command

  final case class PlaceOrder(orderId: String) extends Command

  final case class ForwardToOrder(orderId: String, command: Order.Command) extends Command

  final case class OrderTerminated(orderId: OrderId) extends Command

  def apply(orders: Map[OrderId, ActorRef[Order.Command]] = Map()): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case PlaceOrder(orderId) =>
        val order = context.spawn(Order.placed(orderId), s"order-$orderId")
        context.watchWith(order, OrderTerminated(orderId))
        apply(orders + (orderId -> order))
      case ForwardToOrder(orderId, command) =>
        orders.get(orderId) match {
          case Some(order) => order ! command
          case None => context.log.warn(s"Cannot find order $orderId for command $command")
        }
        Behaviors.same
      case OrderTerminated(orderId) =>
        val order = context.spawn(Order.apply(orderId), s"order-$orderId")
        context.watchWith(order, OrderTerminated(orderId))
        apply(orders - orderId + (orderId -> order))
      case _ => Behaviors.unhandled
    }
  }
}

// Replace Actor with ADT
object Order {

  type OrderId = String
  type CourierId = String

  sealed trait Command

  final case class Assign(courierId: CourierId) extends Command

  final case class AssignmentTimeout() extends Command

  final case class UnAssign() extends Command

  final case class PickUp() extends Command

  final case class DropOff() extends Command

  final case class Cancel() extends Command

  def apply(orderId: OrderId): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.same
  }

  def placed(orderId: OrderId): Behavior[Command] = Behaviors.setup { context =>
    context.log.info(s"Order $orderId is placed")
    Behaviors.withTimers[Command] { timers =>
      // send for assignment
      val timeout = 5.minutes
      context.log.info(s"Assignment will time-out in ${timeout.toSeconds} seconds")
      timers.startSingleTimer(AssignmentTimeout(), timeout)
      Behaviors.receiveMessage {
        case Assign(courierId) =>
          context.log.info(s"Order $orderId is assigned to courier $courierId")
          assigned(orderId, courierId)
        case AssignmentTimeout() =>
          context.log.info(s"Order $orderId assignment timed-out")
          Behaviors.stopped
        case Cancel() =>
          context.log.info(s"Order $orderId is cancelled")
          Behaviors.stopped
        case unexpectedCommand =>
          context.log.warn(s"Unexpected command $unexpectedCommand for order $orderId in placed state")
          Behaviors.unhandled
      }
    }
  }

  def assigned(orderId: OrderId, courierId: CourierId): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case PickUp() =>
        context.log.info(s"Order $orderId is picked-up by courier $courierId")
        pickedUp(orderId, courierId)
      case UnAssign() =>
        context.log.info(s"Order $orderId is un-assigned from courier $courierId")
        placed(orderId)
      case Cancel() =>
        context.log.info(s"Order $orderId is cancelled by customer, un-assigning courier $courierId")
        // un-assign courier
        Behaviors.stopped
      case AssignmentTimeout() =>
        context.log.info(s"Order $orderId is assigned to $courierId before timeout")
        Behaviors.same
      case unexpectedCommand =>
        context.log.warn(s"Unexpected command $unexpectedCommand for order $orderId in assigned to courier $courierId state")
        Behaviors.unhandled
    }
  }

  def pickedUp(orderId: OrderId, courierId: CourierId): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.receiveMessage {
      case DropOff() =>
        context.log.info(s"Order $orderId is dropped-off by courier $courierId")
        Behaviors.stopped
      case Cancel() =>
        context.log.info(s"Order $orderId is cancelled bu customer, un-assigning courier $courierId")
        Behaviors.stopped
      case AssignmentTimeout() =>
        context.log.info(s"Order $orderId is picked-up by $courierId before assignment timeout")
        Behaviors.same
      case unexpectedCommand =>
        context.log.warn(s"Unexpected command $unexpectedCommand for order $orderId in picked-up by courier $courierId state")
        Behaviors.unhandled
    }
  }
}
