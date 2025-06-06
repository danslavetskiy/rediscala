package redis.api

import org.apache.pekko.util.ByteString

case class LimitOffsetCount(offset: Long, count: Long) {
  def toByteString: Seq[ByteString] = Seq(ByteString("LIMIT"), ByteString(offset.toString), ByteString(count.toString))
}
