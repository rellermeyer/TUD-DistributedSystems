package nl.tudelft.htable.tests


import akka.actor.ActorSystem
import akka.stream.testkit.scaladsl.TestSink
import akka.util.ByteString
import nl.tudelft.htable.client.HTableClient
import nl.tudelft.htable.client.impl.MetaHelpers
import nl.tudelft.htable.core.{Order => _, _}
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api._

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A integration test suite for HugeTable.
 */
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(classOf[OrderAnnotation])
abstract class AbstractIntegrationTest {
  /**
   * The client to access the cluster.
   */
  protected var client: HTableClient

  /**
   * The actor system to use for materializing streams.
   */
  implicit var sys: akka.actor.ActorSystem = _

  /**
   * Set up the integration test suite.
   */
  @BeforeAll
  def setUp(): Unit = {
    sys = ActorSystem()
  }

  /**
   * Tear down the integration test suite.
   */
  @AfterAll
  def tearDown(): Unit = {
    sys.terminate()
  }

  /**
   * A simple test to verify whether the initial database contains the root METADATA tablet.
   */
  @Test
  @Order(1)
  @DisplayName("initial database should contain only METADATA tablet")
  def testInitial(): Unit = {
    val probe = client.read(Scan("METADATA", RowRange.unbounded))
      .runWith(TestSink.probe[Row])
      .ensureSubscription()

    val row = MetaHelpers.readRow(probe.requestNext())
    Assertions.assertTrue(row.isDefined, "Row parses to correct value")
    val (tablet, state, uid) = row.get
    Assertions.assertEquals("METADATA", tablet.table, "The first row is the METADATA table")
    Assertions.assertEquals(TabletState.Served, state, "The METADATA root tablet is served")

    probe.expectComplete()
  }

  /**
   * A simple test to verify whether we can create a table.
   */
  @Test
  @Order(2)
  @DisplayName("create-table is successful")
  def testCreateTable(): Unit = {
    val future = client.createTable("test")
    Await.result(future, 5.seconds)

    // Wait a few seconds before the change is propagated
    Thread.sleep(2000)
  }

  /**
   * A simple test to verify whether the METADATA is updated after table creation.
   */
  @Test
  @Order(3)
  @DisplayName("create-table update METADATA")
  def testCreateTableUpdateMetadata(): Unit = {
    val probe = client.read(Scan("METADATA", RowRange.unbounded))
      .runWith(TestSink.probe[Row])
      .ensureSubscription()
      .request(2)

    // Expect the initial METADATA row
    probe.expectNext()

    val row = MetaHelpers.readRow(probe.expectNext())
    Assertions.assertTrue(row.isDefined, "Row parses to correct value")
    val (tablet, state, uid) = row.get
    Assertions.assertEquals("test", tablet.table, "The first row is the METADATA table")
    Assertions.assertEquals(TabletState.Served, state, "The test tablet is served")
    Assertions.assertTrue(uid.isDefined, "The test tablet must be located on some node")

    probe.expectComplete()
  }

  /**
   * A simple test to verify whether we can add a row to a table.
   */
  @Test
  @Order(5)
  @DisplayName("put is successful")
  def testPutTable(): Unit = {
    val column = ByteString("my-column")
    val time = System.currentTimeMillis()
    val value = ByteString("my-value")

    (0 until 8 by 2).foreach { index =>
      val mutation = RowMutation("test", ByteString(index))
        .put(RowCell(column, time, value))
      val future = client.mutate(mutation)
      Await.result(future, 5.seconds)
    }
  }

