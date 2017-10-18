package Utils

import java.text.SimpleDateFormat
import java.util.Calendar

import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.{Dataset, SparkSession}

import scala.collection.mutable.ArrayBuffer

/**
  * Created by baronfeng on 2017/9/10.
  */
object Tools {

  val boss_guid = Array(
    "9184e76550f51034a4d780fbd42c850a",
    "5effa45308271035a4d780fbd48ad00a",
    "3677833c44e6103595a880fbd48ad00a",
    "9162462f1c871035a4d780fbd42c850a",
    "2c5a32ca8d191035afb780fbd42c850a",
    "6df254cc82ee1035913180fb0722850a",
    "3df5c4f7b1c5103599f780fbd48ad00a",
    "dffba92e94a74bd8a5e7a5e3d39e20de",
    "96525a1387f31034a4d780fb0722850a",
    "a386f167ac741034a4d780fbd48ad00a",
    "3b90413354821035a5b380fb0722850a",
    "87734285a6b71035a72c80fb0722850a",
    "bd9db9588ff0103597aa80fb0722850a",
    "2b40ca3f947e11e79d19a042d48ad00a",
    "1f00c3638ff011e79d19a042d42c850a",
    "7b202337979811e79d19a0429186d00a",
    "9887eb1090e91032bbcf80fbe202bb0a",
    "c2a452df31d31035a0e080fb9186d00a",
    "d68e02a30b6a1035a4d780fbd42c850a",
    "8b00615f9bc11034a4d780fbd42c850a",
    "36d9bf5124781034a4d780fb9186d00a",
    "86715224c4831030b355d48564437054",
    "11ed19b894c411e3b068abcd0e8dd00a",
    "b59150bd4f4a1035a73980fbd42c850a",
    "1ccfb00d87e711e79d19a042d42c850a",
    "6167e776ce6b11e385efabcd0296bb0a",
    "3e7241e4c0ba1035874680fbd48ad00a",
    "5729a6109fe51034a4d780fbd48ad00a",
    "fec7dace70461033bbcf80fb0722850a",
    "9184e76550f51034a4d780fbd42c850a",
    "2cfb5cf6335d1035a2e680fbd42c850a",
    "cfcbacb4b4371035b62080fbd48ad00a",
    "c9d4edbf679140ab9a12142b67941cf6",
    "ff093425bbd21033a4d780fbd48ad00a",
    "580a59ecdb2a1033a4d780fb5201850a",
    "a0fa9e910f0a1035a4d780fbd42c850a",
    "c2a452df31d31035a0e080fb9186d00a",
    "d68e02a30b6a1035a4d780fbd42c850a",
    "8b00615f9bc11034a4d780fbd42c850a").toSet


  def get_n_day(N: Int, format: String = "yyyyMMdd"): String= {
    var dateFormat: SimpleDateFormat = new SimpleDateFormat(format)
    var cal: Calendar = Calendar.getInstance()
    cal.add(Calendar.DATE, N)
    dateFormat.format(cal.getTime)
  }
  def get_last_month_date_str(format: String= "yyyyMMdd") : Array[String] = {
    get_last_days(20)
  }

  def get_last_days(num: Int = 30, format: String = "yyyyMMdd") : Array[String] = {
    val ret = new ArrayBuffer[String]
    for(i <- 1 to num) {
      ret += get_n_day(0 - i)
    }
    ret.toArray
  }

  def diff_date(date1: String, date2: String): Int = {
    val dateFormat1 = new SimpleDateFormat("yyyyMMdd")
    val d1 = dateFormat1.parse(date1).getTime
    val d2 = dateFormat1.parse(date2).getTime
    val days = ((d2 - d1) / (1000 * 3600 * 24)).toInt
    days
  }

  def is_path_exist(spark: SparkSession, file_path: String) : Boolean = {
    val conf = spark.sparkContext.hadoopConfiguration
    val fs = org.apache.hadoop.fs.FileSystem.get(conf)
    fs.exists(new org.apache.hadoop.fs.Path(file_path))
  }

  /**
    * 判断文件夹存在，而且文件夹不为空，必须得有_SUCCESS存在才可以
    * */
  def is_flag_exist(spark: SparkSession, file_path: String) : Boolean = {
    val conf = spark.sparkContext.hadoopConfiguration
    val fs = org.apache.hadoop.fs.FileSystem.get(conf)
    val fpath = new org.apache.hadoop.fs.Path(file_path)
    val exists = fs.exists(fpath)
    if(!exists)
      return false
    else {
      val fileStatus = fs.listStatus(fpath)
      for(i <- fileStatus if i.isDirectory) {
        if(is_flag_exist(spark, file_path + "/" + i.getPath.getName))
          return true
      }
      for(i <- fileStatus if i.isFile){
        if(i.getPath.getName.contains("_SUCCESS")) {
          return true
        }
      }
      false
    }
  }

