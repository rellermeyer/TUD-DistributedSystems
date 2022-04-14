package Messaging

trait MessageContent

@SerialVersionUID(1L)
case class Advertisement(var ID: (Int, Int),
                         var pClass: String,
                         var pAttributes: (String, Int)) extends Serializable with MessageContent

@SerialVersionUID(1L)
case class Publication(ID: (Int, Int),  var pClass: String, var pAttributes: (String, Int), var pContent: Int) extends Serializable with MessageContent

@SerialVersionUID(1L)
case class Subscription(var ID: (Int, Int),
                        var pClass: String,
                        var pAttributes: (String, Int)) extends Serializable with MessageContent