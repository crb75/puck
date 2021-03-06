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

package puck.intellij
package graphBuilding

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.psi._
import com.intellij.psi.search.FileTypeIndex
import puck.graph.io.DG2AST
import puck.graph._
import puck.graph.transformations.NodeMappingInitialState
import puck.javaGraph.JavaGraphBuilder
import puck.javaGraph.nodeKind.JavaNodeKind
import scala.collection.JavaConversions._




object Utils {
  def psiMemberAncestor(elt : PsiElement) : Option[PsiMember] =
    elt match {
      case null => None
      case m : PsiMember => Some(m)
      case _ => psiMemberAncestor(elt.getParent)
    }

  def psiClassAncestor(elt : PsiElement) : Option[PsiClass] =
    elt match {
      case null => None
      case m : PsiClass => Some(m)
      case _ => psiClassAncestor(elt.getParent)
    }
}

import Utils._

class IntellijGraphBuilder (val module : Module)
  extends JavaRecursiveElementVisitor/*JavaElementVisitor*/
  with JavaGraphBuilder {

  implicit val builder = this

  def getJavaSources = {
    //TODO filter to keep only the ones in the sources directory
    //FilenameIndex.getAllFilesByExt(project, "java")
    FileTypeIndex.getFiles(JavaFileType.INSTANCE, module.getModuleScope)
  }

  val psiManager = PsiDocumentManager.getInstance(module.getProject)

  val psiFiles = (getJavaSources filterNot (_.isDirectory) map {
    vf =>
      //println(vf.getName)
      val doc = FileDocumentManager.getInstance().getDocument(vf)
      psiManager getPsiFile doc
  }).toList


  var containerNodes = List[NodeIdT]()

  def directContainer : NodeIdT =
    containerNodes match {
      case pid :: _ => pid
      case Nil => sys.error("expecting a container")
    }

  def pushContainer(cid : NodeIdT) : Unit =
    containerNodes ::= cid

  def popContainer() : Unit =
    containerNodes match {
      case _ :: cts => containerNodes = cts
      case Nil => sys.error("expecting a container")
    }



  def build() : DG2AST = {
    psiFiles.foreach ( _ accept this )
    val pid = getPrimitivePackage
    addContains(DependencyGraph.rootId, pid)
    AttachOrphanNodes()
    registerSuperTypes()

    //println(g.nodes mkString "\n")
    //println(nodesByName.keys mkString "\n")
    new  IntellijDG2AST(module,
      g.newGraph(recording = Seq()),
      NodeMappingInitialState.normalizeNodeTransfos(JavaNodeKind.rootKind,
        g.recording, Seq())._2,
        IntellijGraphBuilder.this.nodesByName,
        graph2ASTMap)
  }

  def getPrimitivePackage : NodeIdT =
    GetNode(primitivePackage, primitivePackage, PackageDummyWrapper, mutable = false)

  def fromSource(e : PsiElement) : Boolean =
    psiFiles contains e.getContainingFile



  var graph2ASTMap = Map[Int, PsiNodeWrapper]()

  //val facade = JavaPsiFacade.getInstance(project)

  def withContainer(cid: NodeId)(f : => Unit) : Unit = {
    pushContainer(cid)
    f
    popContainer()
  }

  def visitElement(cid : NodeId, elt : PsiElement) : Unit =
    withContainer(cid){
    elt.accept(this)
  }

  override def visitJavaFile(file : PsiJavaFile) : Unit = {
    val pid = this.addPackage(file.getPackageName, mutable = true)
    withContainer(pid) {
      file.getClasses foreach visitClass
    }
  }

  override def addPackage(p : String, mutable : Boolean): NodeIdT = {
    val pid = super.addPackage(p, mutable)
    graph2ASTMap get pid match {
      case None => graph2ASTMap += (pid -> PackageDummyWrapper)
      case Some(_) => ()
    }
    pid
  }



  def addIsas(cid : NodeId, superList : PsiReferenceList) : Unit =
    if (superList != null) {
      superList.getReferencedTypes map (_.resolve()) filter {
        case c: PsiAnonymousClass =>
          println("name is null" + c.getName == null)
          true
        case c => c.getName != "Object"
      } foreach { c =>
        val superId = GetNode(c)
        addIsa(cid, superId)
      }
    }


  override def visitClass(aClass: PsiClass) : Unit = {

    val cid = GetNode(aClass)
    addContains(directContainer, cid)

    addIsas(cid, aClass.getImplementsList)
    addIsas(cid, aClass.getExtendsList)

    withContainer(cid){
      aClass.getFields foreach visitField
      aClass.getMethods foreach visitMethod
      aClass.getConstructors foreach visitMethod
      aClass.getInnerClasses foreach visitClass
      aClass.getInitializers foreach visitElement
    }

  }


  override def visitField(field: PsiField): Unit = {
    val fid = GetNode(field)
    setFieldType(fid, field)
    addContains(directContainer, fid)
    field.getInitializer match {
      case null =>
      case init =>
        val iid = GetNode.anonymous(QualifiedName(field), ExprHolder(init), fromSource(init))
        addDef(fid, iid)
        visitElement(iid, init)
    }

  }

  def setFieldType(fid : NodeId, field: PsiField) : Unit = {
    val tid = GetNode(field.getType)
    setType(fid, NamedType(tid))

  }
  def setMethodType(mid : NodeId, method : PsiMethod) : Unit = {
    val params = method.getParameterList.getParameters.toList
    val param_prefix = QualifiedName.memberQN(method)
    val paramIds = params map { p =>
      val pid = GetNode.parameter(param_prefix, p, fromSource(p))
      val tid = GetNode(p.getType)
      setType(pid, NamedType(tid))
      pid
    }
    addParams(mid, paramIds)
    val tid =
      if(method.isConstructor) GetNode(method.getContainingClass)
      else GetNode(method.getReturnType)

    setType(mid, NamedType(tid))
  }



  override def visitMethod(method : PsiMethod) : Unit = {
    val mqname = QualifiedName(method)
    val w =
      if(method.isConstructor) ConstructorDeclHolder(method)
      else MethodDeclHolder(method)

    val mid = GetNode(method)
    addContains(directContainer, mid)

    setMethodType(mid, method)

    method.getBody match {
      case null => ()
      case body =>
        val bid = GetNode.anonymous(mqname, BlockHolder(body), fromSource(body))
        addDef(mid, bid)
        visitElement(bid, body)
    }
  }

//  override def visitMethodCallExpression(expression: PsiMethodCallExpression) : Unit = {
//    println(expression.getParent.getClass)
//    addEdge(Uses(directContainer, GetNode(expression.resolveMethod())))
//
//  }

//  def visitLocalVariable(variable: PsiLocalVariable)
//  def visitCallExpression(callExpression: PsiCallExpression)
//  def visitNewExpression(expression: PsiNewExpression)



//  override def visitElement(element : PsiElement) ={
//    println("visiting "+ element.getClass)
//    super.visitElement(element)
//  }


  override def visitReferenceExpression(reference: PsiReferenceExpression) : Unit =
    reference.resolve() match {
      case m : PsiMember =>
        val typeMemberUses = Uses(directContainer, GetNode(m))
        addEdge(typeMemberUses)

        if(!isStatic(m))
          buildBindingRelationShip(reference, typeMemberUses)

      case r =>
        println(s"reference to $r ignored")
    }

  def isStatic(m: PsiMember) : Boolean =
    m.getModifierList.hasModifierProperty(PsiModifier.STATIC)

  def buildBindingRelationShip(reference: PsiReferenceExpression, typeMemberUses : Uses) : Unit ={
    val typeUses : Uses =
      if(!reference.isQualified){
        val Some(declClassHost) = psiClassAncestor(reference.resolve())
        val Some(refClassHost) = psiClassAncestor(reference)
        val isThisAccess = declClassHost == refClassHost
        val thisClassNode = GetNode(refClassHost)
        val u = if(isThisAccess) Uses(thisClassNode, thisClassNode)
                else Uses(thisClassNode, GetNode(declClassHost))
        addEdge(u)
        u
      }
      else {
        val typeUser = TypeUserFromQualifier(reference.getQualifierExpression)
        if(reference.getQualifierExpression.getType == null)
          sys.error(reference.getQualifierExpression + " has null type")
        val typeUsed = GetNode(reference.getQualifierExpression.getType)
        Uses(typeUser, typeUsed)
      }
    addTypeRelationship(typeUses,typeMemberUses)
  }


}