  /**
    * 取得文件夹中最后一个文件，验证此文件夹合法性
    *
    * */
  def get_latest_subpath(spark: SparkSession, file_path: String) : String = {
    val reg_str = """\d+""".r
    val conf = spark.sparkContext.hadoopConfiguration
    val fs = org.apache.hadoop.fs.FileSystem.get(conf)
    val fpath = new org.apache.hadoop.fs.Path(file_path)
    val files = fs.listStatus(fpath)
    val time_sub_path = new ArrayBuffer[String]
    for(i <- files if i.isDirectory && is_flag_exist(spark, file_path + "/" + i.getPath.getName)){
      time_sub_path.append(i.getPath.getName)
    }
    val max_str = time_sub_path.map(line=> (line, reg_str.findFirstIn(line).getOrElse("0").toInt)).sortWith(_._2>_._2)
    if (max_str.isEmpty || max_str(0)._2 == 0) {
      println("illegal input_path: " + file_path)
      return null
    } else {
      for(str<-max_str) {
        val real_sub_path = file_path + "/" + str._1
        if(is_flag_exist(spark, real_sub_path)) {
          return file_path + "/" + max_str(0)._1
        }
      }
      return null
    }
  }

  def normalize(x: Double): Double = {
    // 2/(1+exp(-2.3 *x)) -1
    2 / (1 + Math.exp(-2.3 * x)) - 1
  }

  case class KeyValueWeight(key: String, value_weight: Seq[(String, Double)])

  def put_to_redis(input:Dataset[KeyValueWeight],
                   broadcast_redis_pool: Broadcast[TestRedisPool],
                   bzid:String,
                   prefix: String,
                   tag_type: Int,
                   weight_format: String = "%.4f",
                   expire_time: Int = 400000,
                   limit_num: Int = -1) : Unit = {
    println("put to redis.")
    val output = if(limit_num == -1) input else input.limit(limit_num)

    //要写入redis的数据，RDD[Map[String,String]]
    output.repartition(30).foreachPartition { iter =>
      //val redis = new Jedis(ip, port, expire_time)
      val redis = broadcast_redis_pool.value.getRedisPool.getResource   // lazy 加载 应该可以用
      val ppl = redis.pipelined() //使用pipeline 更高效的批处理
      var count = 0
      iter.foreach(f => {
        val key = bzid + "_" + prefix + "_" + f.key
        val values_data = f.value_weight.sortWith(_._2>_._2).map(line=>{
          line._1 + {
             if(tag_type == -1)
               ""
             else
               ":" + tag_type.toString
          } +
            ":" + line._2.formatted(weight_format)

        })
        val keys = Array(key)
        //ppl.del(keys: _*)
        ppl.rpush(key, values_data: _*)
        ppl.expire(key, 60*60*24*7)


        count += 1
        if(count % 20 == 0) {  // 每写20条同步一次
          ppl.sync()
        }
      })
      ppl.sync()
      redis.close()

    }
  }

  def delete_to_redis(input:Dataset[KeyValueWeight],
                   broadcast_redis_pool: Broadcast[TestRedisPool],
                   bzid:String,
                   prefix: String,
                   tag_type: Int,
                   weight_format: String = "%.4f",
                   expire_time: Int = 400000,
                   limit_num: Int = -1) : Unit = {
    println("redis delete")
    val output = if(limit_num == -1) input else input.limit(limit_num)

    //要写入redis的数据，RDD[Map[String,String]]
    output.repartition(50).foreachPartition { iter =>
      //val redis = new Jedis(ip, port, expire_time)
      val redis = broadcast_redis_pool.value.getRedisPool.getResource   // lazy 加载 应该可以用
    val ppl = redis.pipelined() //使用pipeline 更高效的批处理
    var count = 0
      iter.foreach(f => {
        val key = bzid + "_" + prefix + "_" + f.key
        val values_data = f.value_weight.sortWith(_._2>_._2).map(line=>{
          line._1 + ":" + tag_type.toString + ":" + line._2.formatted(weight_format)

        })
        val keys = Array(key)
        ppl.del(keys: _*)
        //ppl.rpush(key, values_data: _*)
        //ppl.expire(key, 60*60*24*2)


        count += 1
        if(count % 30 == 0) {
          ppl.sync()
        }
      })
      ppl.sync()
      redis.close()

    }
  }


  def main(args: Array[String]): Unit = {
    //get_last_month_date_str().foreach(println)
    println(get_n_day(-1))
  }

}
