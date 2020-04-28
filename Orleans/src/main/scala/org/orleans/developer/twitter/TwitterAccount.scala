package org.orleans.developer.twitter

import com.typesafe.scalalogging.LazyLogging
import org.orleans.developer.twitter.TwitterMessages._
import org.orleans.silo.dispatcher.Sender
import org.orleans.silo.services.grain.Grain
import org.orleans.silo.services.grain.Grain.Receive

class TwitterAccount(id: String) extends Grain(id) with LazyLogging {

  private var username: String = ""

  private var tweets: List[Tweet] = List()
  private var followers: List[String] = List()

  override def receive: Receive = {
    case (uname: SetUsername, _) => this.username = uname.name
    case (tweet: Tweet, _) => {
      tweets = tweet :: tweets
    }
    case (t: GetTweetList, sender: Sender) => sender ! TweetList(tweets)
    case (follow: FollowUser, sender: Sender) => {
      followers = follow.name :: followers
      sender ! TwitterSuccess()
    }
    case (f: GetFollowing, sender: Sender) =>
      sender ! FollowList(followers.toList)
    case (t: GetTweetListSize, sender: Sender) =>
      sender ! TweetListSize(tweets.size)
  }
}
