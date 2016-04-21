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

package puck.util

import puck.graph._
import ShowDG._

import scala.math.Ordering


object Debug {


  def printEdgeSet(g : DependencyGraph, logger : PuckLogger, s : Set[Uses])=
    s.foreach(e => logger.writeln(s"\t\t*${(g, e).shows}"))

  def logUsersOf(g : DependencyGraph, logger : PuckLogger, n : NodeId) = {

    logger.writeln(s"users of ${(g, n).shows} :")

    def printUser = g.kindType(n) match {
      case InstanceValueDecl =>
        (userId : NodeId) =>
          logger.writeln("\tType Uses are:")
          printEdgeSet(g, logger, g.typeUsesOf(userId,n))
      case TypeDecl =>
        (userId : NodeId) =>
          logger.writeln("\tTypeMember Uses are:")
          printEdgeSet(g, logger, g.typeMemberUsesOf(userId,n))
      case kt =>
        logger.writeln(s"$kt (${g.getNode(n).kind}) unhandled")
        (userId : NodeId) => ()
    }

    g.usersOfExcludingTypeUse(n).foreach {
      userId =>
        logger.writeln(s"\t- user ${(g, userId).shows}")
        printUser(userId)
    }

  }

  def logUsedBy(g : DependencyGraph, logger : PuckLogger, n : NodeId) = {
    logger.writeln(s"used by ${(g, n).shows} :")

    g.usedByExcludingTypeUse(n).foreach{
      usedId =>
        g.kindType(usedId) match {
          case InstanceValueDecl =>
            logger.writeln(s"\t- used type member ${(g, usedId).shows}")
            logger.writeln("\tType Uses are:")
            printEdgeSet(g, logger, g.typeUsesOf(n, usedId))
          case TypeDecl =>
            logger.writeln(s"\t- used type ${(g, usedId).shows}")
            logger.writeln("\tTypeMember Uses are:")
            printEdgeSet(g, logger, g.typeMemberUsesOf(n, usedId))
          case kt => logger.writeln(s"$kt (${g.getNode(usedId).kind}) unhandled")

        }

    }
  }

  def logUsesDependency(g : DependencyGraph, logger : PuckLogger, n : NodeId) = {
    logUsersOf(g, logger, n)
    logUsedBy(g, logger, n)

  }

  def mkMapStringSortedByKey[A,B](m : Map[A,B])(implicit ord: Ordering[A]) : String = {
    m.toList.sortBy(_._1).mkString("\t[",",\n\t ","]\n")
  }


  implicit val edgeMapCordBuilder : CordBuilder[EdgeMap] = {
    case (dg, EdgeMap ( userMap, usedMap, accessKindMap,
    contents, containers, superTypes, subTypes,
    typeMemberUses2typeUsesMap,
    typeUses2typeMemberUsesMap,
    typeUsesConstraints,
    parameters, types, typedBy)) =>
      val builder = new StringBuilder(150)

//      builder.append("used -> user\n")
//      builder append print(userMap, (used : NodeId, user : NodeId) =>
//        s"$used - ${desambiguatedFullName(dg, used)} used by $user - ${desambiguatedFullName(dg, user)}")

      builder.append("\nuser -> used\n")
      builder append print(usedMap, (user : NodeId, used : NodeId) =>
        s"$user - ${desambiguatedFullName(dg, user)} uses $used - ${desambiguatedFullName(dg, used)}")

//      builder.append("\ncontainer -> content\n")
//      builder append print(contents, (container : NodeId, content : NodeId) =>
//        s"${desambiguatedFullName(dg, container)} contains ${desambiguatedFullName(dg, content)}")
//      builder.append("\ncontent -> container\n\t")
//      builder.append(containers.toString())
      builder.append("\ntypes\n")
      builder.append( types.toList map { case (nid, t) =>
        s"$nid - ${desambiguatedFullName(dg, nid)} : ${typeCord(dg, t)}"
      } mkString("\t", "\n\t", "\n"))

//      builder.append("\nsub -> super\n")
//      builder append print(superTypes, (sub : NodeId, sup : NodeId) =>
//        s"${desambiguatedFullName(dg, sub)} is a ${desambiguatedFullName(dg, sup)}")
//      builder.append("\nsuper -> sub\n\t")
//      builder.append(subTypes.toString)

//      builder.append("\ntmUse -> tUse\n\t")
//      builder.append(typeMemberUses2typeUsesMap.toString)
//      builder.append("\ntUse -> tmUse\n\t")
//      builder.append(typeUses2typeMemberUsesMap.toString)
      builder.append("\ntypeUsesConstraints\n")
      builder append print(typeUsesConstraints, (k : NodeIdP, v : TypeUseConstraint) => typeConstraintCordBuilder(dg,(k,v)).toString())


      builder.toString()
  }


  def mkMapStringSortedByFullName(g : DependencyGraph, m : Map[NodeId,DGNode]) : String = {
    m.toList.map{ case (id, n) =>  ((g, id).shows(desambiguatedFullName) +" - " + n.kind , id) }.
      sortBy(_._1).mkString("\t[",",\n\t ","]\n")

  }

  def print[K,V](s : SetValueMap.T[K,V], p : (K, V)  => String) : String = {
    val sb = new StringBuilder()
    s.iterator.foreach{ case (k, v) =>
      sb append "\t"
      sb append p(k, v)
      sb append "\n"
    }
    sb.toString()
  }




  implicit val nodeIndexCordBuilder : CordBuilder[NodeIndex] = {
    case (g, NodeIndex(_, cNodes, removedCnodes,
    vNodes, removedVnodes,
    cNodes2vNodes,
    roles)) =>
      "Concrete Nodes : " +
        mkMapStringSortedByFullName(g, cNodes) +
        "Removed Concrete Nodes : " +
        mkMapStringSortedByFullName(g, removedCnodes) +
        "Virtual Nodes : " +
        mkMapStringSortedByFullName(g, vNodes) +
        "Removed Virtual Nodes : " +
        mkMapStringSortedByFullName(g, removedVnodes) +
        "CN -> VN : " +
        cNodes2vNodes.mkString("\t[",",\n\t ","]\n") +
        "Roles : " +
        roles.mkString("\t[",",\n\t ","]\n")
  }
}
