package puck.search

import puck._
import puck.graph.LoggedTry

import scala.collection.mutable
import scalaz.{-\/, \/-}

trait Search[Result]{
  def initialState : SearchState[Result]
  def successes : Seq[SearchState[Result]]
  def failures : Seq[SearchState[Result]]
  def exploredStates : Int

  def failuresByDepth : Map[Int, Seq[SearchState[Result]]] =
    failures.foldLeft(Map[Int, Seq[SearchState[Result]]]()){
      (m, state) =>
        val seq = m.getOrElse(state.depth, Seq())
        m + (state.depth -> (state +: seq))
    }

  def allStatesByDepth : Map[Int, Seq[SearchState[Result]]] =
    initialState.iterator.foldLeft(Map[Int, Seq[SearchState[Result]]]()){
      (m, state) =>
        val seq = m.getOrElse(state.depth, Seq())
        m + (state.depth -> (state +: seq))
    }
}


class SearchEngine[T]
( val searchStrategy: SearchStrategy[T],
  val control : SearchControl[T],
  val maxResult : Option[Int] = None,// default = all result
  val evaluator : Option[Evaluator[T]] = None
  ) extends Search[T] {

  val successes = mutable.ListBuffer[SearchState[T]]()
  val failures = mutable.ListBuffer[SearchState[T]]()

  val initialState : SearchState[T] = control.initialState


  val enoughSuccess : () => Boolean =
    maxResult match {
      case None => () => false
      case Some(i) => () => successes.length >= i
    }

  private [this] var idSeed : Int = 0
  private def idGen() : Int = {idSeed += 1; idSeed}

  val storeSuccess : SearchState[T] => Unit =
    evaluator match {
      case None => resState =>
        ignore(successes += resState)
      case Some(ev) =>
        resState =>
          if(successes.forall(!ev.equals(_, resState)))
            ignore(successes += resState)
    }

  def storeResult(resState : SearchState[T]) : Unit = {
    ignore(resState.loggedResult.value match {
      case -\/(err) => failures += resState
      case \/-(g) => storeSuccess(resState)

    })

  }

  protected var numExploredStates = 0

  def addState(cr : LoggedTry[T], choices : Seq[LoggedTry[T]]) : Unit = {
    numExploredStates = numExploredStates + 1

    if(choices.isEmpty)
      storeResult(searchStrategy.currentState.createNextState(cr, Seq()))
    else searchStrategy.addState(cr, choices)
  }

  def init() : Unit = ()

  def exploredStates = numExploredStates

  def oneStep() : Unit= {
    searchStrategy.nextState.nextChoice foreach (lt =>
      lt.value match {
        case \/-(cc) => addState(lt, control.nextStates(cc) map (ltnext => lt.log <++: ltnext))
        case _ =>  addState(lt, Seq())
      })
  }

  def explore() : Unit =
    if(initialState.choices.isEmpty)
      storeResult(initialState)
    else {
      searchStrategy.addState(initialState)
      numExploredStates = 1

      do oneStep()
      while(searchStrategy.canContinue && !enoughSuccess())
    }


}

trait SearchControl[T]{
  //def produce(t : T) : Seq[LoggedTry[T]]
  def initialState : SearchState[T]
  def nextStates(t : T) : Seq[LoggedTry[T]]
}

trait SearchStrategy[T] {
  def addState(s : SearchState[T]) : Unit
  def addState(currentResult : LoggedTry[T], choices : Seq[LoggedTry[T]]) : Unit
  def canContinue : Boolean
  def currentState : SearchState[T]
  def nextState: SearchState[T]
}



