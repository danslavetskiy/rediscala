package redis.api.blists

import org.apache.pekko.util.ByteString
import redis.ByteStringDeserializer
import redis.ByteStringSerializer
import redis.RedisCommandBulkOptionByteString
import redis.api.ListDirection
import scala.concurrent.duration.FiniteDuration

case class Blmove[KS, KD, R](
  source: KS,
  destination: KD,
  from: ListDirection,
  to: ListDirection,
  timeout: FiniteDuration
)(using
  sourceSer: ByteStringSerializer[KS],
  destSer: ByteStringSerializer[KD],
  override val deserializer: ByteStringDeserializer[R],
) extends RedisCommandBulkOptionByteString[R] {
  def isMasterOnly = true
  val encodedRequest: ByteString = encode(
    "BLMOVE",
    Seq(
      sourceSer.serialize(source),
      destSer.serialize(destination),
      ByteString.fromString(from.value),
      ByteString.fromString(to.value),
      ByteString(timeout.toSeconds.toString)
    )
  )
}
