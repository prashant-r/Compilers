package l3

import BitTwiddling.bitsToIntMSBF
import l3.{ SymbolicCPSTreeModule => H }
import l3.{ SymbolicCPSTreeModuleLow => L }

/**
 * Value-representation phase for the CPS language. Translates a tree
 * with high-level values (blocks, integers, booleans, unit) and
 * corresponding primitives to one with low-level values (blocks
 * and integers only) and corresponding primitives.
 *
 * @author Michel Schinz <Michel.Schinz@epfl.ch>
 */

object CPSValueRepresenter extends (H.Tree => L.Tree) {

  var collectKVBdgs: Seq[(L.Name, L.Literal)] = Seq();

  def resetCollectKVBdgs() =
    collectKVBdgs = Seq()

  def apply(tree: H.Tree): L.Tree =
    transform(tree)(Map.empty)

  private def sLetL_*(bdgs: Seq[(L.Name, L.Literal)], body: L.Tree)(implicit worker: Map[Symbol, (Symbol, Seq[Symbol])]): L.Tree =
    {
      (bdgs :\ body)((b, t) => {
        b match {
          case (en, e) =>
            L.LetL(en.asInstanceOf[L.Name], e.asInstanceOf[L.Literal], t)
        }
      })
    }

  private def sLetP_*(bdgs: Seq[(L.Name, L.ValuePrimitive, Seq[L.Name])], body: L.Tree)(implicit worker: Map[Symbol, (Symbol, Seq[Symbol])]): L.Tree =
    {
      (bdgs :\ body)((b, t) => {
        b match {
          case (en, v, e) =>
            L.LetP(en.asInstanceOf[L.Name], v.asInstanceOf[L.ValuePrimitive], e.asInstanceOf[Seq[L.Name]], t)
        }
      })
    }

