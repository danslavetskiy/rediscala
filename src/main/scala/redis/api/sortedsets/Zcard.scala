package redis.api.sortedsets

import redis.RediscalaCompat.util.ByteString
import redis.*

case class Zcard[K](key: K)(implicit keySeria: ByteStringSerializer[K]) extends SimpleClusterKey[K] with RedisCommandIntegerLong {
  def isMasterOnly = false
  val encodedRequest: ByteString = encode("ZCARD", Seq(keyAsString))
}
