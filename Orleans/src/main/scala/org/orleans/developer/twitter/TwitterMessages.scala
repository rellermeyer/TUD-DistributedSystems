package org.orleans.developer.twitter
import org.orleans.silo.control.GrainPacket

object TwitterMessages {

  case class UserExists(username: String) extends GrainPacket
  case class UserCreate(username: String, ref: String) extends GrainPacket
  case class UserGet(username: String) extends GrainPacket
  case class UserRetrieve(grainId: String) extends GrainPacket

  case class SetUsername(name: String) extends GrainPacket
  case class Tweet(msg: String, timestamp: String) extends GrainPacket
  case class FollowUser(name: String) extends GrainPacket
  case class GetFollowing() extends GrainPacket
  case class FollowList(followList: List[String]) extends GrainPacket
  case class GetTweetListSize() extends GrainPacket
  case class TweetListSize(size: Int) extends GrainPacket
  case class GetTweetList() extends GrainPacket
  case class TweetList(tweets: List[Tweet]) extends GrainPacket

  case class TwitterSuccess() extends GrainPacket
  case class TwitterFailure(failure: String) extends GrainPacket

}