  private def transform(tree: H.Tree)(implicit worker: Map[Symbol, (Symbol, Seq[Symbol])]): L.Tree = tree match {

    //---------------------------------------------------------------------- 
    // Function abstraction, definition and application
    //----------------------------------------------------------------------   
    case H.LetF(funs: Seq[H.FunDef], body: H.Tree) => {

      val convertedFunctions = funs map { input =>
        {
          resetCollectKVBdgs();
          val w1 = Symbol.fresh("worker_name")
          val env1 = Symbol.fresh("environ")
                 
          var preimage = Seq(input.name) ++ (freeVariables(input.body)(Map.empty) -- (Seq(input.name) ++ input.args))

          (L.FunDef(w1, input.retC, Seq(env1) ++: input.args, {
            val arg_count = input.args.size
            
            var x = 1
            var vees: Seq[L.Name] = Seq()
            var pBdgs: Seq[(L.Name, L.ValuePrimitive, Seq[L.Name])] = Seq()
            for (x <- 1 to preimage.size -1) {
              val v1 = Symbol.fresh("interior_v1")
              
              val x_name = Symbol.fresh("x_s_name")
              collectKVBdgs = collectKVBdgs ++ Seq((x_name, x))
              pBdgs ++= Seq((v1, CPSBlockGet, Seq(env1, x_name)))
              vees ++= Seq(v1)
            }
            var image = Seq(env1) ++ vees
         
             
            
            
            sLetP_*(pBdgs, transform(input.body.subst(Substitution(preimage, image))))
          }), preimage)
        }
      }

      val all_bdgs = {
        var balloc_bdgs: Seq[(L.Name, L.ValuePrimitive, Seq[L.Name])] = Seq()
        var bset_bdgs: Seq[(L.Name, L.ValuePrimitive, Seq[L.Name])] = Seq()
        val dummy_ret = convertedFunctions map { e1 =>
          {
            var fun_ext = e1._1
            var pimgworker = e1._2
            
            val btag = 202
            val fv_num = Symbol.fresh("fv_numberrraavvvi")
            collectKVBdgs ++= Seq((fv_num, fun_ext.args.size))
            
            balloc_bdgs ++= Seq((pimgworker(0), CPSBlockAlloc(btag), Seq(fv_num)))
            
            val func_args_size = pimgworker.size - 1
            var xi = 0
            val name_zero_t1 = Symbol.fresh("block_set_zero")
            val t1_zero = Symbol.fresh("zero_name")
            collectKVBdgs ++= Seq((t1_zero, xi))
            bset_bdgs ++= Seq((name_zero_t1, CPSBlockSet, Seq(pimgworker(0), t1_zero, fun_ext.name)))

            for (xi <- 1 to func_args_size) {
              val new_t1 = Symbol.fresh("block_set_wrapper")
              val name_int_t1 = Symbol.fresh("block_set_int")
              collectKVBdgs ++= Seq((name_int_t1, xi))
              bset_bdgs ++= Seq((new_t1, CPSBlockSet, Seq(pimgworker(0), name_int_t1, pimgworker(xi))))
            }
          }
        }
        var tot_bdgs = balloc_bdgs ++ bset_bdgs
        tot_bdgs
      }
      sLetL_*(collectKVBdgs: Seq[(L.Name, L.Literal)],
        L.LetF(convertedFunctions map { e1 => e1._1 }, {
          sLetP_*(all_bdgs, transform(body))
        }))

    }

    case H.AppF(fun, retC, args) =>
      {
        val f = Symbol.fresh("f__appf_namespace")
        tempLetL(0) { c1 =>
          L.LetP(f, CPSBlockGet, Seq(fun, c1), L.AppF(f, retC, Seq(fun) ++ args))
        }
      }    
    
    //----------------------------------------------------------------------    
    // Representing Literals
    //----------------------------------------------------------------------

    case H.LetL(name, IntLit(value), body) =>
      L.LetL(name, (value << 1) | 1, transform(body))

    case H.LetL(name, CharLit(value), body) =>
      L.LetL(name, (value << 3) | bitsToIntMSBF(1, 1, 0), transform(body))

    case H.LetL(name, BooleanLit(true), body) =>
      L.LetL(name, bitsToIntMSBF(1, 1, 0, 1, 0), transform(body))

    case H.LetL(name, BooleanLit(false), body) =>
      L.LetL(name, bitsToIntMSBF(0, 1, 0, 1, 0), transform(body))

    case H.LetL(name, UnitLit, body) =>
      L.LetL(name, bitsToIntMSBF(0, 0, 1, 0), transform(body))

    //----------------------------------------------------------------------    
    // Representing Primitives   
    //---------------------------------------------------------------------- 

    case H.LetP(name, L3IntAdd, args, body) =>
      tempLetP(CPSAdd, args) { r =>
        tempLetL(1) { c1 =>
          L.LetP(name, CPSSub, Seq(r, c1), transform(body))
        }
      }

    case H.LetP(name, L3IntSub, args, body) =>
      tempLetP(CPSSub, args) { r =>
        tempLetL(1) { c1 =>
          L.LetP(name, CPSAdd, Seq(r, c1), transform(body))
        }
      }

    case H.LetP(name, L3IntMul, args, body) =>
      args match {
        case Seq(n1, n2) =>
          {
            tempLetL(1) {
              ns1 =>
                {
                  val temp = Seq(n1, ns1)
                  tempLetP(CPSSub, temp) { firstMultArg =>
                    tempLetP(CPSArithShiftR, Seq(n2, ns1)) {
                      secondMultArg =>
                        {
                          tempLetP(CPSMul, Seq(firstMultArg, secondMultArg)) {
                            answer =>
                              L.LetP(name, CPSAdd, Seq(answer, ns1), transform(body))
                          }
                        }
                    }

                  }

                }
            }
          }

      }

    case H.LetP(name, L3IntDiv, args, body) =>
      args match {
        case Seq(n1, n2) =>
          {
            tempLetL(1) {
              ns1 =>
                {
                  val temp = Seq(n1, ns1)
                  tempLetP(CPSSub, temp) { firstMultArg =>
                    tempLetP(CPSArithShiftR, Seq(n2, ns1)) {
                      secondMultArg =>
                        {
                          tempLetP(CPSDiv, Seq(firstMultArg, secondMultArg)) {
                            answer =>
                              L.LetP(name, CPSAdd, Seq(answer, ns1), transform(body))
                          }
                        }
                    }

                  }

                }
            }
          }

      }

    case H.LetP(name, L3IntMod, args, body) =>
      args match {
        case Seq(n1, n2) =>
          {
            tempLetL(1) {
              ns1 =>
                {
                  val temp = Seq(n1, ns1)
                  tempLetP(CPSSub, temp) { firstMultArg =>
                    tempLetP(CPSSub, Seq(n2, ns1)) {
                      secondMultArg =>
                        {
                          tempLetP(CPSMod, Seq(firstMultArg, secondMultArg)) {
                            answer =>
                              L.LetP(name, CPSAdd, Seq(answer, ns1), transform(body))
                          }
                        }
                    }

                  }

                }
            }
          }

      }

    case H.LetP(name, L3IntArithShiftRight, args, body) =>
      args match {
        case Seq(n1, n2) =>
          {
            tempLetL(1) {
              ns1 =>
                {
                  tempLetP(CPSSub, Seq(n1, ns1)) {

                    thisIsFirstArgument =>
                      {
                        tempLetP(CPSArithShiftR, Seq(n2, ns1)) {
                          thisIsSecondArgument =>
                            {
                              tempLetP(CPSArithShiftR, Seq(thisIsFirstArgument, thisIsSecondArgument)) { last_val =>
                                L.LetP(name, CPSAdd, Seq(last_val, ns1), transform(body))
                              }
                            }
                        }
                      }

                  }
                }
            }
          }
      }

    case H.LetP(name, L3IntArithShiftLeft, args, body) =>
      args match {
        case Seq(n1, n2) =>
          {
            tempLetL(1) {
              ns1 =>
                {
                  tempLetP(CPSSub, Seq(n1, ns1)) {

                    thisIsFirstArgument =>
                      {
                        tempLetP(CPSArithShiftR, Seq(n2, ns1)) {
                          thisIsSecondArgument =>
                            {
                              tempLetP(CPSArithShiftL, Seq(thisIsFirstArgument, thisIsSecondArgument)) { last_val =>
                                L.LetP(name, CPSAdd, Seq(last_val, ns1), transform(body))
                              }
                            }
                        }
                      }

                  }
                }
            }
          }
      }

    case H.LetP(name, L3IntBitwiseAnd, args, body) =>
       L.LetP(name, CPSAnd, args, transform(body))

    case H.LetP(name, L3IntBitwiseOr, args, body) =>
       L.LetP(name, CPSOr, args, transform(body))
      
    case H.LetP(name, L3IntBitwiseXOr, args, body) =>
       L.LetP(name, CPSXOr, args , transform(body))
     
    //----------------------------------------------------------------------     
    // other integer comparison primitives similar
    //----------------------------------------------------------------------

    case H.If(L3IntLt, args, thenC, elseC) =>
      L.If(CPSLt, args, thenC, elseC)

    case H.If(L3IntLe, args, thenC, elseC) =>
      L.If(CPSLe, args, thenC, elseC)

    case H.If(L3IntGt, args, thenC, elseC) =>
      L.If(CPSGt, args, thenC, elseC)

    case H.If(L3IntGe, args, thenC, elseC) =>
      L.If(CPSGe, args, thenC, elseC)

    case H.If(L3Eq, args, thenC, elseC) =>
      L.If(CPSEq, args, thenC, elseC)

    case H.If(L3Ne, args, thenC, elseC) =>
      L.If(CPSNe, args, thenC, elseC)

    //----------------------------------------------------------------------
    // Block primitive types
    //----------------------------------------------------------------------
    case H.LetP(name, L3BlockAlloc(tag), Seq(a), body) =>
      tempLetL(1) { c1 =>
        tempLetP(CPSArithShiftR, Seq(a, c1)) { t1 =>
          L.LetP(name, CPSBlockAlloc(tag), Seq(t1), transform(body))
        }
      }

    case H.LetP(name, L3BlockTag, Seq(a), body) => {
      tempLetL(1) { c1 =>
        tempLetP(CPSBlockTag, Seq(a)) { t1 =>
          tempLetP(CPSArithShiftL, Seq(t1, c1)) { t2 =>
            L.LetP(name, CPSAdd, Seq(t2, c1), transform(body))
          }
        }
      }
    }

    case H.LetP(name, L3BlockLength, Seq(a), body) => {
      tempLetL(1) { c1 =>
        tempLetP(CPSBlockLength, Seq(a)) { cps_len =>
          tempLetP(CPSArithShiftL, Seq(cps_len, c1)) { ext_len =>
            L.LetP(name, CPSAdd, Seq(ext_len, c1), transform(body))
          }
        }
      }
    }

    case H.LetP(name, L3BlockGet, Seq(a, b_loc), body) => {
      tempLetL(1) { c1 =>
        tempLetP(CPSArithShiftR, Seq(b_loc, c1)) { cps_pos =>
          L.LetP(name, CPSBlockGet, Seq(a, cps_pos), transform(body))
        }
      }
    }
    
    case H.LetP(name, L3BlockSet, Seq(a, b_loc, b_val), body) => {
      tempLetL(1) { c1 =>
        tempLetP(CPSArithShiftR, Seq(b_loc, c1)) { cps_pos =>
          L.LetP(name, CPSBlockSet, Seq(a, cps_pos, b_val), transform(body))
        }
      }
    }
    
    //----------------------------------------------------------------------
    //representing L3 integers(4) - ByteRead and ByteWrite
    //----------------------------------------------------------------------

    case H.LetP(name, L3ByteRead, Seq(), body) => {
      tempLetP(CPSByteRead, Seq()) { t1 =>
        {
          tempLetL(1) { c1 =>
            {
              tempLetP(CPSArithShiftL, Seq(t1, c1)) { t2 =>
                {
                  L.LetP(name, CPSAdd, Seq(t2, c1), transform(body))
                }
              }

            }
          }
        }
      }
    }

    case H.LetP(name, L3ByteWrite, Seq(a), body) => {
      tempLetL(1) { c1 =>
        tempLetP(CPSArithShiftR, Seq(a, c1)) { shiftedVal =>
          L.LetP(name, CPSByteWrite, Seq(shiftedVal), transform(body))
        }
      }
    }

    //----------------------------------------------------------------------   
    //character literal typecasting to and fro Integer primitive
    //----------------------------------------------------------------------

    case H.LetP(name, L3CharToInt, Seq(arg), body) =>
      {
        tempLetL(2) { c1 =>
          L.LetP(name, CPSArithShiftR, Seq(arg, c1), transform(body))
        }
      }

    case H.LetP(name, L3IntToChar, Seq(arg), body) =>
      {
        tempLetL(2) { c1 =>
          tempLetP(CPSArithShiftL, Seq(arg, c1)) { t1 =>
            {
              L.LetP(name, CPSAdd, Seq(t1, c1), transform(body))
            }
          }
        }
      }

    //----------------------------------------------------------------------    
    // Check type primitive LSB 
    //----------------------------------------------------------------------

    case H.If(L3IntP, Seq(a), thenC, elseC) =>
      ifEqLSB(a, Seq(1), thenC, elseC)

    case H.If(L3BlockP, Seq(a), thenC, elseC) =>
      ifEqLSB(a, Seq(0, 0), thenC, elseC)

    case H.If(L3BoolP, Seq(a), thenC, elseC) =>
      ifEqLSB(a, Seq(1, 0, 1, 0), thenC, elseC)

    case H.If(L3UnitP, Seq(a), thenC, elseC) =>
      ifEqLSB(a, Seq(0, 0, 1, 0), thenC, elseC)

    case H.If(L3CharP, Seq(a), thenC, elseC) =>
      ifEqLSB(a, Seq(1, 1, 0), thenC, elseC)

    //----------------------------------------------------------------------
    // For continuations
    //----------------------------------------------------------------------
    case H.AppC(cnt, args) =>
      L.AppC(cnt, args)

    case H.LetC(cnts, body) => {
      val letC_cont = cnts map { c =>
        {
          L.CntDef(c.name, c.args, transform(c.body))
        }
      }
      L.LetC(letC_cont, transform(body))
    }

    //----------------------------------------------------------------------   
    // For identity primitive
    //----------------------------------------------------------------------

    case H.LetP(name, L3Id, args, body) =>
      L.LetP(name, CPSId, args, transform(body))

    //----------------------------------------------------------------------
    // The Halt primitive 
    //----------------------------------------------------------------------
    case H.Halt(arg) =>
      tempLetL(1) { res1 =>
        tempLetP(CPSArithShiftR, Seq(arg, res1)) { res2 =>
          L.Halt(res2)
        }
      }
      
  }

