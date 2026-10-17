<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/toolchain.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 工具链说明

[English](toolchain.en.md)

本仓库用固定版本的 Mill、Scala、Chisel 与 firtool 生成电路，使用 Verilator、C/C++ 和 OpenSSL 完成参考验证；下表列出已验证版本及便携安装要求，所有运行入口均从仓库根目录执行。

| 工具 | 已验证版本 | 用途 |
| --- | --- | --- |
| JDK | OpenJDK 21.0.11 | Mill/Scala/Chisel |
| Mill | 1.1.7 | 依赖、编译、生成入口 |
| Scala | 2.13.18 | Chisel 源码 |
| Chisel | 7.13.0 | 电路构建与验证提取层 |
| firtool | 1.149.0 | 降低为SystemVerilog |
| Verilator | 5.050 | SV lint与时钟仿真 |
| Python | 3.12 | 生成/验证脚本与大整数参考 |
| GCC/G++ | 16.2.0 | C参考及Verilator生成的C++ |
| OpenSSL | 1.1.1k | 独立通用椭圆曲线C参考 |

`build.mill` 固定Scala/Chisel版本及Mill版本元数据；JDK使用已安装版本。安装对应Mill，使 `mill` 位于PATH，或通过 `MILL` 环境变量指定调用命令。Chisel会解析匹配的firtool；如使用已安装工具，可设置 `CHISEL_FIRTOOL_PATH` 指向含firtool的目录。首次运行需要下载依赖。

Linux主机需要C/C++编译器、make、Verilator及OpenSSL开发头文件与libcrypto，Python无需第三方包。OpenSSL须支持本参考使用的通用EC/BN接口；脚本通过 `-lcrypto` 链接而不固定系统库路径，允许使用 `CC`、`CPPFLAGS`、`CFLAGS`、`LDFLAGS` 配置自己的安装。OpenSSL 3.x的旧EC接口弃用警告由编译选项单独处理，测试记录中的数值使用上表版本。

生成的产品只需要SV工具支持同步存储与常规寄存器/算术；C/OpenSSL和验证ROM仅用于参考测试，不属于产品。验证与集成操作见 [复现指南](reproduction.md)。
