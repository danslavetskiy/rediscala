package redis.api.strings

import redis.*
import redis.RediscalaCompat.util.ByteString
import redis.protocol.RedisProtocolRequest

case class Getdel[K, R](key: K)(implicit redisKey: ByteStringSerializer[K], deserializerR: ByteStringDeserializer[R])
    extends SimpleClusterKey[K]
    with RedisCommandBulkOptionByteString[R] {
  def isMasterOnly = false
  val encodedRequest: ByteString = RedisProtocolRequest.multiBulk("GETDEL", Seq(keyAsString))
  val deserializer: ByteStringDeserializer[R] = deserializerR
}
