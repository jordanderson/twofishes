package com.foursquare.twofishes.replayer

import com.twitter.conversions.time._
import com.foursquare.twofishes._
import com.twitter.finagle.Service
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.thrift.{ThriftClientFramedCodec, ThriftServerFramedCodec, ThriftClientRequest}
import java.net.InetSocketAddress
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.transport.TSocket
import scala.collection.JavaConverters._

import com.twitter.conversions.time._
import com.twitter.finagle.builder.ClientBuilder
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.protocol.TMessage
import org.apache.thrift.protocol.{TProtocolFactory, TProtocol}
import com.twitter.util.Future

import org.apache.thrift._
import org.apache.thrift.protocol._
import org.apache.thrift.transport._
import org.apache.hadoop.io.SequenceFile.Reader
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.SequenceFile
import java.nio._
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.protocol.TCompactProtocol
import org.apache.thrift.{TDeserializer, TSerializer}
import org.apache.hadoop.io.BytesWritable
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.io.DataOutputBuffer;

import java.util.concurrent.{ConcurrentHashMap, Executors}
import com.twitter.util.{Future, FuturePool, RingBuffer}

object ThriftPrinter {
  def typeStr(t: Byte): String = {
    t match {
      case TType.BOOL => "BOOL"
      case TType.BYTE => "BYTE"
      case TType.DOUBLE => "DOUBLE"
      case TType.ENUM => "ENUM"
      case TType.I16 => "I16"
      case TType.I32 => "I32"
      case TType.I64 => "I64"
      case TType.LIST => "LIST"
      case TType.MAP => "MAP"
      case TType.SET => "SET"
      case TType.STOP => "STOP"
      case TType.STRING => "STRING"
      case TType.STRUCT => "STRUCT"
      case TType.VOID => "VOID"
      case _ => throw new RuntimeException("unrecognized type byte %d".format(t.toInt))
    }
  }

  def printValue(t: Byte, iprot: TProtocol, indent: String): Unit = {
    t match {
      case TType.BOOL => println(iprot.readBool())
      case TType.BYTE => println(iprot.readByte())
      case TType.DOUBLE => println(iprot.readDouble())
      case TType.ENUM =>
        TProtocolUtil.skip(iprot, t)
        println("ENUM")
      case TType.I16 => println(iprot.readI16().toString)
      case TType.I32 => println(iprot.readI32().toString)
      case TType.I64 => println(iprot.readI64.toString)
      case TType.LIST =>
        TProtocolUtil.skip(iprot, t)
        println("LIST (not printing internals)")
      case TType.MAP =>
        TProtocolUtil.skip(iprot, t)
        println("MAP (not printing internals)")
      case TType.SET =>
        TProtocolUtil.skip(iprot, t)
        println("SET (not printing internals)")
      case TType.STOP =>
        TProtocolUtil.skip(iprot, t)
        println("STOP")
      case TType.STRING => println(iprot.readString())
      case TType.STRUCT => prettyPrintThrift(iprot, indent + "  ")
      case TType.VOID =>
        TProtocolUtil.skip(iprot, t)
        println("VOID (not printing internals)")
    }
  }

  def prettyPrintThrift(iprot: TProtocol, indent: String = ""): Unit = {
    iprot.readStructBegin()
    println("%s{".format(indent))
    var field_header: org.apache.thrift.protocol.TField = iprot.readFieldBegin()
    while (field_header.`type` != org.apache.thrift.protocol.TType.STOP) {
      val line = "%s  ID: %d, type: %s, %svalue: ".format(indent, field_header.id,
        typeStr(field_header.`type`),
        Option(field_header.name).filter(_.nonEmpty).map(n => "name: %s, ".format(n)).getOrElse(""))
      print(line)
      printValue(field_header.`type`, iprot, indent)
      iprot.readFieldEnd()
      field_header = iprot.readFieldBegin()
    } // end while
    iprot.readStructEnd()
    println("%s}".format(indent))
  }

