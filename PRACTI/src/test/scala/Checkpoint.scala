import core.Node
import helper.CheckpointSeeder

class Checkpoint extends TestBase {
  // test that seeds checkpoint with test data for arbitrary node.
  test("checkpointHelper.seedCheckpoint") {
    val node1 = new Node(9100, "./testing/1/", "localhost", 1)
    val seeder = new CheckpointSeeder(node1)
    node1.checkpoint.clear()
    seeder.seedCheckpoint()
    assert(node1.checkpoint.getAllItems().size == 2)
    assert(node1.checkpoint.getAllItems().map(_._1).contains(("data.txt")))

  }

  test("checkpoint.CorrectlyLoadsFromFile") {
    val node1 = new Node(9100, "./testing/1/", "localhost", 1)
    assert(node1.checkpoint.getAllItems().size == 2)

  }
}
