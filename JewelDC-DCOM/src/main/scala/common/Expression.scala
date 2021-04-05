package common

import java.rmi.Remote

/**
  * Represents an (arithmetic) expression.
  *
  * For example, the calculation "5 + 3" can be expressed using
  *  "BinaryExpression(PlusOp(), NumberLiteral(5), NumberLiteral(3))".
  *
  * These expression can be used in Java RMI.
  */
sealed abstract class Expression extends Remote
case class NumberLiteral(number: Int) extends Expression
case class BinaryExpression(operator: BinaryOperator, left: Expression, right: Expression) extends Expression

/**
  * Represents an (arithmetic) operator.
  */
sealed abstract class BinaryOperator extends Remote
case class PlusOp() extends BinaryOperator
case class MinusOp() extends BinaryOperator
case class MultOp() extends BinaryOperator