  def prettyPrintThriftMessage(iprot: TProtocol, indent: String = ""): Unit = {
    val msg = iprot.readMessageBegin()
    // println(msg)
    prettyPrintThrift(iprot)
    iprot.readMessageEnd()
  }
}

class SeqFileByteIterator(fname: String) extends Iterator[Array[Byte]] {
  val conf = new Configuration()
  val fs = FileSystem.get(conf)
  val key = NullWritable.get()
  val reader = new SequenceFile.Reader(fs, new Path(fname), conf)
  var i = 0
  val bw = new BytesWritable()
  var done: Boolean = false

  var nextVal: Array[Byte] = null

  def hasNext = {
    done = reader.next(key, bw)
    nextVal = bw.copyBytes()
    done
  }

  def next = nextVal
}

object GeocoderTestClient {
  def main(args: Array[String]) {
    println(args.toList)
    val hostport = args(1)
    val seqfile = args(2)
    val limit = args(3).toInt

    val service = (ClientBuilder()
      .hosts(hostport)
      .name("geocoderv2")
      .hostConnectionLimit(100)
      .tcpConnectTimeout(5.seconds)
      .codec(ThriftClientFramedCodec())
      .build())
    val factory = new TBinaryProtocol.Factory()

    def callService(
      target: String,
      protocolFactory: TProtocolFactory,
      args: TBase[_, _]): Future[TProtocol] = {
      try {
        // TODO: size
        val __memoryTransport__ = new org.apache.thrift.transport.TMemoryBuffer(512)
        val __prot__ = protocolFactory.getProtocol(__memoryTransport__)
        __prot__.writeMessageBegin(new org.apache.thrift.protocol.TMessage(target, org.apache.thrift.protocol.TMessageType.CALL, 0))
        args.write(__prot__)
        __prot__.writeMessageEnd()

        val __buffer__ = new Array[Byte](__memoryTransport__.length())
        Array.copy(__memoryTransport__.getArray(), 0, __buffer__, 0, __memoryTransport__.length())
        val __request__ = new com.twitter.finagle.thrift.ThriftClientRequest(__buffer__, false)
        val __done__ = service.apply(__request__)
        __done__.map { __buffer__ =>
          val __memoryTransport__ = new org.apache.thrift.transport.TMemoryInputTransport(__buffer__)
          val __prot__ = protocolFactory.getProtocol(__memoryTransport__)
          __prot__
        }
      } catch {
        case e: org.apache.thrift.TException => com.twitter.util.Future.exception(e)
      }
    }

    var sent = 0

    // .onSuccess(responseProt => {
        // val response = descriptor.responseMetaRecord.createRawRecord
        // val responseMsg = responseProt.readMessageBegin()
        // response.read(responseProt)
        // responseProt.readMessageEnd()
        // println(response)
      // })

    def processMessage(rb: Array[Byte]) = {
       val inputTransport = new org.apache.thrift.transport.TMemoryInputTransport(rb)
       val iprot = factory.getProtocol(inputTransport)
       val msg = iprot.readMessageBegin()
       // println(msg.name)

      val descriptor = Geocoder.functionDescriptors.find(_.functionName == msg.name).get
      val req: TBase[_, _] = descriptor.requestMetaRecord.createRawRecord.asInstanceOf[TBase[_, _]]
      req.read(iprot)
      // println(req)
      val responseProtFuture = callService(msg.name, factory, req)
      responseProtFuture
    }

    val reader = new SeqFileByteIterator(seqfile)

    def requestLoop() {
      // println("requesting")
      var bytes: Array[Byte] = null
      reader.synchronized {
        if (reader.hasNext) {
          bytes = reader.next
        }
      }
      if (bytes != null) {
        processMessage(bytes).onSuccess(p => requestLoop())
      }
    }

    0.to(50).foreach { p => requestLoop() }
    //throw new Exception("done!")
  }
}