   private def freeVariables(tree: H.Tree)(implicit worker: Map[Symbol, Set[Symbol]]): Set[Symbol] = tree match {
    case H.LetL(name, _, body) =>
      freeVariables(body) - name

    case H.LetP(name, prim, args, body) =>
      freeVariables(body) -- Set(name) ++ args.toSet

    case H.LetC(cnts, body) =>
      val ext_cnt = cnts flatMap { cnt => freeVariables(cnt.body) -- cnt.args.toSet }
      freeVariables(body) ++ ext_cnt.toSet

    case H.LetF(funs, body) =>
      val ext_func = funs flatMap { ft => freeVariables(ft.body) -ft.name -- ft.args.toSet }
      freeVariables(body) ++ ext_func.toSet

    case H.AppC(cnt, args) =>
      args.toSet

    case H.AppF(fun, retC, args) =>
      Set(fun) ++ args.toSet

    case H.If(cond, args, thenC, elseC) =>
      args.toSet
  }

  private def freeVariables(cnt: H.CntDef)(implicit worker: Map[Symbol, Set[Symbol]]): Set[Symbol] =
    freeVariables(cnt.body) - cnt.name -- cnt.args

  private def freeVariables(fun: H.FunDef)(implicit worker: Map[Symbol, Set[Symbol]]): Set[Symbol] =
    freeVariables(fun.body) - fun.name -- fun.args

