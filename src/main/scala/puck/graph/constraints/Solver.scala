package puck.graph
package constraints

import puck.PuckError
import puck.graph.transformations.TransformationRules
import puck.util.{PuckLog, PuckLogger}

import scalaz.{Failure, Success}

import scalaz.Validation.FlatMap._

trait Solver {

  implicit val defaultVerbosity : PuckLog.Verbosity = (PuckLog.Solver, PuckLog.Info)
  import scala.language.implicitConversions
  implicit def logVerbosity(lvl : PuckLog.Level) = (PuckLog.Solver, lvl)

  val logger : PuckLogger
  val decisionMaker : DecisionMaker
  val rules : TransformationRules

  val automaticConstraintLoosening : Boolean

  type GraphT = DependencyGraph
  type ResT = ResultT
  type NIdT = NodeId
  type PredicateT = decisionMaker.PredicateT


  def redirectTowardExistingAbstractions(graph : GraphT,
                                         used : ConcreteNode,
                                         wrongUsers : Seq[NIdT])
                                        (k : (GraphT, Seq[NIdT]) => Unit) : Unit = {
    decisionMaker.abstractionKindAndPolicy(graph, used) {
      case Some((absKind, absPolicy)) =>

        logger.writeln("redirect toward existing abstractions")
        val (g3, allUnsolved) = wrongUsers.foldLeft((graph, Seq[NIdT]())) {
          case ((g, unsolved), wu) =>
          g.abstractions(used.id) find {
            case (nid, `absPolicy`) =>
              val cn = g.getConcreteNode(nid)
              cn.kind == absKind && !graph.interloperOf(wu, nid)
            case _ => false
          } match {
            case None => (g, wu +: unsolved)
            case Some((abs, _)) =>
              logger.writeln(s"$wu will use abstraction $abs")

              //val breakPoint = g.startSequence()

              rules.redirectUsesOf(g, DGEdge.uses(wu, used.id), abs, absPolicy) match {
                  case Success((_, g2)) => (g2, unsolved)
                  case Failure(e) =>
                    logger.writeln("redirection error catched !!")(PuckLog.Debug)
                    (g/*.undo(breakPoint)*/, wu +: unsolved)
              }
          }
        }
        k(g3, allUnsolved)
      case None =>k(graph, wrongUsers)
    }
  }

  var newCterNumGen = 0

  private val allwaysTrue : PredicateT = (_,_) => true

  trait FindHostResult
  case class Host(host : NIdT, graph : GraphT) extends FindHostResult
  case class FindHostError() extends DGError with FindHostResult

  def findHost(graph : GraphT,
               toBeContained : NIdT,
               specificPredicate : PredicateT = allwaysTrue,
               parentsThatCanBeCreated : Int = 1)
              (k : FindHostResult => Unit) : Unit = {

    def predicate(graph : GraphT, n : NIdT) : Boolean =
      graph.canContain(n, toBeContained) && specificPredicate(graph, n)

    def hostIntro(toBeContainedId : NIdT) : Unit = {
      logger.writeln("call hostIntro " + toBeContainedId )(PuckLog.Debug)
      val toBeContained = graph.getNode(toBeContainedId)

      //TODO filter instead of find !!!
      graph.nodeKinds.find(_.canContain(toBeContained.kind)) match {
        case None =>
          logger.write("do not know how to create a valid host for " + toBeContained.kind)(PuckLog.Debug)
          k(FindHostError())
        case Some(hostKind) =>
          newCterNumGen += 1
          val hostName = s"${toBeContained.name}_container$newCterNumGen"
          val (hid, graph2) = graph.addConcreteNode(hostName, hostKind, NoType)
          logger.writeln("creating " + hostName )(PuckLog.Debug)
          logger.writeln("host intro, rec call to find host " + parentsThatCanBeCreated )
          findHost(graph2, hid, allwaysTrue, parentsThatCanBeCreated - 1){
            case Host(hostsHostId, graph3) =>
              k(Host(hid, graph3.addContains(hostsHostId, hid)))
            case FindHostError() =>
              logger.writeln("host intro transmit find host error" )(PuckLog.Error)
              k(FindHostError())

          }
      }
    }

    logger.writeln("find host for "+ toBeContained + ", call to choose Node "+ parentsThatCanBeCreated)(PuckLog.Debug)
    // with the search engine, all solutions will be explored anyway
    decisionMaker.chooseNode(graph, predicate){
      case None =>
        if(parentsThatCanBeCreated == 0){
          val msg = "host intro, ancestor's max limit is not enough"
          logger.writeln(msg)(PuckLog.Warning)
          k(FindHostError())
        }
        else {
          logger.writeln("find host, no node given by decision maker : call to host intro")(PuckLog.Debug)
          hostIntro(toBeContained)
        }
      case Some(h) =>
        logger.writeln("find host: decision maker chose " + h + " to contain " + toBeContained)
        k(Host(h, graph))
    }
  }

