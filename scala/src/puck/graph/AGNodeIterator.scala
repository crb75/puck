package puck.graph

import _root_.java.util.NoSuchElementException
import scala.collection.mutable

/**
 * Created by lorilan on 13/05/14.
 */
/*
  implements a width cross (parcours en largeur ?) of the graph via the contains tree
 */
class AGNodeIterator (private var nextOne : AGNode) extends Iterator[AGNode]{

  private val nexts : mutable.Queue[AGNode] = mutable.Queue[AGNode]()

  nexts ++= nextOne.content
/*  println("next one is "+ nextOne)
  if(nextOne.content.nonEmpty){
  println("adding to queue :")
  println(nextOne.content.mkString("-->", "\n-->", "\n<end of adding>"))}*/


  override def hasNext : Boolean = nexts.nonEmpty || !(nextOne == null)

  override def next() : AGNode = {
    val n = nextOne

    try{
      nextOne = nexts.dequeue()
      nexts ++= nextOne.content
      /*println("next one is "+ nextOne)
      if(nextOne.content.nonEmpty){
        println("adding to queue :")
        println(nextOne.content.mkString("-->", "\n-->", "<end of adding>"))}*/

    } catch {
      case e : NoSuchElementException => nextOne = null
    }

    n
  }
}
