package l3

import SymbolicCPSTreeModuleLow._

object CPSHoister extends (Tree => Tree) {
  def apply(tree: Tree): Tree =
    hoist(tree)

  private def hoist(tree: Tree): LetF = tree match {
    case LetL(name, value, body) => {
      val LetF(funs, hBody) = hoist(body)
      LetF(funs, LetL(name, value, hBody))
    }
    
    case LetP(name, prim, args, body) => {
      val LetF(funs, hBody) = hoist(body)
      LetF(funs, LetP(name, prim, args, hBody))
    }
    
    case LetC(cnts: Seq[CntDef], body: Tree) => {
      val LetF(f, e) = hoist(body)
      val cs = cnts map{ eachCnt => hoistC(eachCnt)}
      val csList = cs.toList
      LetF(csList.flatMap(_._1) ++: f, LetC(csList map(_._2), e))
    }
    
    case LetF(funs: Seq[FunDef], body: Tree) => {
      val LetF(f, e) = hoist(body)
      LetF((funs flatMap{ eachFunDef => hoistF(eachFunDef) } ) ++: f, e)
    }
   
    
    case other =>
      LetF(Seq.empty, other)
    
  }
  
  private def hoistC(cnt: CntDef): (Seq[FunDef], CntDef) = {
    val LetF(funs, hBody) = hoist(cnt.body)
    (funs, CntDef(cnt.name, cnt.args, hBody))
  }

  private def hoistF(fun: FunDef): Seq[FunDef] = {
    val LetF(funs, hBody) = hoist(fun.body)
    FunDef(fun.name, fun.retC, fun.args, hBody) +: funs
  }
}