  def absIntroPredicate(graph : GraphT,
                        impl : NIdT,
                        absPolicy : AbstractionPolicy,
                        absKind : NodeKind) : PredicateT = absPolicy match {
    case SupertypeAbstraction =>
      (graph, potentialHost) => !graph.interloperOf(impl, potentialHost)

    case DelegationAbstraction =>
      (graph, potentialHost) => !graph.interloperOf(potentialHost, impl)
  }

  def absIntro(graph : GraphT,
               impl : ConcreteNode,
               wrongUsers : Seq[NIdT],
               degree : Int = 1)
               (k : Try[GraphT] => Unit) : Unit = {

    logger.writeln("abs of "+ impl +" intro degree "+degree)


    def aux(graph : GraphT, deg : Int, currentImpl : ConcreteNode)
           (k : Try[(GraphT, ConcreteNode, AbstractionPolicy)] => Unit) : Unit = {
      logger.writeln(s"*** abs intro degree $deg/$degree ***")

      decisionMaker.abstractionKindAndPolicy(graph, currentImpl){
        case Some((absKind, absPolicy)) =>

          def doIntro(k: Try[(GraphT, ConcreteNode, AbstractionPolicy)] => Unit) : Unit = {
            logger.writeln(s"trying to create abstraction( $absKind, $absPolicy ) of $currentImpl")

            val tryAbs = rules.createAbstraction(graph, currentImpl, absKind, absPolicy)

            if(tryAbs.isFailure)
              logger.writeln("failure !!")

            tryAbs map {
              case (abs, graph2) =>
                logger.writeln("in solver "+ abs + " introduced as "+ absPolicy + " for " + currentImpl)((PuckLog.ConstraintSearch, PuckLog.Debug))

                logger.writeln(s"Searching host for abstraction( $absKind, $absPolicy ) of $currentImpl")

                findHost(graph2, abs.id,
                  absIntroPredicate(graph2, currentImpl.id, absPolicy, absKind)) {
                  case Host(h, graph3) =>
                    logger.writeln(s"absIntro : host of $abs is $h")

                    val graph4 = graph3.addContains(h, abs.id)

                    //TODO check if can find another way more generic
                    //whitout passing by abstracting the container
                    val graph5 = rules.abstractionCreationPostTreatment(graph4, currentImpl.id, abs.id, absPolicy)

                    k(Success((graph5, abs, absPolicy)))
                  case FindHostError() =>
                    logger.writeln("error while searching host for abstraction")
                    k(Failure(FindHostError()).toValidationNel)
                }
            }
            ()
          }

          if (deg == degree)
            doIntro(k)
          else
            doIntro({
              case Failure(_) =>
                k(Failure(new DGError(s"Single abs intro degree $deg/$degree error (currentImpl = $currentImpl)")).toValidationNel)
              case Success((g, abs, _)) => aux(g, deg + 1, abs)(k)
            })

        case None => k(Failure(new DGError(s"no abstraction for impl of kind $currentImpl")).toValidationNel)
      }
    }


    aux(graph, 1, impl) {
      case Success((g, abs, absPolicy)) =>

        logger.writeln("redirecting wrong users !!")
        val res = wrongUsers.foldLeft(Success(g) : Try[GraphT]){
          case (Failure(exc), wuId) => Failure(exc)
          case (Success(g0), wuId) =>
            rules.redirectUsesOf(g0, DGEdge.uses(wuId, impl.id), abs.id, absPolicy) match {
              case Success((_, g1)) => Success(g1)
              case Failure(e) => Failure(e)
            }
        }
        k(res)
      case Failure(e) => k(Failure(e))

    }

  }

