package org.orleans.developer.twitter
import org.orleans.developer.twitter.TwitterMessages._
import org.orleans.silo.services.grain.GrainReference

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TwitterAcountRef extends GrainReference {
  def tweet(str: String) =
    (this.grainRef ! Tweet(str, System.currentTimeMillis().toString))

  def followUser(twitter: TwitterRef, username: String): Future[Any] = {
    val userExists = (twitter.grainRef ? UserExists(username))
    userExists flatMap {
      case TwitterFailure("User already exists.") =>
        this.grainRef ? FollowUser(username)
      case x => throw new IllegalArgumentException(s"here $x")
    }
  }

  def getFollowingList(): Future[List[String]] = {
    (this.grainRef ? GetFollowing()) map {
      case FollowList(followers: List[String]) => {
        followers
      }
      case x => throw new IllegalArgumentException(x.toString)
    }
  }

  def getTweets(): Future[List[Tweet]] = {
    (this.grainRef ? GetTweetList()) map {
      case TweetList(tweets: List[String]) => tweets
      case x                               => throw new IllegalArgumentException(x.toString)
    }
  }

  def getAmountOfTweets(): Future[Int] = {
    (this.grainRef ? GetTweetListSize()) map {
      case TweetListSize(tweetSize: Int) => tweetSize
      case x                             => throw new IllegalArgumentException(x.toString)
    }
  }

  def getTimeline(twitter: TwitterRef, timestamp: Long = 0) = {
    (getFollowingList()) flatMap { followers: List[String] =>
      Future.traverse(followers) { user =>
        twitter.getAccount(user)
      }
    } flatMap { followerRefs: List[TwitterAcountRef] =>
      Future
        .traverse(followerRefs) { ref =>
          ref.getTweets()
        }
    } map {
      _.flatten
    } map {
      _.filter(_.timestamp.toLong >= timestamp)
    }
  }

}
