package redis.commands

import org.apache.pekko.util.ByteString
import redis.*
import redis.api.*
import redis.api.ZaddOption.CH
import redis.api.ZaddOption.NX
import redis.api.ZaddOption.XX
import scala.concurrent.Await
import scala.concurrent.Future

class SortedSetsSpec extends RedisDockerServer {

  "Sorted Sets commands" should {
    "ZADD" in {
      val r = for {
        version <- redisVersion()
        ge_3_0_2 = version.exists(_ >= RedisVersion(3, 0, 2))
        _ <- redis.del("zaddKey")
        z1 <- redis.zadd("zaddKey", 1.0 -> "one", (1, "uno"), (2, "two"))
        z2 <- redis.zadd("zaddKey", (3, "two"))
        z3 <- if (ge_3_0_2) redis.zaddWithOptions("zaddKey", Seq(XX, CH), 0.9 -> "one", (3, "three")) else Future.successful(1)
        z4 <- if (ge_3_0_2) redis.zaddWithOptions("zaddKey", Seq(NX), 0.8 -> "one", (4, "three")) else Future.successful(1)
        _ <- redis.zadd("zaddKey", 1.0 -> "one", (4, "three"))
        zr <- redis.zrangeWithscores("zaddKey", 0, -1)
      } yield {
        assert(z1 == 3)
        assert(z2 == 0)
        assert(z3 == 1)
        assert(z4 == 1)
        assert(zr == Seq((ByteString("one"), 1.0), (ByteString("uno"), 1), (ByteString("two"), 3), (ByteString("three"), 4)))
      }
      Await.result(r, timeOut)
    }

    "ZCARD" in {
      val r = for {
        _ <- redis.del("zcardKey")
        c1 <- redis.zcard("zcardKey")
        _ <- redis.zadd("zcardKey", 1.0 -> "one", (2, "two"))
        c2 <- redis.zcard("zcardKey")
      } yield {
        assert(c1 == 0)
        assert(c2 == 2)
      }
      Await.result(r, timeOut)
    }

    "ZCOUNT" in {
      val r = for {
        _ <- redis.del("zcountKey")
        c1 <- redis.zcount("zcountKey")
        _ <- redis.zadd("zcountKey", 1.0 -> "one", (2, "two"), (3, "three"))
        c2 <- redis.zcount("zcountKey")
        c3 <- redis.zcount("zcountKey", Limit(1, inclusive = false), Limit(3))
      } yield {
        assert(c1 == 0)
        assert(c2 == 3)
        assert(c3 == 2)
      }
      Await.result(r, timeOut)
    }

    "ZINCRBY" in {
      val r = for {
        _ <- redis.del("zincrbyKey")
        _ <- redis.zadd("zincrbyKey", 1.0 -> "one", (2, "two"))
        d <- redis.zincrby("zincrbyKey", 2.1, "one")
        d2 <- redis.zincrby("zincrbyKey", 2.1, "notexisting")
        zr <- redis.zrangeWithscores("zincrbyKey", 0, -1)
      } yield {
        assert(d == 3.1)
        assert(d2 == 2.1)
        assert(zr == Seq((ByteString("two"), 2.0), (ByteString("notexisting"), 2.1), (ByteString("one"), 3.1)))
      }
      Await.result(r, timeOut)
    }

    "ZINTERSTORE" in {
      val r = for {
        _ <- redis.del("zinterstoreKey1")
        _ <- redis.del("zinterstoreKey2")
        z1 <- redis.zadd("zinterstoreKey1", 1.0 -> "one", (2, "two"))
        z2 <- redis.zadd("zinterstoreKey2", 1.0 -> "one", (2, "two"), (3, "three"))
        zinterstore <- redis.zinterstore("zinterstoreKeyOut", "zinterstoreKey1", Seq("zinterstoreKey2"))
        zinterstoreWeighted <- redis.zinterstoreWeighted("zinterstoreKeyOutWeighted", Map("zinterstoreKey1" -> 2, "zinterstoreKey2" -> 3))
        zr <- redis.zrangeWithscores("zinterstoreKeyOut", 0, -1)
        zrWeighted <- redis.zrangeWithscores("zinterstoreKeyOutWeighted", 0, -1)
      } yield {
        assert(z1 == 2)
        assert(z2 == 3)
        assert(zinterstore == 2)
        assert(zinterstoreWeighted == 2)
        assert(zr == Seq((ByteString("one"), 2), (ByteString("two"), 4)))
        assert(zrWeighted == Seq((ByteString("one"), 5), (ByteString("two"), 10)))
      }
      Await.result(r, timeOut)
    }

    "ZRANGE" in {
      val r = for {
        _ <- redis.del("zrangeKey")
        z1 <- redis.zadd("zrangeKey", 1.0 -> "one", (2, "two"), (3, "three"))
        zr1 <- redis.zrange("zrangeKey", 0, -1)
        zr2 <- redis.zrange("zrangeKey", 2, 3)
        zr3 <- redis.zrange("zrangeKey", -2, -1)
      } yield {
        assert(z1 == 3)
        assert(zr1 == Seq(ByteString("one"), ByteString("two"), ByteString("three")))
        assert(zr2 == Seq(ByteString("three")))
        assert(zr3 == Seq(ByteString("two"), ByteString("three")))
      }
      Await.result(r, timeOut)
    }

    "ZRANGEBYSCORE" in {
      val r = for {
        _ <- redis.del("zrangebyscoreKey")
        z1 <- redis.zadd("zrangebyscoreKey", 1.0 -> "one", (2, "two"), (3, "three"))
        zr1 <- redis.zrangebyscore("zrangebyscoreKey", Limit(Double.NegativeInfinity), Limit(Double.PositiveInfinity))
        zr1Limit <- redis.zrangebyscore("zrangebyscoreKey", Limit(Double.NegativeInfinity), Limit(Double.PositiveInfinity), Some(1L -> 2L))
        zr2 <- redis.zrangebyscore("zrangebyscoreKey", Limit(1), Limit(2))
        zr2WithScores <- redis.zrangebyscoreWithscores("zrangebyscoreKey", Limit(1), Limit(2))
        zr3 <- redis.zrangebyscore("zrangebyscoreKey", Limit(1, inclusive = false), Limit(2))
        zr4 <- redis.zrangebyscore("zrangebyscoreKey", Limit(1, inclusive = false), Limit(2, inclusive = false))
      } yield {
        assert(z1 == 3)
        assert(zr1 == Seq(ByteString("one"), ByteString("two"), ByteString("three")))
        assert(zr1Limit == Seq(ByteString("two"), ByteString("three")))
        assert(zr2 == Seq(ByteString("one"), ByteString("two")))
        assert(zr2WithScores == Seq((ByteString("one"), 1), (ByteString("two"), 2)))
        assert(zr3 == Seq(ByteString("two")))
        assert(zr4 == Seq())
      }
      Await.result(r, timeOut)
    }

    "ZRANK" in {
      val r = for {
        _ <- redis.del("zrankKey")
        z1 <- redis.zadd("zrankKey", 1.0 -> "one", (2, "two"), (3, "three"))
        zr1 <- redis.zrank("zrankKey", "three")
        zr2 <- redis.zrank("zrankKey", "four")
      } yield {
        assert(z1 == 3)
        assert(zr1 == Some(2))
        assert(zr2.isEmpty)
      }
      Await.result(r, timeOut)
    }

    "ZREM" in {
      val r = for {
        _ <- redis.del("zremKey")
        z1 <- redis.zadd("zremKey", 1.0 -> "one", (2, "two"), (3, "three"))
        z2 <- redis.zrem("zremKey", "two", "nonexisting")
        zr <- redis.zrangeWithscores("zremKey", 0, -1)
      } yield {
        assert(z1 == 3)
        assert(z2 == 1)
        assert(zr == Seq((ByteString("one"), 1), (ByteString("three"), 3)))
      }
      Await.result(r, timeOut)
    }

    "ZREMRANGEBYLEX" in {
      val r = for {
        _ <- redis.del("zremrangebylexKey")
        z1 <- redis.zadd("zremrangebylexKey", 0d -> "a", 0d -> "b", 0d -> "c", 0d -> "d", 0d -> "e", 0d -> "f", 0d -> "g")
        z2 <- redis.zremrangebylex("zremrangebylexKey", "[z", "[d")
        z3 <- redis.zremrangebylex("zremrangebylexKey", "[b", "[d")
        zrange1 <- redis.zrange("zremrangebylexKey", 0, -1)
      } yield {
        assert(z1 == 7)
        assert(z2 == 0)
        assert(z3 == 3)
        assert(zrange1 == Seq(ByteString("a"), ByteString("e"), ByteString("f"), ByteString("g")))
      }
      Await.result(r, timeOut)
    }

    "ZREMRANGEBYRANK" in {
      val r = for {
        _ <- redis.del("zremrangebyrankKey")
        z1 <- redis.zadd("zremrangebyrankKey", 1.0 -> "one", (2, "two"), (3, "three"))
        z2 <- redis.zremrangebyrank("zremrangebyrankKey", 0, 1)
        zr <- redis.zrangeWithscores("zremrangebyrankKey", 0, -1)
      } yield {
        assert(z1 == 3)
        assert(z2 == 2)
        assert(zr == Seq((ByteString("three"), 3)))
      }
      Await.result(r, timeOut)
    }

    "ZREMRANGEBYSCORE" in {
      val r = for {
        _ <- redis.del("zremrangebyscoreKey")
        z1 <- redis.zadd("zremrangebyscoreKey", 1.0 -> "one", (2, "two"), (3, "three"))
        z2 <- redis.zremrangebyscore("zremrangebyscoreKey", Limit(Double.NegativeInfinity), Limit(2, inclusive = false))
        zr <- redis.zrangeWithscores("zremrangebyscoreKey", 0, -1)
      } yield {
        assert(z1 == 3)
        assert(z2 == 1)
        assert(zr == Seq((ByteString("two"), 2), (ByteString("three"), 3)))
      }
      Await.result(r, timeOut)
    }

    "ZREVRANGE" in {
      val r = for {
        _ <- redis.del("zrevrangeKey")
        z1 <- redis.zadd("zrevrangeKey", 1.0 -> "one", (2, "two"), (3, "three"))
        zr1 <- redis.zrevrange("zrevrangeKey", 0, -1)
        zr2 <- redis.zrevrange("zrevrangeKey", 2, 3)
        zr3 <- redis.zrevrange("zrevrangeKey", -2, -1)
        zr3WithScores <- redis.zrevrangeWithscores("zrevrangeKey", -2, -1)
      } yield {
        assert(z1 == 3)
        assert(zr1 == Seq(ByteString("three"), ByteString("two"), ByteString("one")))
        assert(zr2 == Seq(ByteString("one")))
        assert(zr3 == Seq(ByteString("two"), ByteString("one")))
        assert(zr3WithScores == Seq((ByteString("two"), 2), (ByteString("one"), 1)))
      }
      Await.result(r, timeOut)
    }

    "ZREVRANGEBYSCORE" in {
      val r = for {
        _ <- redis.del("zrevrangebyscoreKey")
        z1 <- redis.zadd("zrevrangebyscoreKey", 1.0 -> "one", (2, "two"), (3, "three"))
        zr1 <- redis.zrevrangebyscore("zrevrangebyscoreKey", Limit(Double.PositiveInfinity), Limit(Double.NegativeInfinity))
        zr2 <- redis.zrevrangebyscore("zrevrangebyscoreKey", Limit(2), Limit(1))
        zr2WithScores <- redis.zrevrangebyscoreWithscores("zrevrangebyscoreKey", Limit(2), Limit(1))
        zr3 <- redis.zrevrangebyscore("zrevrangebyscoreKey", Limit(2), Limit(1, inclusive = false))
        zr4 <- redis.zrevrangebyscore("zrevrangebyscoreKey", Limit(2, inclusive = false), Limit(1, inclusive = false))
      } yield {
        assert(z1 == 3)
        assert(zr1 == Seq(ByteString("three"), ByteString("two"), ByteString("one")))
        assert(zr2 == Seq(ByteString("two"), ByteString("one")))
        assert(zr2WithScores == Seq((ByteString("two"), 2), (ByteString("one"), 1)))
        assert(zr3 == Seq(ByteString("two")))
        assert(zr4 == Seq())
      }
      Await.result(r, timeOut)
    }

    "ZREVRANK" in {
      val r = for {
        _ <- redis.del("zrevrankKey")
        z1 <- redis.zadd("zrevrankKey", 1.0 -> "one", (2, "two"), (3, "three"))
        zr1 <- redis.zrevrank("zrevrankKey", "one")
        zr2 <- redis.zrevrank("zrevrankKey", "four")
      } yield {
        assert(z1 == 3)
        assert(zr1 == Some(2))
        assert(zr2.isEmpty)
      }
      Await.result(r, timeOut)
    }

    "ZSCAN" in {
      val r = for {
        _ <- redis.del("zscan")
        _ <- redis.zadd("zscan", (1 to 20).map(x => x.toDouble -> x.toString)*)
        scanResult <- redis.zscan[String]("zscan", count = Some(100))
      } yield {
        assert(scanResult.index == 0)
        assert(scanResult.data == (1 to 20).map(x => x.toDouble -> x.toString))
      }

      Await.result(r, timeOut)
    }

    "ZSCORE" in {
      val r = for {
        _ <- redis.del("zscoreKey")
        z1 <- redis.zadd(
          "zscoreKey",
          1.1 -> "one",
          (2, "two"),
          (3, "three"),
          Double.PositiveInfinity -> "positiveinf",
          Double.NegativeInfinity -> "negativeinf"
        )
        zr1 <- redis.zscore("zscoreKey", "one")
        zr2 <- redis.zscore("zscoreKey", "notexisting")
        zr3 <- redis.zscore("zscoreKey", "positiveinf")
        zr4 <- redis.zscore("zscoreKey", "negativeinf")
      } yield {
        assert(z1 == 5)
        assert(zr1 == Some(1.1))
        assert(zr2 == None)
        assert(zr3 == Some(Double.PositiveInfinity))
        assert(zr4 == Some(Double.NegativeInfinity))
      }
      Await.result(r, timeOut)
    }

    "ZUNIONSTORE" in {
      val r = for {
        _ <- redis.del("zunionstoreKey1")
        _ <- redis.del("zunionstoreKey2")
        z1 <- redis.zadd("zunionstoreKey1", 1.0 -> "one", (2, "two"))
        z2 <- redis.zadd("zunionstoreKey2", 1.0 -> "one", (2, "two"), (3, "three"))
        zunionstore <- redis.zunionstore("zunionstoreKeyOut", "zunionstoreKey1", Seq("zunionstoreKey2"))
        zr <- redis.zrangeWithscores("zunionstoreKeyOut", 0, -1)
        zunionstoreWeighted <- redis.zunionstoreWeighted("zunionstoreKeyOutWeighted", Map("zunionstoreKey1" -> 2, "zunionstoreKey2" -> 3))
        zrWeighted <- redis.zrangeWithscores("zunionstoreKeyOutWeighted", 0, -1)
      } yield {
        assert(z1 == 2)
        assert(z2 == 3)
        assert(zunionstore == 3)
        assert(zr == Seq((ByteString("one"), 2), (ByteString("three"), 3), (ByteString("two"), 4)))
        assert(zunionstoreWeighted == 3)
        assert(zrWeighted == Seq((ByteString("one"), 5), (ByteString("three"), 9), (ByteString("two"), 10)))
      }
      Await.result(r, timeOut)
    }

    "ZRANGEBYLEX" in {
      val r = for {
        _ <- redis.del("zrangebylexKey")
        z1 <- redis.zadd("zrangebylexKey", (0, "lexA"), (0, "lexB"), (0, "lexC"))
        zr1 <- redis.zrangebylex("zrangebylexKey", Some("[lex"), None, None)
        zr2 <- redis.zrangebylex("zrangebylexKey", Some("[lex"), None, Some((0L, 1L)))
      } yield {
        assert(z1 == 3)
        assert(zr1 == Seq(ByteString("lexA"), ByteString("lexB"), ByteString("lexC")))
        assert(zr2 == Seq(ByteString("lexA")))
      }
      Await.result(r, timeOut)
    }

    "ZREVRANGEBYLEX" in {
      val r = for {
        _ <- redis.del("zrevrangebylexKey")
        z1 <- redis.zadd("zrevrangebylexKey", (0, "lexA"), (0, "lexB"), (0, "lexC"))
        zr1 <- redis.zrevrangebylex("zrevrangebylexKey", None, Some("[lex"), None)
        zr2 <- redis.zrevrangebylex("zrevrangebylexKey", None, Some("[lex"), Some((0L, 1L)))
      } yield {
        assert(z1 == 3)
        assert(zr1 == Seq(ByteString("lexC"), ByteString("lexB"), ByteString("lexA")))
        assert(zr2 == Seq(ByteString("lexC")))
      }
      Await.result(r, timeOut)
    }

    "ZPOPMIN" in {
      val r = for {
        _ <- redis.del("zpopminKey")
        z1 <- redis.zadd("zpopminKey", (1, "one"))
        z1r <- redis.zpopmin("zpopminKey")
        z2 <- redis.zadd("zpopminKey", (3, "three"), (2, "two"), (1, "one"))
        z2r <- redis.zpopmin("zpopminKey", 2)
      } yield {
        assert(z1 == 1)
        assert(z1r == Seq(ByteString("one"), ByteString("1")))
        assert(z2 == 3)
        assert(z2r == Seq(ByteString("one"), ByteString("1"), ByteString("two"), ByteString("2")))
      }
      Await.result(r, timeOut)
    }

    "ZPOPMAX" in {
      val r = for {
        _ <- redis.del("zpopmaxKey")
        z1 <- redis.zadd("zpopmaxKey", (1, "one"))
        z1r <- redis.zpopmax("zpopmaxKey")
        _ <- redis.del("zpopmaxKey")
        z2 <- redis.zadd("zpopmaxKey", (3, "three"), (2, "two"), (1, "one"))
        z2r <- redis.zpopmax("zpopmaxKey", 2)
      } yield {
        assert(z1 == 1)
        assert(z1r == Seq(ByteString("one"), ByteString("1")))
        assert(z2 == 3)
        assert(z2r == Seq(ByteString("three"), ByteString("3"), ByteString("two"), ByteString("2")))
      }
      Await.result(r, timeOut)
    }
  }
}