  /**
   * A simple test to verify the new table is in the correct order and contains enough entries
   */
  @Test
  @Order(5)
  @DisplayName("put is right-sized and right-ordered")
  def testPutTableVerify(): Unit = {
    val probe = client.read(Scan("test", RowRange.unbounded))
      .runWith(TestSink.probe[Row])
      .ensureSubscription()
      .request(4)

    val firstRow = probe.expectNext()
    val firstColumn = firstRow.cells.head

    Assertions.assertEquals(ByteString(0), firstRow.key)
    Assertions.assertEquals(ByteString("my-column"), firstColumn.qualifier)
    Assertions.assertEquals(ByteString("my-value"), firstColumn.value)

    val secondRow = probe.expectNext()
    val secondColumn = firstRow.cells.head
    Assertions.assertEquals(ByteString(2), secondRow.key)
    Assertions.assertEquals(ByteString("my-column"), secondColumn.qualifier)
    Assertions.assertEquals(ByteString("my-value"), secondColumn.value)

    probe.expectNextN(2)
    probe.expectComplete()
  }

  /**
   * A simple test to verify whether scan processes range.
   */
  @Test
  @Order(6)
  @DisplayName("scan should process range")
  def testScanRange(): Unit = {
    val probe = client.read(Scan("test", RowRange(ByteString(1), ByteString(5))))
      .runWith(TestSink.probe[Row])
      .ensureSubscription()
      .request(2)

    val firstRow = probe.expectNext()
    Assertions.assertEquals(ByteString(2), firstRow.key)

    val secondRow = probe.expectNext()
    Assertions.assertEquals(ByteString(4), secondRow.key)

    probe.expectComplete()
  }

  /**
   * A simple test to verify whether scan supports reverse.
   */
  @Test
  @Order(7)
  @DisplayName("scan reverse results")
  def testScanReverse(): Unit = {
    val probe = client.read(Scan("test", RowRange.unbounded, reversed = true))
      .runWith(TestSink.probe[Row])
      .ensureSubscription()
      .request(4)

    val firstRow = probe.expectNext()
    Assertions.assertEquals(ByteString(6), firstRow.key)

    val secondRow = probe.expectNext()
    Assertions.assertEquals(ByteString(4), secondRow.key)

    probe.expectNextN(2)
    probe.expectComplete()
  }

  /**
   * A simple test to verify whether get functions properly.
   */
  @Test
  @Order(7)
  @DisplayName("get obtains single row")
  def testGet(): Unit = {
    val probe = client.read(Get("test", ByteString(2)))
      .runWith(TestSink.probe[Row])
      .ensureSubscription()

    val row = probe.requestNext()
    val column = row.cells.head

    Assertions.assertEquals(ByteString(2), row.key)
    Assertions.assertEquals(ByteString("my-column"), column.qualifier)
    Assertions.assertEquals(ByteString("my-value"), column.value)
    probe.expectComplete()
  }

  /**
   * A single test to verify whether the split functionality works correctly.
   */
  @Test
  @Order(8)
  @DisplayName("split returns successfully")
  def testSplit(): Unit = {
    val future = client.split("test", ByteString(4))
    Await.result(future, 5.seconds)

    // Wait a few seconds before the change is propagated
    Thread.sleep(2000)
  }

  /**
   * A simple test to verify whether the METADATA is updated after table split
   */
  @Test
  @Order(9)
  @DisplayName("split updates METADATA")
  def testSplitUpdateMetadata(): Unit = {
    val probe = client.read(Scan("METADATA", RowRange.unbounded))
      .runWith(TestSink.probe[Row])
      .ensureSubscription()
      .request(3)

    // Expect the initial METADATA row
    probe.expectNext()

    val rowB = MetaHelpers.readRow(probe.expectNext())
    Assertions.assertTrue(rowB.isDefined, "Row parses to correct value")
    val (tabletB, stateB, uidB) = rowB.get
    Assertions.assertEquals("test", tabletB.table, "The second tablet is test")
    Assertions.assertEquals(RowRange.rightBounded(ByteString(4)), tabletB.range, "The second tablet is right bounded")
    Assertions.assertEquals(TabletState.Served, stateB, "The second tablet is served")
    Assertions.assertTrue(uidB.isDefined, "The second tablet must be located on some node")
    Assertions.assertEquals(1, tabletB.id, "The second identifier must be greater than zero")

    val rowC = MetaHelpers.readRow(probe.expectNext())
    Assertions.assertTrue(rowC.isDefined, "Row parses to correct value")
    val (tabletC, stateC, uidC) = rowC.get
    Assertions.assertEquals("test", tabletC.table, "The third tablet is test")
    Assertions.assertEquals(RowRange.leftBounded(ByteString(4)), tabletC.range, "The third tablet is left bounded")
    Assertions.assertEquals(TabletState.Served, stateC, "The third tablet is served")
    Assertions.assertTrue(uidC.isDefined, "The third tablet must be located on some node")
    Assertions.assertEquals(1, tabletC.id, "The second identifier must be greater than zero")

    probe.expectComplete()
  }

