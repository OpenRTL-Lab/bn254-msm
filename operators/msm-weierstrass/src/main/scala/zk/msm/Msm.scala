/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
 * Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
 * Repository: https://github.com/OpenRTL-Lab/bn254-msm
 * File: operators/msm-weierstrass/src/main/scala/zk/msm/Msm.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.msm
import chisel3._
import chisel3.util._

case class MsmConfig(maxPoints:Int=65536) { require(maxPoints>=2 && maxPoints<=65536 && isPow2(maxPoints)); val aw=log2Ceil(maxPoints) }
class MsmTask extends Bundle {val tag=UInt(32.W);val mode=UInt(2.W);val length=UInt(17.W)}
class MsmRecord extends Bundle {val tag=UInt(32.W);val scalar=UInt(256.W);val infinity=Bool();val x=UInt(256.W);val y=UInt(256.W);val last=Bool()}
class MsmResult extends Bundle {val tag=UInt(32.W);val infinity=Bool();val x=UInt(256.W);val y=UInt(256.W);val last=Bool()}
class MsmCompletion extends Bundle {val tag=UInt(32.W);val status=UInt(3.W)}
class MsmIO extends Bundle {
  val task=Flipped(Decoupled(new MsmTask));val input=Flipped(Decoupled(new MsmRecord))
  val output=Decoupled(new MsmResult);val completion=Decoupled(new MsmCompletion)
}
class StoredPoint extends Bundle {val scalar=UInt(256.W);val infinity=Bool();val x=UInt(256.W);val y=UInt(256.W)}

