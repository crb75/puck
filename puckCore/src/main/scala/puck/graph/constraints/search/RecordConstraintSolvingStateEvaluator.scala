/*
 * Puck is a dependency analysis and refactoring tool.
 * Copyright (C) 2016 Loïc Girault loic.girault@gmail.com
 *               2016 Mikal Ziane  mikal.ziane@lip6.fr
 *               2016 Cédric Besse cedric.besse@lip6.fr
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *   Additional Terms.
 * Author attributions in that material or in the Appropriate Legal
 * Notices displayed by works containing it is required.
 *
 * Author of this file : Loïc Girault
 */

package puck.graph.constraints.search

import puck.graph._
import puck.graph.comparison.Mapping
import puck.graph.transformations.Transformation
import puck.search.{Evaluator, SearchState}

import scalaz.{-\/, \/-}

class RecordConstraintSolvingStateEvaluator
(val initialRecord : Seq[Transformation])
  extends Evaluator[SResult]{


  def evaluate(s : SearchState[SResult]): Double =
    s.loggedResult.value match {
      case -\/(err) => 0
      case \/-(res) =>
        val g = graphOfResult(res)
        Metrics.nameSpaceCoupling(g)
    }

  def equals(s1 : SearchState[SResult], s2 : SearchState[SResult] ): Boolean =
    (s1.loggedResult.value, s2.loggedResult.value) match {
      case (\/-(res1), \/-(res2)) =>
        DependencyGraph.areEquivalent(initialRecord,
          graphOfResult(res1),
          graphOfResult(res2))
      case _ => false
    }



}


class GraphConstraintSolvingStateEvaluator(f : DependencyGraph => Double)
  extends Evaluator[SResult]{

  def evaluate(s : SearchState[SResult]): Double =
    s.loggedResult.value match {
      case -\/(err) => 0
      case \/-(res) =>
        val g = graphOfResult(res)
        f(g)
    }

  def equals(s1 : SearchState[SResult], s2 : SearchState[SResult] ): Boolean =
    (s1.loggedResult.value, s2.loggedResult.value) match {
      case (\/-(res1), \/-(res2)) =>
        Mapping.equals(graphOfResult(res1), graphOfResult(res2))
      case _ => false
    }



}

