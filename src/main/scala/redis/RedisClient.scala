package redis

import redis.RediscalaCompat.actor.*
import redis.commands.*
import scala.concurrent.duration.FiniteDuration

case class RedisClient(
  var host: String = "localhost",
  var port: Int = 6379,
  override val username: Option[String] = None,
  override val password: Option[String] = None,
  override val db: Option[Int] = None,
  name: String = "RedisClient",
  connectTimeout: Option[FiniteDuration] = None
)(implicit _system: ActorSystem, redisDispatcher: RedisDispatcher = Redis.dispatcher)
    extends RedisClientActorLike(_system, redisDispatcher, connectTimeout)
    with RedisCommands
    with Transactions {}