class Bn254Msm(cfg:MsmConfig) extends Module {
  val io=IO(new MsmIO)
  // IP边界禁止验证环境的常量跨模块传播；不是综合dont_touch约束。
  // DC仍可以对交付RTL做正常优化，运行时length和协议端口必须保持通用。
  dontTouch(io)
  object S extends ChiselEnum {val Idle,Load,Convert,Converted,Clear,ReadPoint,PointData,
    ReadBucket,BucketData,Accumulate,Accumulated,ReduceRead,ReduceData,RunAdd,RunAdded,
    SumAdd,SumAdded,Double, Doubled,WindowAdd,WindowAdded,Normalize,Normalized,Output,Complete=Value}
  val state=RegInit(S.Idle);val tag=Reg(UInt(32.W));val length=Reg(UInt(17.W));val error=RegInit(0.U(3.W))
  val index=RegInit(0.U(17.W));val window=Reg(UInt(5.W));val bucket=Reg(UInt(8.W));val doubles=Reg(UInt(4.W))
  val points=SyncReadMem(cfg.maxPoints,new StoredPoint)
  val buckets=SyncReadMem(256,new Jacobian)
  val valid=Reg(Vec(256,Bool()))
  val pending=Reg(new StoredPoint);val point=Reg(new Jacobian);val oldBucket=Reg(new Jacobian)
  val running=Reg(new Jacobian);val sum=Reg(new Jacobian);val total=Reg(new Jacobian);val answer=Reg(new Jacobian)
  val engine=Module(new Bn254PointEngine)
  val zero=0.U.asTypeOf(new Jacobian)
  io.task.ready:=state===S.Idle;io.input.ready:=state===S.Load
  io.output.valid:=state===S.Output;io.output.bits.tag:=tag;io.output.bits.infinity:=answer.z===0.U
  io.output.bits.x:=answer.x;io.output.bits.y:=answer.y;io.output.bits.last:=true.B
  io.completion.valid:=state===S.Complete;io.completion.bits.tag:=tag;io.completion.bits.status:=error
  val pr=points.read(index(cfg.aw-1,0),state===S.ReadPoint)
  val br=buckets.read(bucket,state===S.ReadBucket || state===S.ReduceRead)
  engine.io.command.valid:=false.B;engine.io.command.bits:=0.U.asTypeOf(new PointCommand)
  engine.io.result.ready:=false.B
  def request(mode:Int,p:Jacobian,q:Jacobian,next:S.Type):Unit={
    engine.io.command.valid:=true.B;engine.io.command.bits.mode:=mode.U
    engine.io.command.bits.p:=p;engine.io.command.bits.q:=q
    when(engine.io.command.fire){state:=next}
  }
  def advancePoint():Unit={when(index===length-1.U){bucket:=255.U;running:=zero;sum:=zero;state:=S.ReduceRead}
    .otherwise{index:=index+1.U;state:=S.ReadPoint}}
  when(io.task.fire){
    tag:=io.task.bits.tag;length:=io.task.bits.length;index:=0.U;error:=0.U;total:=zero
    when(io.task.bits.mode=/=0.U || io.task.bits.length===0.U || io.task.bits.length>cfg.maxPoints.U){error:=1.U;state:=S.Complete}
    .otherwise{state:=S.Load}
  }
  when(io.input.fire){
    val x=io.input.bits;val finalInput=index===length-1.U
    val frameBad=x.tag=/=tag || x.last=/=finalInput
    val scalarBad=x.scalar>=Bn254.Order.U
    val pointBad=x.x>=Bn254.P.U || x.y>=Bn254.P.U || (x.infinity && (x.x=/=0.U || x.y=/=0.U))
    val newError=Mux(frameBad,2.U,Mux(scalarBad,1.U,Mux(pointBad,3.U,0.U)))
    when(error=/=0.U || newError=/=0.U){
      when(error===0.U){error:=newError}
      when(finalInput){state:=S.Complete}.otherwise{index:=index+1.U}
    }.otherwise{
      pending.scalar:=x.scalar;pending.infinity:=x.infinity;pending.x:=x.x;pending.y:=x.y
      point.x:=x.x;point.y:=x.y;point.z:=Mux(x.infinity,0.U,1.U);state:=S.Convert
    }
  }
  switch(state){
    is(S.Convert){request(3,point,zero,S.Converted)}
    is(S.Converted){engine.io.result.ready:=true.B;when(engine.io.result.fire){
      val p=Wire(new StoredPoint);p.scalar:=pending.scalar;p.infinity:=pending.infinity
      p.x:=engine.io.result.bits.x;p.y:=engine.io.result.bits.y;points.write(index(cfg.aw-1,0),p)
      when(index===length-1.U){window:=31.U;bucket:=0.U;index:=0.U;state:=S.Clear}
      .otherwise{index:=index+1.U;state:=S.Load}
    }}
    is(S.Clear){valid(bucket):=false.B;when(bucket===255.U){index:=0.U;state:=S.ReadPoint}.otherwise{bucket:=bucket+1.U}}
    is(S.ReadPoint){state:=S.PointData}
    is(S.PointData){
      point.x:=pr.x;point.y:=pr.y;point.z:=Mux(pr.infinity,0.U,Bn254.R.U)
      val digit=(pr.scalar>>(window<<3))(7,0);bucket:=digit
      when(digit===0.U || pr.infinity){advancePoint()}.otherwise{state:=S.ReadBucket}
    }
    is(S.ReadBucket){state:=S.BucketData}
    is(S.BucketData){oldBucket:=Mux(valid(bucket),br,zero);state:=S.Accumulate}
    is(S.Accumulate){request(0,oldBucket,point,S.Accumulated)}
    is(S.Accumulated){engine.io.result.ready:=true.B;when(engine.io.result.fire){
      buckets.write(bucket,engine.io.result.bits);valid(bucket):=engine.io.result.bits.z=/=0.U;advancePoint()
    }}
    is(S.ReduceRead){state:=S.ReduceData}
    is(S.ReduceData){oldBucket:=Mux(valid(bucket),br,zero);state:=S.RunAdd}
    is(S.RunAdd){request(0,running,oldBucket,S.RunAdded)}
    is(S.RunAdded){engine.io.result.ready:=true.B;when(engine.io.result.fire){running:=engine.io.result.bits;state:=S.SumAdd}}
    is(S.SumAdd){request(0,sum,running,S.SumAdded)}
    is(S.SumAdded){engine.io.result.ready:=true.B;when(engine.io.result.fire){sum:=engine.io.result.bits
      when(bucket===1.U){doubles:=0.U;state:=S.Double}.otherwise{bucket:=bucket-1.U;state:=S.ReduceRead}
    }}
    is(S.Double){request(1,total,zero,S.Doubled)}
    is(S.Doubled){engine.io.result.ready:=true.B;when(engine.io.result.fire){total:=engine.io.result.bits
      when(doubles===7.U){state:=S.WindowAdd}.otherwise{doubles:=doubles+1.U;state:=S.Double}
    }}
    is(S.WindowAdd){request(0,total,sum,S.WindowAdded)}
    is(S.WindowAdded){engine.io.result.ready:=true.B;when(engine.io.result.fire){total:=engine.io.result.bits
      when(window===0.U){state:=S.Normalize}.otherwise{window:=window-1.U;bucket:=0.U;state:=S.Clear}
    }}
    is(S.Normalize){request(2,total,zero,S.Normalized)}
    is(S.Normalized){engine.io.result.ready:=true.B;when(engine.io.result.fire){answer:=engine.io.result.bits;state:=S.Output}}
  }
  when(io.output.fire){state:=S.Complete}
  when(io.completion.fire){state:=S.Idle}
  // 计数器只存在于Verification提取层，产品SV不携带性能探针寄存器。
  chisel3.layer.block(chisel3.layers.Verification){
    val cycles=RegInit(0.U(64.W));val pointBusy=RegInit(0.U(64.W));val mulBusy=RegInit(0.U(64.W))
    val productBusy=RegInit(0.U(64.W));val mulIssues=RegInit(0.U(64.W));val linear=RegInit(0.U(64.W))
    val pointReads=RegInit(0.U(64.W));val bucketReads=RegInit(0.U(64.W));val pointIssues=RegInit(0.U(64.W))
    val windowTicks=RegInit(0.U(64.W));val windowPoint=RegInit(0.U(64.W));val windowMul=RegInit(0.U(64.W))
    val windowProduct=RegInit(0.U(64.W));val windowLinear=RegInit(0.U(64.W))
    val emptyBuckets=RegInit(0.U(17.W));val collisions=RegInit(0.U(17.W));val cancellations=RegInit(0.U(17.W))
    val skipped=RegInit(0.U(17.W));val used=RegInit(0.U(17.W))
    when(state=/=S.Idle){windowTicks:=windowTicks+1.U}
    when(engine.io.working){windowPoint:=windowPoint+1.U};when(engine.io.mulWorking){windowMul:=windowMul+1.U}
    when(engine.io.productStep){windowProduct:=windowProduct+1.U};when(engine.io.linearStep){windowLinear:=windowLinear+1.U}
    when(state===S.ReduceData && !valid(bucket)){emptyBuckets:=emptyBuckets+1.U}
    when(state===S.BucketData && valid(bucket)){collisions:=collisions+1.U}
    when(state===S.Accumulated && engine.io.result.fire && engine.io.result.bits.z===0.U){cancellations:=cancellations+1.U}
    when(state===S.PointData){
      val digit=(pr.scalar>>(window<<3))(7,0)
      when(digit===0.U || pr.infinity){skipped:=skipped+1.U}.otherwise{used:=used+1.U}
    }
    when(state===S.Clear && bucket===0.U){windowTicks:=0.U;windowPoint:=0.U;windowMul:=0.U
      windowProduct:=0.U;windowLinear:=0.U;emptyBuckets:=0.U;collisions:=0.U;cancellations:=0.U;skipped:=0.U;used:=0.U}
    when(state=/=S.Idle){cycles:=cycles+1.U}
    when(engine.io.working){pointBusy:=pointBusy+1.U};when(engine.io.mulWorking){mulBusy:=mulBusy+1.U}
    when(engine.io.productStep){productBusy:=productBusy+1.U};when(engine.io.mulIssue){mulIssues:=mulIssues+1.U}
    when(engine.io.linearStep){linear:=linear+1.U};when(state===S.ReadPoint){pointReads:=pointReads+1.U}
    when(state===S.ReadBucket || state===S.ReduceRead){bucketReads:=bucketReads+1.U}
    when(engine.io.command.fire){pointIssues:=pointIssues+1.U}
    when(io.task.fire){cycles:=0.U;pointBusy:=0.U;mulBusy:=0.U;productBusy:=0.U;mulIssues:=0.U
      linear:=0.U;pointReads:=0.U;bucketReads:=0.U;pointIssues:=0.U}
    when(io.completion.fire){printf(p"MSM_STATS tag=${tag} cycles=${cycles} point_busy=${pointBusy} mul_busy=${mulBusy} product_busy=${productBusy} mul_issues=${mulIssues} linear=${linear} point_reads=${pointReads} bucket_reads=${bucketReads} point_issues=${pointIssues}\n")}
    when(state===S.WindowAdded && engine.io.result.fire){printf(p"MSM_WINDOW tag=${tag} window=${window} cycles=${cycles} mul_issues=${mulIssues} window_cycles=${windowTicks+2.U} point_busy=${windowPoint} mul_busy=${windowMul} product_busy=${windowProduct} linear=${windowLinear} empty=${emptyBuckets} collisions=${collisions} cancellations=${cancellations} skipped=${skipped} used=${used}\n")}
    val blocked=RegNext(io.output.valid && !io.output.ready,false.B)
    val held=RegEnable(io.output.bits.asUInt,io.output.valid && !io.output.ready)
    when(blocked){assert(io.output.valid && io.output.bits.asUInt===held,"unstable MSM output")}
    val cb=RegNext(io.completion.valid && !io.completion.ready,false.B)
    val ch=RegEnable(io.completion.bits.asUInt,io.completion.valid && !io.completion.ready)
    when(cb){assert(io.completion.valid && io.completion.bits.asUInt===ch,"unstable completion")}
  }
}
