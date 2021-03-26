package jobmanager

import scala.collection.mutable.ArrayBuffer
import taskmanager.TaskManagerInfo

/**
     * 1
     *   \
     *    4
     *  /   \
     * 2      6
     *   \  /
     *    5
     *   /
     * 3
    */

    // 1 to = [4, 5] from = [dataSource]
    // 2 to = [4, 5] from = [dataSource]
    // 3 to = [4, 5] from = [dataSource]
    // 4 to = [6]    from = [1, 2, 3]
    // 5 to = [6]    from = [1, 2, 3]
    // 6 to = []     from = [4, 5]

    /**
     * (TM, TaskID)
     * plan = 
     * [
     *  data: [(tm1, 0)]
     *  op0:  [(tm2, 0), (tm3, 0), (tm3, 1)]
     *  op1:  [(tm3, 2), (tm4, 0)]
     *  op2:  [(tm4, 1)]
     * ]
     **/

object ExecutionPlan {
    
    // Create an execution plan with the specified taskManagers and parallelism
    def createPlan(taskManagers: ArrayBuffer[TaskManagerInfo], 
                   ps: Array[Int], ops: Array[String], 
                   parallelisms: Array[Int],
                   taskIDCounters: ArrayBuffer[Int]): Array[ArrayBuffer[(Int, Int)]] = {
        // Plan holds for each operator the tuple (TM, TaskID)
        val plan = Array.fill(ops.length + 1)(ArrayBuffer.empty[(Int, Int)]) // 1 extra row for data sources

        val dataSources = Array(3) // indices of taskManagers who will provide data

        // Add data sources to plan
        dataSources.foreach(x => {
            plan(0) += ((x, taskIDCounters(x))) // add to plan
            taskIDCounters(x) += 1 // increment task counter for this TM
        })

        // Add operators to plan
        for (op <- ops.indices) {                             // for each operator
            var par = parallelisms(op)
            for (tm <- taskManagers.indices) {                // for each task manager

                // While current operator still needs tasks assigned
                //  AND current task manager still has available slots
                while (par > 0 && ps(tm) > 0) {
                    plan(op + 1) += ((tm, taskIDCounters(tm))) // add to plan
                    taskIDCounters(tm) += 1                    // increment task counter for current TM
                    ps(tm) -= 1                                // decrement number of available slots for current TM
                    par -= 1                                   // decrement number of times current operator still needs to be assigned
                }
            }
        }
        return plan
    }

    def comparePlans(plan1: Array[ArrayBuffer[(Int, Int)]], plan2: Array[ArrayBuffer[(Int, Int)]]):Boolean = {
        for (opIndex <- 0 until plan1.size) {
            for (asIndex <- 0 until plan1(opIndex).size) {
                var operator1: (Int, Int) = plan1(opIndex)(asIndex)
                var operator2: (Int, Int) = plan2(opIndex)(asIndex)
                if (operator1._1 != operator2._1) {
                    return false
                }
            }
        }
        true
    }

    def printPlan(plan: Array[ArrayBuffer[(Int, Int)]]) = {
        for (i <- plan.indices) {
            println(plan(i).mkString(" "))
        }
    }
}

case class Task(jobID: Int, taskID: Int, from: Array[Int], to: Array[Int], toTaskIDs: Array[Int], operator: String)
