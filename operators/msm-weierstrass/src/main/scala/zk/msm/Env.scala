/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
 * Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
 * Repository: https://github.com/OpenRTL-Lab/bn254-msm
 * File: operators/msm-weierstrass/src/main/scala/zk/msm/Env.scala
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
import chisel3.util.experimental.loadMemoryFromFileInline

class MsmSimTop(cfg:MsmConfig,n:Int,jobs:Int,dir:String) extends Module {
  val io=IO(new Bundle {val trigger=Input(Bool());val mode=Input(UInt(4.W));val done=Output(Bool())
    val pass=Output(Bool());val mismatches=Output(UInt(32.W));val checked=Output(UInt(32.W));val cycles=Output(UInt(64.W))})
  val idle::sendTask::sendInput::waitDone::finished::Nil=Enum(5)
  val state=RegInit(idle);val tick=RegInit(0.U(64.W));val job=RegInit(0.U(log2Ceil(jobs+1).W))
  val index=RegInit(0.U(17.W));val mismatch=RegInit(0.U(32.W));val checked=RegInit(0.U(32.W))
  val taskTick=Reg(UInt(64.W));val firstTick=Reg(UInt(64.W));val outputTick=Reg(UInt(64.W));val sawOutput=RegInit(false.B)
  val inputRom=Mem(n*jobs,UInt(769.W));val expectedRom=Mem(jobs,UInt(513.W))
  loadMemoryFromFileInline(inputRom,s"$dir/input.hex");loadMemoryFromFileInline(expectedRom,s"$dir/expected.hex")
  val didReset=RegInit(false.B)
  val resetAt=Mux(io.mode===10.U,1000.U,300.U)
  val localReset=(io.mode===4.U || io.mode===10.U) && !didReset && tick===resetAt
  val dut=withReset(reset.asBool || localReset){Module(new Bn254Msm(cfg))}
  val active=state=/=idle && state=/=finished
  when(active){tick:=tick+1.U}
  when(state===idle && io.trigger){state:=sendTask}
  val malformed=io.mode===2.U || io.mode===6.U || io.mode===7.U || io.mode===8.U || io.mode===9.U
  val d=inputRom(job*n.U+index);val expected=expectedRom(job)
  dut.io.task.valid:=state===sendTask && !localReset
  dut.io.task.bits.tag:=job+1.U;dut.io.task.bits.mode:=Mux(io.mode===5.U,1.U,0.U);dut.io.task.bits.length:=n.U
  dut.io.input.valid:=state===sendInput && !localReset
  dut.io.input.bits.tag:=Mux(io.mode===9.U && index===0.U,0.U,job+1.U)
  dut.io.input.bits.scalar:=Mux(io.mode===6.U && index===0.U,Bn254.Order.U,d(255,0))
  dut.io.input.bits.y:=d(511,256);dut.io.input.bits.x:=Mux(io.mode===7.U && index===0.U,Bn254.P.U,d(767,512))
  dut.io.input.bits.infinity:=d(768)
  dut.io.input.bits.last:=Mux(io.mode===8.U,false.B,index===(n-1).U || (io.mode===2.U && index===0.U))
  dut.io.output.ready:=active && (io.mode=/=1.U || tick(4,0)>=12.U)
  dut.io.completion.ready:=active && (io.mode=/=1.U || tick(4,0)>=20.U)
  when(dut.io.task.fire){taskTick:=tick;index:=0.U;sawOutput:=false.B
    state:=Mux(io.mode===5.U,waitDone,sendInput)
    printf(p"MSM_ACCEPT job=${job} tick=${tick}\n")}
  when(dut.io.input.fire){when(index===0.U){firstTick:=tick;printf(p"MSM_FIRST job=${job} tick=${tick}\n")}
    when(index===(n-1).U){state:=waitDone}.otherwise{index:=index+1.U}}
  when(dut.io.output.fire){
    val got=Cat(dut.io.output.bits.infinity,dut.io.output.bits.x,dut.io.output.bits.y)
    val golden=expected ^ Mux(io.mode===3.U && job===0.U,1.U,0.U)
    val good=got===golden && dut.io.output.bits.tag===job+1.U && dut.io.output.bits.last && !malformed && io.mode=/=5.U
    when(!good){mismatch:=mismatch+1.U;printf(p"MSM_MISMATCH job=${job} got=0x${Hexadecimal(got)} expected=0x${Hexadecimal(golden)}\n")}
    checked:=checked+1.U;sawOutput:=true.B;outputTick:=tick
    printf(p"MSM_OUTPUT job=${job} tick=${tick}\n")
  }
  when(dut.io.completion.fire){
    val wantStatus=Mux(io.mode===2.U || io.mode===8.U || io.mode===9.U,2.U,
      Mux(io.mode===5.U || io.mode===6.U,1.U,Mux(io.mode===7.U,3.U,0.U)))
    when(dut.io.completion.bits.tag=/=job+1.U || dut.io.completion.bits.status=/=wantStatus ||
      (wantStatus===0.U && !sawOutput) || (wantStatus=/=0.U && sawOutput)){mismatch:=mismatch+1.U}
    printf(p"MSM_COMPLETE job=${job} tick=${tick} A=${taskTick} F=${firstTick} O=${outputTick} status=${dut.io.completion.bits.status}\n")
    when(job===(jobs-1).U){state:=finished}.otherwise{job:=job+1.U;state:=sendTask}
  }
  when(localReset){didReset:=true.B;index:=0.U;job:=0.U;checked:=0.U;mismatch:=0.U;sawOutput:=false.B;state:=sendTask
    printf(p"MSM_RESET tick=${tick}\n")}
  when(tick>10000000000L.U){mismatch:=mismatch+1.U;state:=finished}
  io.done:=state===finished;io.pass:=state===finished && mismatch===0.U
  io.mismatches:=mismatch;io.checked:=checked;io.cycles:=tick
}
