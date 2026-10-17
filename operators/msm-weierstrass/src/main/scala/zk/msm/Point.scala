/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
 * Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
 * Repository: https://github.com/OpenRTL-Lab/bn254-msm
 * File: operators/msm-weierstrass/src/main/scala/zk/msm/Point.scala
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

class Jacobian extends Bundle {val x=UInt(256.W);val y=UInt(256.W);val z=UInt(256.W)}
class PointCommand extends Bundle { val mode=UInt(2.W);val p=new Jacobian;val q=new Jacobian }
object PointProgram {
  // 一个操作只修改一个256位寄存器。0乘、1加、2减，3判断H/r，4结束。
  case class Ins(op:Int,d:Int=0,a:Int=0,b:Int=0)
  def mul(d:Int,a:Int,b:Int)=Ins(0,d,a,b)
  def add(d:Int,a:Int,b:Int)=Ins(1,d,a,b)
  def sub(d:Int,a:Int,b:Int)=Ins(2,d,a,b)
  val addition=Seq(
    mul(6,2,2),mul(7,5,5),mul(8,0,7),mul(9,3,6),mul(10,5,7),mul(11,1,10),
    mul(12,2,6),mul(13,4,12),sub(14,9,8),sub(15,13,11),Ins(3),
    mul(16,14,14),mul(17,16,14),mul(18,8,16),mul(19,15,15),add(20,18,18),
    sub(21,19,17),sub(21,21,20),sub(22,18,21),mul(23,15,22),mul(24,11,17),
    sub(25,23,24),mul(26,2,5),mul(27,26,14),Ins(4,21,25,27))
  val doubling=Seq(
    mul(6,0,0),mul(7,1,1),mul(8,7,7),add(9,0,7),mul(10,9,9),sub(10,10,6),
    sub(10,10,8),add(10,10,10),add(11,6,6),add(11,11,6),mul(12,11,11),
    add(16,10,10),sub(13,12,16),sub(17,10,13),mul(14,11,17),add(18,8,8),
    add(18,18,18),add(18,18,18),sub(14,14,18),mul(15,1,2),add(15,15,15),Ins(4,13,14,15))
  // 100..101初始逆运算，102..106固定指数循环，107..112仿射化/出Mont。
  val normalize=Seq(mul(6,28,28),Ins(5),mul(6,6,6),Ins(6),mul(6,6,2),Ins(7),Ins(7),
    mul(8,6,6),mul(9,8,6),mul(10,0,8),mul(11,1,9),mul(12,10,29),mul(13,11,29),Ins(8,12,13))
  val convert=Seq(mul(12,0,30),mul(13,1,30),Ins(9,12,13))
  val rom=(0 until 128).map(i =>
    if(i<addition.size)addition(i)
    else if(i>=64 && i<64+doubling.size)doubling(i-64)
    else if(i>=100 && i<100+normalize.size)normalize(i-100)
    else if(i>=120 && i<120+convert.size)convert(i-120)
    else Ins(10))
}

class Bn254PointEngine extends Module {
  val io=IO(new Bundle {
    val command=Flipped(Decoupled(new PointCommand))
    val result=Decoupled(new Jacobian)
    val working=Output(Bool());val mulWorking=Output(Bool());val productStep=Output(Bool())
    val mulIssue=Output(Bool());val linearStep=Output(Bool())
  })
  val idle :: run :: output :: Nil=Enum(3)
  val state=RegInit(idle);val pc=Reg(UInt(7.W));val regs=Reg(Vec(32,UInt(256.W)))
  val exponentBit=Reg(UInt(8.W));val waiting=RegInit(false.B);val answer=Reg(new Jacobian)
  val mul=Module(new Bn254MontgomeryMul)
  val rom=VecInit(PointProgram.rom.map(i=>Cat(i.op.U(4.W),i.d.U(5.W),i.a.U(5.W),i.b.U(5.W))))
  val ins=rom(pc);val op=ins(18,15);val d=ins(14,10);val a=ins(9,5);val b=ins(4,0)
  mul.io.command.valid:=state===run && op===0.U && !waiting
  mul.io.command.bits.a:=regs(a);mul.io.command.bits.b:=regs(b)
  mul.io.result.ready:=state===run && waiting
  io.command.ready:=state===idle;io.result.valid:=state===output;io.result.bits:=answer
  io.working:=state===run;io.mulWorking:=mul.io.working;io.productStep:=mul.io.productStep
  io.mulIssue:=mul.io.command.fire
  io.linearStep:=state===run && (op===1.U || op===2.U)
  def zero():Unit={answer.x:=0.U;answer.y:=0.U;answer.z:=0.U;state:=output}
  when(io.command.fire) {
    val p=io.command.bits.p;val q=io.command.bits.q
    regs(0):=p.x;regs(1):=p.y;regs(2):=p.z;regs(3):=q.x;regs(4):=q.y;regs(5):=q.z
    regs(28):=Bn254.R.U;regs(29):=1.U;regs(30):=Bn254.R2.U
    waiting:=false.B;state:=run
    when(io.command.bits.mode===0.U) {
      pc:=0.U
      when(p.z===0.U){answer:=q;state:=output}
      .elsewhen(q.z===0.U){answer:=p;state:=output}
    }.elsewhen(io.command.bits.mode===1.U) {
      pc:=64.U;when(p.z===0.U || p.y===0.U){zero()}
    }.elsewhen(io.command.bits.mode===2.U) {
      pc:=100.U;when(p.z===0.U){zero()}
    }.otherwise { pc:=120.U;when(p.z===0.U){zero()} }
  }
  when(mul.io.command.fire){waiting:=true.B}
  when(mul.io.result.fire){regs(d):=mul.io.result.bits;waiting:=false.B;pc:=pc+1.U}
  when(state===run) {
    switch(op) {
      is(1.U){regs(d):=Bn254.add(regs(a),regs(b));pc:=pc+1.U}
      is(2.U){regs(d):=Bn254.sub(regs(a),regs(b));pc:=pc+1.U}
      is(3.U){
        when(regs(14)===0.U){when(regs(15)===0.U){pc:=64.U}.otherwise{zero()}}
        .otherwise{pc:=pc+1.U}
      }
      is(4.U){answer.x:=regs(d);answer.y:=regs(a);answer.z:=regs(b);state:=output}
      is(5.U){exponentBit:=253.U;pc:=102.U}
      is(6.U){val bits=VecInit((0 until 254).map(i=>(Bn254.P-2).testBit(i).B))
        pc:=Mux(bits(exponentBit),104.U,105.U)}
      is(7.U){when(exponentBit===0.U){pc:=107.U}.otherwise{exponentBit:=exponentBit-1.U;pc:=102.U}}
      is(8.U){answer.x:=regs(d);answer.y:=regs(a);answer.z:=1.U;state:=output}
      is(9.U){answer.x:=regs(d);answer.y:=regs(a);answer.z:=Bn254.R.U;state:=output}
      is(10.U){assert(false.B,"invalid point microcode")}
    }
  }
  when(io.result.fire){state:=idle}
}
