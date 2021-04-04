import core.{Node}

object cord extends App {
  assert(args.length > 0)
  val counts = Integer.parseInt(args(0))

  println("Starting " + counts)

  val r = Math.random()

  val hostname =
    if (args.length <= 1)
      "localhost"
    else
      args(1)

  val startport: Int =
    if (args.length <= 2)
      9000
    else
      Integer.parseInt(args(2))

  val first = new Node(startport, s"./cord-run/random$r/0/", hostname, 0)
  var last = first
  var current = first

  for (i <- 1 to counts) {
    current = new Node(startport + i * 10, s"./cord-run/random$r/$i/", hostname, i)
    last.addNeighbour(current.getVirtualNode())
    last = current
  }

  current.addNeighbour(first.getVirtualNode())


}
