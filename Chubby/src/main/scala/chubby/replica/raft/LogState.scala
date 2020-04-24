package chubby.replica.raft

case class LogState(
    allLog: List[LogEntry],
    commitIndex: Int,
    lastApplied: Int,
    nextIndex: Map[String, Int],
    matchIndex: Map[String, Int]
)
