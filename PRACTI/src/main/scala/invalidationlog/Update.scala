package invalidationlog

import clock.ClockInfluencer

abstract class Update(objId: String, timestamp: Long) extends ClockInfluencer with Serializable