  def solveUsesToward(graph : GraphT, impl : ConcreteNode, k : Try[GraphT] => Unit) : Unit = {
    val implId = impl.id
    logger.writeln("###################################################")
    logger.writeln(s"##### Solving uses violations toward $implId ######")

    redirectTowardExistingAbstractions(graph, impl, graph.wrongUsers(implId)){
      (graph2, wrongUsers) =>
      if (wrongUsers.nonEmpty){
        absIntro(graph2, impl, wrongUsers){
          case Failure(_) =>
            //dead code : en acceptant qu'une abstraction nouvellement introduite
            //soit la cible de violation, on a jamais besoin d'utiliser le degré 2
            absIntro(graph2, impl, wrongUsers, 2){
              case Failure(e) => k(Failure(e))

                /*decisionMaker.modifyConstraints(LiteralNodeSet(wrongUsers), impl)
                if(impl.wrongUsers.nonEmpty)
                throw new AGError ("cannot solve uses toward " + impl)
                k ()*/
              case sg => k (sg)
            }
          case sg => k (sg)
        }
      }
      else k(Success(graph2))
    }
  }

  def solveContains(graph : GraphT,
                    wronglyContainedNode : ConcreteNode,
                    k : Try[GraphT] => Unit) : Unit = {
    val wronglyContainedId = wronglyContainedNode.id
    logger.writeln("###################################################")
    logger.writeln(s"##### Solving contains violations toward $wronglyContainedId ######")

    // detach for host searching : do not want to consider parent constraints
    val oldCter = graph.container(wronglyContainedId).get

    val graphWithoutContains = graph.removeContains(oldCter, wronglyContainedId, register = false)

    findHost(graphWithoutContains, wronglyContainedId,
      (graph : GraphT, potentialHost: NIdT) =>
        !graph.interloperOf(potentialHost, wronglyContainedId)) {
       case Host(newCter, graph2) =>

        logger.writeln("solveContains : host of "+ wronglyContainedId +" will now be " + newCter)


         //re-attach before moving
         val tryGraph3 = rules.moveTo(graph2.addContains(oldCter, wronglyContainedId, register = false),
                        wronglyContainedId, newCter)

          val tryGraph4 = tryGraph3.flatMap {graph3 =>

          (graph3.isWronglyContained(wronglyContainedId), automaticConstraintLoosening) match {
            case (false, _) => Success(graph3)
            case (true, true) => Success(rules.addHideFromRootException (graph3, wronglyContainedId, newCter))
            case (true, false) => Failure(new PuckError("constraint unsolvable")).toValidationNel
          }
        }

        logger.writeln("solveContains : calling k()")
        k(tryGraph4)
       case FindHostError() => k(Failure(new DGError("FindHostError caught")).toValidationNel)
    }
  }


  def solveViolationsToward(graph : GraphT, target : ConcreteNode) (k: Try[GraphT] => Unit ) = {
    def end: Try[GraphT] => Unit = {
      case Success(g) =>
      logger.writeln("solveViolationsToward$end")
      if (g.wrongUsers(target.id).nonEmpty)
        solveUsesToward(g, target, k)
      else
        k(Success(g))
      case noRes =>k(noRes)
    }

    if(graph.isWronglyContained(target.id))
      solveContains(graph, target, end)
    else
      end(Success(graph))
  }

  def doMerges(graph : GraphT, k : Try[ResT] => Unit) : Unit = {

    def aux(graph : GraphT, it : Iterator[NIdT]) : Try[GraphT] =
      if(it.hasNext){
        val n = it.next()
        rules.findMergingCandidate(graph, n) match {
          case Some(other) =>
            rules.merge(graph, other, n) flatMap {
              g1 => aux(g1, g1.nodesId.iterator)
            }

          case None => aux(graph, it)
        }
      }
      else Success(graph)

    k(aux(graph, graph.nodesId.iterator) map (g => (g, g.recording)))

  }

  def solve(graph : GraphT, k : Try[ResT] => Unit) : Unit = {
    /*logger.writeln("solve begins !")
    val sortedId = graph.nodesId.toSeq.sorted
    sortedId.foreach{id => logger.writeln("("+ id + ", " + graph.container(id)+ ")")}
    val nodes = graph.nodes.toSeq.sortBy(_.id)
    nodes foreach {n => logger.writeln(n)}
*/
    def aux: Try[GraphT] => Unit = {
      case Success(g) =>
      decisionMaker.violationTarget(g) {
        case None => doMerges(g, k)
        case Some(target) =>
          solveViolationsToward(g, target)(aux)
      }
      case Failure(e) => k(Failure(e))
    }

    aux(Success(graph))
  }


}
