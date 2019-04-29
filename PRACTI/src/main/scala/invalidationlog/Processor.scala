package invalidationlog

trait Processor {
  def process(inv: Invalidation): Unit
}