  /**
   * A simple test to verify we can still scan the table after a split.
   */
  @Test
  @Order(10)
  @DisplayName("scan after split works")
  def testSplitScan(): Unit = {
    testScanRange()
    testScanReverse()
  }

  /**
   * A simple test to verify we can still scan the table after a split.
   */
  @Test
  @Order(11)
  @DisplayName("get after split works")
  def testSplitGet(): Unit = {
    testGet()
  }

  /**
   * A simple test to verify that we can invalidate the current assignments.
   */
  @Test
  @Order(12)
  @DisplayName("invalidate is successful")
  def testInvalidate(): Unit = {
    val future = client.balance(Set.empty, shouldInvalidate = true)
    Await.result(future, 5.seconds)

    // Wait a few seconds before the change is propagated
    Thread.sleep(2000)
  }

  /**
   * A simple test to verify that our scans still work after invalidation.
   */
  @Test
  @Order(13)
  @DisplayName("scan works after invalidation")
  def testScanAfterInvalidate(): Unit = {
    Thread.sleep(2000)
    testSplitUpdateMetadata()
    testSplitScan()
  }

  /**
   * A simple test to verify whether we can add a row to a split table.
   */
  @Test
  @Order(14)
  @DisplayName("put after split is successful")
  def testPutAfterSplitTable(): Unit = {
    val column = ByteString("my-column")
    val time = System.currentTimeMillis()
    val value = ByteString("my-value")

    (1 until 8 by 2).foreach { index =>
      val mutation = RowMutation("test", ByteString(index))
        .put(RowCell(column, time, value))
      val future = client.mutate(mutation)
      Await.result(future, 5.seconds)
    }
  }


  /**
   * A simple test to verify that our scans still work after a split put.
   */
  @Test
  @Order(15)
  @DisplayName("scan works after split put")
  def testScanAfterSplitPut(): Unit = {
    val probe = client.read(Scan("test", RowRange.unbounded))
      .runWith(TestSink.probe[Row])
      .ensureSubscription()
      .request(8)

    val firstRow = probe.expectNext()
    Assertions.assertEquals(ByteString(0), firstRow.key)

    val secondRow = probe.expectNext()
    Assertions.assertEquals(ByteString(1), secondRow.key)

    probe.expectNextN(5)

    val lastRow = probe.expectNext()
    Assertions.assertEquals(ByteString(7), lastRow.key)

    probe.expectComplete()
  }


  /**
   * A simple test to verify that we can delete a table.
   */
  @Test
  @Order(16)
  @DisplayName("delete-table is successful")
  def testDeleteTable(): Unit = {
    val future = client.deleteTable("test")

    Await.result(future, 5.seconds)

    // Wait a few seconds before the change is propagated
    Thread.sleep(2000)
  }

  /**
   * A simple test to verify that deleting a table updates the METADATA
   */
  @Test
  @Order(16)
  @DisplayName("delete-table updates METADATA")
  def testDeleteTableUpdatesMetadata(): Unit = {
    testInitial()
  }

  /**
   * A simple test to verify that deleting a table prevents us from accessing it again.
   */
  @Test
  @Order(17)
  @DisplayName("delete-table prevents new acccess")
  def testDeleteTablePreventsAccess(): Unit = {
    client.read(Scan("test", RowRange.unbounded))
      .runWith(TestSink.probe[Row])
      .ensureSubscription()
      .request(1)
      .expectError()
  }
}
