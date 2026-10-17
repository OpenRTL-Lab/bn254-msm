<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: THIRD_PARTY_NOTICES.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 第三方声明

[English](THIRD_PARTY_NOTICES.en.md)

本仓库自主编写的Chisel、生成工具、参考调用与验证代码采用MIT许可；本文件列出算法参数、数学公式与外部工具的资料来源，第三方工具仍遵循各自许可，未随本仓库重分发其二进制或工艺库。

## 参数与数学资料

- [BN254 参数](https://docs.rs/ark-bn254/latest/ark_bn254/)：用于核对基域、标量阶、曲线参数；未将arkworks软件实现复制进本仓库。
- [EIP-196 曲线定义](https://eips.ethereum.org/EIPS/eip-196)：同一曲线的以太坊规范；本模块不是该预编译合约的接口实现。
- [Jacobian 公式资料](https://hyperelliptic.org/EFD/g1p/auto-shortw-jacobian.html)：短Weierstrass曲线Jacobian点加和倍点的数学资料。

这些引用帮助读者核对数学含义，不把外部协议、工具或参数适用范围扩大为本仓库已交付能力。

## 依赖工具

Mill、Scala、Chisel、firtool、Verilator、GCC/G++和OpenSSL分别遵循上游许可。C参考调用OpenSSL通用EC/BN接口，不内嵌OpenSSL源码；用户安装的OpenSSL版本按其自身许可使用。安装版本和用途见 [工具链说明](docs/toolchain.md)。

商业使用、修改和再分发本仓库内容时应保留 [MIT 许可证](LICENSE) 及相应声明。第三方工艺资料未包含于本仓库，综合资料边界见 [综合结果](docs/synthesis-summary.md)。
