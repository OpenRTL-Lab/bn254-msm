/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
 * Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
 * Repository: https://github.com/OpenRTL-Lab/bn254-msm
 * File: operators/msm-weierstrass/src/main/scala/zk/msm/Field.scala
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

object Bn254 {
  val P = BigInt("21888242871839275222246405745257275088696311157297823662689037894645226208583")
  val Order = BigInt("21888242871839275222246405745257275088548364400416034343698204186575808495617")
  val R = (BigInt(1) << 256) % P
  val R2 = R * R % P
  val Inv64 = (-P.modInverse(BigInt(1) << 64)) & ((BigInt(1) << 64)-1)
  def add(a: UInt, b: UInt): UInt = {
    val s = a +& b
    Mux(s >= P.U(257.W), s - P.U(257.W), s)(255,0)
  }
  def sub(a: UInt, b: UInt): UInt = {
    val d = Cat(0.U(1.W),a) + P.U(257.W) - b
    Mux(a >= b, a-b, d)(255,0)
  }
}
class MulArgs extends Bundle { val a=UInt(256.W);val b=UInt(256.W) }

/** 四limb迭代Montgomery；每个宽乘积单独寄存，不是理想软件取模。 */
class Bn254MontgomeryMul extends Module {
  val io=IO(new Bundle {
    val command=Flipped(Decoupled(new MulArgs))
    val result=Decoupled(UInt(256.W))
    val working=Output(Bool())
    val productStep=Output(Bool())
  })
  val idle :: product :: addProduct :: lowFactor :: modulusProduct :: eliminate :: correct :: output :: Nil=Enum(8)
  val state=RegInit(idle)
  val a=Reg(UInt(256.W));val b=Reg(UInt(256.W));val limb=Reg(UInt(2.W))
  val acc=Reg(UInt(321.W));val prod=Reg(UInt(320.W));val sum=Reg(UInt(321.W))
  val factor=Reg(UInt(64.W));val mp=Reg(UInt(320.W));val answer=Reg(UInt(256.W))
  io.command.ready:=state===idle
  io.result.valid:=state===output;io.result.bits:=answer
  io.working:=state=/=idle && state=/=output
  io.productStep:=state===product
  when(io.command.fire) {
    assert(io.command.bits.a < Bn254.P.U && io.command.bits.b < Bn254.P.U)
    a:=io.command.bits.a;b:=io.command.bits.b;acc:=0.U;limb:=0.U;state:=product
  }
  switch(state) {
    is(product) { prod := (a >> (limb << 6))(63,0)*b; state:=addProduct }
    is(addProduct) { sum:=prod+acc;state:=lowFactor }
    is(lowFactor) { factor:=(sum(63,0)*Bn254.Inv64.U(64.W))(63,0);state:=modulusProduct }
    is(modulusProduct) { mp:=factor*Bn254.P.U(256.W);state:=eliminate }
    is(eliminate) {
      val t=sum+&mp
      assert(t(63,0)===0.U,"Montgomery low limb must vanish")
      acc:=t >> 64
      when(limb===3.U){state:=correct}.otherwise{limb:=limb+1.U;state:=product}
    }
    is(correct) {
      assert(acc < (2*Bn254.P).U(321.W))
      answer:=Mux(acc>=Bn254.P.U,acc-Bn254.P.U,acc)(255,0);state:=output
    }
    is(output){when(io.result.fire){state:=idle}}
  }
}
