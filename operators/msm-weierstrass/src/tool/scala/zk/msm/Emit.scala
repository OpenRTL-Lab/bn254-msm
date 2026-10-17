/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
 * Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
 * Repository: https://github.com/OpenRTL-Lab/bn254-msm
 * File: operators/msm-weierstrass/src/tool/scala/zk/msm/Emit.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.msm
import circt.stage.ChiselStage
import java.nio.file.{Files,Paths}
object Emit extends App {
  require(args.length==5,"Emit outputRTL maxPoints n jobs vectorDirectory")
  val target=Paths.get(args(0));require(!Files.exists(target) && !target.isAbsolute && target.startsWith("build"))
  Files.createDirectories(target)
  ChiselStage.emitSystemVerilog(new MsmSimTop(MsmConfig(args(1).toInt),args(2).toInt,args(3).toInt,args(4)),
    firtoolOpts=Array("-O=release","-disable-all-randomization","--lowering-options=disallowLocalVariables",
      "-split-verilog",s"-o=$target"))
}
