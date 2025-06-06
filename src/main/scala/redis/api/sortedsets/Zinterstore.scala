package redis.api.sortedsets

import org.apache.pekko.util.ByteString
import redis.*
import redis.api.Aggregate
import redis.api.SUM

case class Zinterstore[KD: ByteStringSerializer, K: ByteStringSerializer, KK: ByteStringSerializer](
  destination: KD,
  key: K,
  keys: Seq[KK],
  aggregate: Aggregate = SUM
) extends RedisCommandIntegerLong {
  def isMasterOnly = true
  val encodedRequest: ByteString = encode("ZINTERSTORE", Zstore.buildArgs(destination, key, keys, aggregate))
}
