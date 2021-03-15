// package jobmanager

import scala.collection.mutable.ArrayBuffer
import optimus.optimization._
import optimus.optimization.enums.SolverLib
import optimus.optimization.model.MPFloatVar
import optimus.algebra.Constraint
import optimus.optimization.model.MPIntVar
import optimus.optimization.model.MPConstraint
import optimus.algebra.Expression
import optimus.algebra.ConstraintRelation

// import jobmanager.TaskManagerInfo

object ReconfigurationManager {
  def solveILP(
      taskManagers: ArrayBuffer[TaskManagerInfo],
      prl: Float, //Desired parallelism : The desired sum of all slots of all data centers
      alpha: Float
  ) = {

    implicit val solver = MPModel(SolverLib.oJSolver)
    if (solver == null) {
      System.out.println("Could not create LP oJSolver")
    } else {
      println("LP oJSolver started")
      val m = taskManagers.length
      val infinity = java.lang.Integer.MAX_VALUE
      val p = new Array[MPIntVar](m)
      val eqn2 = Array.ofDim[MPIntVar](m, m - 1)
      val eqn3 = Array.ofDim[MPIntVar](m, m - 1)
      val eqn4 = new Array[MPIntVar](m)
      val obj = Array.ofDim[MPIntVar](m, m)

      // var expr1: Expression = 0
      
      for (i <- 0 until m) {
        p(i) = MPIntVar("p" + taskManagers(i).id, 0 until infinity)
        // expr1 = expr1 + p(i)
      }

      // Equation 2
      

      // //Equation 2
      // for (i <- 0 until m) {
      //   for (j <- 0 until m - 1) {
      //     eqn2(i)(j) =
      //       makeConstraint( // a*B from j to i (normally u to s)
      //         0, // minimum value for left-hand side
      //         alpha * taskManagers(i)
      //           .bandwidthsToSelf(j)
      //           .rate, // maximum value for left-hand side
      //         "eqn2_" + taskManagers(i)
      //           .bandwidthsToSelf(j)
      //           .fromID + "_" + taskManagers(i).id
      //       )

      //     // Only one coefficient is used in the equation, set all others to 0
      //     for (k <- 0 until m) {
      //       if (k == i) {
      //         eqn2(i)(j).setCoefficient(
      //           p(k),
      //           taskManagers(k).ipRate / prl
      //         ) // lambda_I / p
      //       } else {
      //         eqn2(i)(j).setCoefficient(p(k), 0)
      //       }
      //     }
      //   }
      // }

      // //Equation 3
      // var tmp: Int = 0;
      // for (i <- 0 until m) {
      //   for (j <- 0 until m) {
      //     if (j != i) {
      //       for (k <- 0 until m - 1) {
      //         if (
      //           taskManagers(j).bandwidthsToSelf(k).fromID == taskManagers(
      //             i
      //           ).id
      //         ) {
      //           eqn3(i)(tmp) = solver.makeConstraint(
      //             0,
      //             alpha * taskManagers(j).bandwidthsToSelf(k).rate,
      //             "eqn3_" + taskManagers(i).id + "_" + taskManagers(k).id
      //           )
      //           for (l <- 0 until m) {
      //             if (l == i) {
      //               eqn3(i)(j)
      //                 .setCoefficient(p(l), taskManagers(i).opRate / prl)
      //             } else {
      //               eqn3(i)(j).setCoefficient(p(l), 0)
      //             }
      //           }
      //           tmp += 1
      //         }
      //       }
      //     }
      //   }
      // }

      // //Equation 4
      // for (i <- 0 until m) {
      //   eqn4(i) = solver.makeConstraint(
      //     0,
      //     taskManagers(i).numSlots,
      //     "eqn4_" + taskManagers(i).id
      //   )
      //   for (k <- 0 until m) {
      //     if (k == i) {
      //       eqn4(i).setCoefficient(p(k), 1)
      //     } else {
      //       eqn4(i).setCoefficient(p(k), 0)
      //     }
      //   }
      // }

      // //Equation 5
      // val eqn5 = solver.makeConstraint(prl, prl, "eqn5")
      // for (i <- 0 until m) {
      //   eqn5.setCoefficient(p(i), 1) //  1*p[0] + 1*p[1] + ...
      // }

      //Objective for vagmcs solver
      var pVals: Expression = 0 // use 0 for initialization
      var constExp: Expression = 0 
      for (i <- 1 until m) {
        val currP = MPFloatVar.positive("p" + i.toString())
        var coef:Float = 0
        var constr2 = Vector.empty[MPConstraint]
        for (j <- 0 until m) {
          // Second constraint (Equation 2)
          // TODO: do we need to check if there is a connection with u?
          val LHS: Expression = alpha * taskManagers(i).bandwidthsToSelf(j).rate
          val RHS: Expression = currP * (taskManagers(i).ipRate / prl)
          constr2 = constr2 :+ add(LHS <:= RHS)

          for (k <- 0 until m - 1) {
            var l_ik: Float = 0
            var l_kj: Float = 0
            for (l <- 0 until m) {
              if (
                taskManagers(k).latenciesToSelf(l).fromID == taskManagers(i).id
              ) {
                l_ik = taskManagers(k).latenciesToSelf(l).time
                coef += l_ik
              }
              if (
                taskManagers(j).latenciesToSelf(l).fromID == taskManagers(k).id
              ) {
                l_kj = taskManagers(j).latenciesToSelf(l).time
                coef += l_kj
              }
            }
          }
        }
        pVals = pVals + (coef * currP)
      }

      /*
      //Objective
      for (i <- 0 until m) {
        for (j <- 0 until m) {
          obj(i)(j) = solver.objective();
          for (k <- 0 until m) {
            var l_ik: Float = 0;
            var l_kj: Float = 0;
            for (l <- 0 until m) {
              if (
                taskManagers(k).latenciesToSelf(l).fromID == taskManagers(i).id
              ) {
                l_ik = taskManagers(k).latenciesToSelf(l).time
              }
              if (
                taskManagers(j).latenciesToSelf(l).fromID == taskManagers(k).id
              ) {
                l_kj = taskManagers(j).latenciesToSelf(l).time
              }
            }

            obj(i)(j).setCoefficient(p(k), (l_ik + l_kj));
          }
          obj(i)(j).setMinimization();
        }
      }
       */
      val resultStatus = solver.start();
      for (i <- 0 until m)
        print("Result: " + resultStatus + " p[" + i + "] = " + p(i).value.get) // *DUMMY PRINT
    }
  }

}
