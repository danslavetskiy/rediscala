package redis

import java.net.InetSocketAddress
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.Props
import org.apache.pekko.testkit.TestActorRef
import org.apache.pekko.testkit.TestProbe
import org.apache.pekko.util.ByteString
import redis.actors.RedisSubscriberActor
import redis.api.pubsub.*
import scala.concurrent.Await

class RedisPubSubSpec extends RedisDockerServer {

  "PubSub test" should {
    "ok (client + callback)" in {

      var redisPubSub: RedisPubSub = null

      redisPubSub = RedisPubSub(
        port = port,
        channels = Seq("chan1", "secondChannel"),
        patterns = Seq("chan*"),
        onMessage = (m: Message) => {
          redisPubSub.unsubscribe("chan1", "secondChannel")
          redisPubSub.punsubscribe("chan*")
          redisPubSub.subscribe(m.data.utf8String)
          redisPubSub.psubscribe("next*")
        }
      )

      Thread.sleep(2000)

      val p = redis.publish("chan1", "nextChan")
      val noListener = redis.publish("noListenerChan", "message")
      assert(Await.result(p, timeOut) == 2)
      assert(Await.result(noListener, timeOut) == 0)

      Thread.sleep(2000)
      val nextChan = redis.publish("nextChan", "message")
      val p2 = redis.publish("chan1", "nextChan")
      assert(Await.result(p2, timeOut) == 0)
      assert(Await.result(nextChan, timeOut) == 2)
    }

    "ok (actor)" in {
      val probeMock = TestProbe()
      val channels = Seq("channel")
      val patterns = Seq("pattern.*")

      val subscriberActor = TestActorRef[SubscriberActor](
        Props(classOf[SubscriberActor], new InetSocketAddress("localhost", port), channels, patterns, probeMock.ref)
          .withDispatcher(Redis.dispatcher.name),
        "SubscriberActor"
      )
      import scala.concurrent.duration.*

      system.scheduler.scheduleOnce(2.seconds)(redis.publish("channel", "value"))

      assert(probeMock.expectMsgType[Message](5.seconds) == Message("channel", ByteString("value")))

      redis.publish("pattern.1", "value")

      assert(probeMock.expectMsgType[PMessage] == PMessage("pattern.*", "pattern.1", ByteString("value")))

      subscriberActor.underlyingActor.subscribe("channel2")
      subscriberActor.underlyingActor.unsubscribe("channel")

      system.scheduler.scheduleOnce(2.seconds) {
        redis.publish("channel", "value")
        redis.publish("channel2", "value")
      }
      assert(probeMock.expectMsgType[Message](5.seconds) == Message("channel2", ByteString("value")))

      subscriberActor.underlyingActor.unsubscribe("channel2")
      system.scheduler.scheduleOnce(1.second) {
        redis.publish("channel2", ByteString("value"))
      }
      probeMock.expectNoMessage(3.seconds)

      subscriberActor.underlyingActor.subscribe("channel2")
      system.scheduler.scheduleOnce(1.second) {
        redis.publish("channel2", ByteString("value"))
      }
      assert(probeMock.expectMsgType[Message](5.seconds) == Message("channel2", ByteString("value")))

      subscriberActor.underlyingActor.psubscribe("pattern2.*")
      subscriberActor.underlyingActor.punsubscribe("pattern.*")

      system.scheduler.scheduleOnce(2.seconds) {
        redis.publish("pattern2.match", ByteString("value"))
        redis.publish("pattern.*", ByteString("value"))
      }
      assert(probeMock.expectMsgType[PMessage](5.seconds) == PMessage("pattern2.*", "pattern2.match", ByteString("value")))

      subscriberActor.underlyingActor.punsubscribe("pattern2.*")
      system.scheduler.scheduleOnce(2.seconds) {
        redis.publish("pattern2.match", ByteString("value"))
      }
      probeMock.expectNoMessage(3.seconds)

      subscriberActor.underlyingActor.psubscribe("pattern.*")
      system.scheduler.scheduleOnce(2.seconds) {
        redis.publish("pattern.*", ByteString("value"))
      }
      assert(probeMock.expectMsgType[PMessage](5.seconds) == PMessage("pattern.*", "pattern.*", ByteString("value")))
    }
  }

}

class SubscriberActor(address: InetSocketAddress, channels: Seq[String], patterns: Seq[String], probeMock: ActorRef)
    extends RedisSubscriberActor(address, channels, patterns, None, None, (b: Boolean) => ()) {

  override def onMessage(m: Message) = {
    probeMock ! m
  }

  def onPMessage(pm: PMessage): Unit = {
    probeMock ! pm
  }
}
