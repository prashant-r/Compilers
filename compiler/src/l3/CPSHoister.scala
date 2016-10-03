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
      val hoistedCntsAndFuns = cnts map {c => {val h = hoist(c.body)
        (CntDef(c.name, c.args, h.body), h.funs)
        }}
      val seqFs = f
      (hoistedCntsAndFuns map{e2 => e2._2}).foreach(seqFs+:_)
      LetF(seqFs, LetC(hoistedCntsAndFuns map{e1 => e1._1},e))
    }
    
    case LetF(funs: Seq[FunDef], body: Tree) => {
      val LetF(f, e) = hoist(body)
      val mod_FunDefsAndFuns = funs map {c => {val h = hoist(c.body)
        (FunDef(c.name, c.retC, c.args, h.body), h.funs)
        }}
      
      val newFs = mod_FunDefsAndFuns map{e2 => e2._2}
      val seqFs = f
      newFs.foreach(seqFs+:_)
      LetF((mod_FunDefsAndFuns map{e1 => e1._1})++seqFs, e)
      
    }
    
    case other =>
      LetF(Seq(), other)
    
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