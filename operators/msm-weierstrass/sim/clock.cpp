/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
 * Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
 * Repository: https://github.com/OpenRTL-Lab/bn254-msm
 * File: operators/msm-weierstrass/sim/clock.cpp
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

// 驱动器只提供时钟/复位/启动/测试模式，数据刺激与比较全部由Chisel ENV实现。
#include "VMsmSimTop.h"
#include "verilated.h"
#include <cstdint>
#include <cstdlib>
#include <cstdio>
int main(int argc,char**argv){Verilated::commandArgs(argc,argv);auto top=new VMsmSimTop;
  top->clock=0;top->reset=1;top->io_trigger=0;top->io_mode=argc>1?atoi(argv[1]):0;
  auto cycle=[&](){top->clock=0;top->eval();top->clock=1;top->eval();};
  for(int i=0;i<4;i++)cycle();top->reset=0;top->io_trigger=1;
  uint64_t watchdog=0;while(!top->io_done && watchdog<10000000005ULL){cycle();watchdog++;}
  bool pass=top->io_done&&top->io_pass;
  printf("MSM_SIM pass=%d checked=%u mismatches=%u cycles=%llu\n",pass,top->io_checked,top->io_mismatches,(unsigned long long)top->io_cycles);
  top->final();delete top;return pass?0:1;
}