  // Tree builders

  /**
   * Call body with a fresh name, and wraps its resulting tree in one
   * that binds the fresh name to the given literal value.
   */
  private def tempLetL(v: Int)(body: L.Name => L.Tree): L.Tree = {
    val tempSym = Symbol.fresh("t")
    L.LetL(tempSym, v, body(tempSym))
  }

  /**
   * Call body with a fresh name, and wraps its resulting tree in one
   * that binds the fresh name to the result of applying the given
   * primitive to the given arguments.
   */
  private def tempLetP(p: L.ValuePrimitive, args: Seq[L.Name])(body: L.Name => L.Tree): L.Tree = {
    val tempSym = Symbol.fresh("t")
    L.LetP(tempSym, p, args, body(tempSym))
  }

  /**
   * Generate an If tree to check whether the least-significant bits
   * of the value bound to the given name are equal to those passed as
   * argument. The generated If tree will apply continuation tC if it
   * is the case, and eC otherwise. The bits should be ordered with
   * the most-significant one first (e.g. the list (1,1,0) represents
   * the decimal value 6).
   */
  private def ifEqLSB(arg: L.Name, bits: Seq[Int], tC: L.Name, eC: L.Name): L.Tree =
    tempLetL(bitsToIntMSBF(bits map { b => 1 }: _*)) { mask =>
      tempLetP(CPSAnd, Seq(arg, mask)) { masked =>
        tempLetL(bitsToIntMSBF(bits: _*)) { value =>
          L.If(CPSEq, Seq(masked, value), tC, eC)
        }
      }
    }
  /* Generate an If treee to check if the least significant bits of the value 
		 bound to the given name are equal to those passed are boolean*/

  private def ifEqLSBBoolean(arg: L.Name, tC: L.Name, eC: L.Name): L.Tree =
    tempLetL(bitsToIntMSBF(1, 1, 1, 1)) { m =>
      tempLetL(bitsToIntMSBF(1, 0, 1, 0)) { t =>
        tempLetP(CPSAnd, Seq(arg, m)) { r =>
          L.If(CPSEq, Seq(r, t), tC, eC)
        }
      }
    }

  private def ifEqLSBUnit(arg: L.Name, tC: L.Name, eC: L.Name): L.Tree =
    tempLetL(bitsToIntMSBF(1, 1, 1, 1)) { m =>
      tempLetL(bitsToIntMSBF(0, 0, 1, 0)) { t =>
        tempLetP(CPSAnd, Seq(arg, m)) { r =>
          L.If(CPSEq, Seq(r, t), tC, eC)
        }
      }
    }
}