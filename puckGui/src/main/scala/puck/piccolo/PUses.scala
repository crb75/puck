package puck.piccolo

import java.beans.{PropertyChangeEvent, PropertyChangeListener}

import org.piccolo2d.PNode
import org.piccolo2d.extras.nodes.PComposite
import puck.graph.NodeIdP
import puck.piccolo.util.{Arrow, Circle, FullTriangle}

import scala.collection.mutable

/**
  * Created by lorilan on 6/3/16.
  */

//object PUses {
//
//
//  def apply(source : DGPNode,
//            target : DGPNode) : PUses = {
//    val u = new PUses(source, target)
//
//    u
//  }
//}

case class PUses(source : DGPNode,
                 target : DGPNode /*,
            virtuality : Option[Int]*/)
  extends PComposite {

  def addArrow() : Unit = {

    val headStyle =
      if(usesSet.size > 1) Circle
      else FullTriangle

   val arrow  = Arrow(
      source.arrowGlobalBounds.getCenter2D,
      target.arrowGlobalBounds.getCenter2D,
     headStyle)
    this addChild arrow
  }

  val usesSet = mutable.Set[NodeIdP]((source.id, target.id))


  addArrow()

  {
    val listener = new PropertyChangeListener() {
      def propertyChange(evt: PropertyChangeEvent): Unit = {
        PUses.this.removeAllChildren()
        addArrow()
      }
    }
    for {
      exty <- List(source, target)
      pty <-
      List(PNode.PROPERTY_TRANSFORM,
            PNode.PROPERTY_BOUNDS,
        PNode.PROPERTY_FULL_BOUNDS,
        PNode.PROPERTY_PAINT)
    }
    exty.toPNode.addPropertyChangeListener(pty,listener)

  }

  def delete(): Unit =
    if(getParent != null){
      source.usesOf -= this
      target.usedBy -= this
      getParent removeChild this
    }

}
