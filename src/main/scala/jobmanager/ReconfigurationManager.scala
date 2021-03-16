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
import optimus.algebra.Zero

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

      for (i <- 0 until m) {
        p(i) = MPIntVar("p" + taskManagers(i).id, 0 until infinity)
      }

      //Integer Linear Program
      var pVals: Expression = Zero // use 0 for initialization

      var LHS_constr4: Expression = Zero
      var constr = Vector.empty[MPConstraint]

      for (i <- 1 until m) {
        val currP = MPFloatVar.positive("p" + i.toString())
        var coef: Float = 0

        // First constraint (Equation 2)
        for (j <- 0 until m - 1) {
          val LHS: Expression = currP * (taskManagers(i).ipRate / prl)
          val RHS: Expression = alpha * taskManagers(i).bandwidthsToSelf(j).rate
          constr = constr :+ add(LHS <:= RHS)
        }

        // Second Constraint (Eqution 3)
        for (j <- 0 until m) {
          var LHS: Expression = currP * (taskManagers(i).opRate / prl)
          if (j != i) {
            for (k <- 0 until m - 1) {
              if (
                taskManagers(j).bandwidthsToSelf(k).fromID == taskManagers(
                  i
                ).id
              ) {
                val RHS: Expression =
                  alpha * taskManagers(j).bandwidthsToSelf(k).rate
 //               constr = constr :+ add(LHS <:= RHS)
              }
            }
          }
        }

        // Third Constraint (Equation 4)
        val LHS: Expression = currP
        val RHS: Expression = taskManagers(i).numSlots
//        constr = constr :+ add(LHS <:= RHS)

        // LHS Fourth Constraint (Equation 5)
        LHS_constr4 += currP

        // Objective Function (minimization for all upstream and downstream)
        for (j <- 0 until m - 1) {
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

      // Add Fourth constraint (Equation 5)
      val RHS_constr4: Expression = prl
      // constr = constr :+ add(LHS_constr4 := RHS_constr4)


      // Minimize objective function
      minimize(pVals)
 
      // Start the Solver
      start()

      for (i <- p.indices)
        println(p(i).value.get)
       
    }
  }
}
