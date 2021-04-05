package controller

import java.io.Serializable

import core.{Body, VirtualNode}

case class ReqBody (virtualNode: VirtualNode, body: Body) extends Serializable
