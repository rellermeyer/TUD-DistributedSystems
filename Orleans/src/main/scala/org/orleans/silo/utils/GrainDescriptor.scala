package org.orleans.silo.utils

import org.orleans.silo.utils.GrainState.GrainState

case class GrainDescriptor(state: GrainState, location : SlaveDetails)
