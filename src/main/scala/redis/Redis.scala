package redis

import redis.RediscalaCompat.actor.*
import redis.RediscalaCompat.util.Helpers
import redis.commands.*
import scala.concurrent.*
import java.net.InetSocketAddress
import redis.actors.RedisSubscriberActorWithCallback
import redis.actors.RedisClientActor
import redis.api.pubsub.*
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.FiniteDuration

trait RedisCommands
    extends Keys
    with Strings
    with Hashes
    with Lists
    with Sets
    with SortedSets
    with Publish
    with Scripting
    with Connection
    with Server
    with HyperLogLog
    with Clusters
    with Geo

abstract class RedisClientActorLike(system: ActorSystem, redisDispatcher: RedisDispatcher, connectTimeout: Option[FiniteDuration] = None)
    extends ActorRequest {
  var host: String
  var port: Int
  val name: String
  val username: Option[String] = None
  val password: Option[String] = None
  val db: Option[Int] = None
  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(redisDispatcher.name)

  val redisConnection: ActorRef = system.actorOf(
    RedisClientActor
      .props(new InetSocketAddress(host, port), getConnectOperations, onConnectStatus, redisDispatcher.name, connectTimeout)
      .withDispatcher(redisDispatcher.name),
    name + '-' + Redis.tempName()
  )

  def reconnect(host: String = host, port: Int = port): Unit = {
    if (this.host != host || this.port != port) {
      this.host = host
      this.port = port
      redisConnection ! new InetSocketAddress(host, port)
    }
  }

  def onConnect(redis: RedisCommands): Unit = {
    (username, password) match {
      case (Some(username), Some(password)) => redis.auth(username, password)
      case (None, Some(password)) => redis.auth(password)
      case (_, _) =>
    }
    db.foreach(redis.select(_))
  }

  def onConnectStatus: Boolean => Unit = (status: Boolean) => {}

  def getConnectOperations: () => Seq[Operation[?, ?]] = () => {
    val self = this
    val redis = new BufferedRequest with RedisCommands {
      implicit val executionContext: ExecutionContext = self.executionContext
    }
    onConnect(redis)
    redis.operations.result()
  }

  /**
   * Disconnect from the server (stop the actor)
   */
  def stop(): Unit = {
    system stop redisConnection
  }
}

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

case class RedisBlockingClient(
  var host: String = "localhost",
  var port: Int = 6379,
  override val username: Option[String] = None,
  override val password: Option[String] = None,
  override val db: Option[Int] = None,
  name: String = "RedisBlockingClient",
  connectTimeout: Option[FiniteDuration] = None
)(implicit _system: ActorSystem, redisDispatcher: RedisDispatcher = Redis.dispatcher)
    extends RedisClientActorLike(_system, redisDispatcher, connectTimeout)
    with BLists {}

case class RedisPubSub(
  host: String = "localhost",
  port: Int = 6379,
  channels: Seq[String],
  patterns: Seq[String],
  onMessage: Message => Unit = _ => {},
  onPMessage: PMessage => Unit = _ => {},
  authUsername: Option[String] = None,
  authPassword: Option[String] = None,
  name: String = "RedisPubSub"
)(implicit system: ActorRefFactory, redisDispatcher: RedisDispatcher = Redis.dispatcher) {

  val redisConnection: ActorRef = system.actorOf(
    Props(
      classOf[RedisSubscriberActorWithCallback],
      new InetSocketAddress(host, port),
      channels,
      patterns,
      onMessage,
      onPMessage,
      authUsername,
      authPassword,
      onConnectStatus()
    ).withDispatcher(redisDispatcher.name),
    name + '-' + Redis.tempName()
  )

  /**
   * Disconnect from the server (stop the actor)
   */
  def stop(): Unit = {
    system stop redisConnection
  }

  def subscribe(channels: String*): Unit = {
    redisConnection ! SUBSCRIBE(channels*)
  }

  def unsubscribe(channels: String*): Unit = {
    redisConnection ! UNSUBSCRIBE(channels*)
  }

  def psubscribe(patterns: String*): Unit = {
    redisConnection ! PSUBSCRIBE(patterns*)
  }

  def punsubscribe(patterns: String*): Unit = {
    redisConnection ! PUNSUBSCRIBE(patterns*)
  }

  def onConnectStatus(): Boolean => Unit = (status: Boolean) => {}
}

case class SentinelMonitoredRedisClient(
  sentinels: Seq[(String, Int)] = Seq(("localhost", 26379)),
  master: String,
  username: Option[String] = None,
  password: Option[String] = None,
  db: Option[Int] = None,
  name: String = "SMRedisClient"
)(implicit system: ActorSystem, redisDispatcher: RedisDispatcher = Redis.dispatcher)
    extends SentinelMonitoredRedisClientLike(system, redisDispatcher)
    with RedisCommands
    with Transactions {

  val redisClient: RedisClient = withMasterAddr((ip, port) => {
    RedisClient(ip, port, username, password, db, name)
  })
  override val onNewSlave = (ip: String, port: Int) => {}
  override val onSlaveDown = (ip: String, port: Int) => {}
}

case class SentinelMonitoredRedisBlockingClient(
  sentinels: Seq[(String, Int)] = Seq(("localhost", 26379)),
  master: String,
  username: Option[String] = None,
  password: Option[String] = None,
  db: Option[Int] = None,
  name: String = "SMRedisBlockingClient"
)(implicit system: ActorSystem, redisDispatcher: RedisDispatcher = Redis.dispatcher)
    extends SentinelMonitoredRedisClientLike(system, redisDispatcher)
    with BLists {
  val redisClient: RedisBlockingClient = withMasterAddr((ip, port) => {
    RedisBlockingClient(ip, port, username, password, db, name)
  })
  override val onNewSlave = (ip: String, port: Int) => {}
  override val onSlaveDown = (ip: String, port: Int) => {}
}

case class RedisDispatcher(name: String)

private[redis] object Redis {
  val dispatcher = RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")

  val tempNumber = new AtomicLong

  def tempName() = Helpers.base64(tempNumber.getAndIncrement())

}
