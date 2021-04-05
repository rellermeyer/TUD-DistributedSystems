package chatapp

import chatapp.model.ChatMessage
import rover.rdo.conflict.resolve.AppendIncomingChangesMergeResolve

class ChatConflictResolutionMechanism extends AppendIncomingChangesMergeResolve[List[ChatMessage]] {
}

