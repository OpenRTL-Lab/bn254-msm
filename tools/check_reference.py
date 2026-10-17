# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
# Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
# Repository: https://github.com/OpenRTL-Lab/bn254-msm
# File: tools/check_reference.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""独立Python仿射大整数模型：参数已知答案及小规模向量逐任务核对。"""
from pathlib import Path
import argparse

P = 21888242871839275222246405745257275088696311157297823662689037894645226208583
R = 21888242871839275222246405745257275088548364400416034343698204186575808495617

def add(a, b):
    if a is None: return b
    if b is None: return a
    x, y = a; u, v = b
    if x == u and (y + v) % P == 0: return None
    m = ((3*x*x)*pow(2*y, -1, P) if x == u else (v-y)*pow(u-x, -1, P)) % P
    w = (m*m-x-u) % P
    return w, (m*(x-w)-y) % P

def mul(a, scalar):
    result = None
    while scalar:
        if scalar & 1: result = add(result, a)
        a = add(a, a); scalar >>= 1
    return result

def check(directory, n, tasks):
    assert mul((1, 2), R) is None
    assert mul((1, 2), 2) == (
        1368015179489954701390400359078579693043519447331113978918064868415326638035,
        9918110051302171585080402603319702774565515993150576347155970296011118125764)
    source = (Path(directory) / 'input.hex').read_text().splitlines()
    expected = (Path(directory) / 'expected.hex').read_text().splitlines()
    assert len(source) == n*tasks and len(expected) == tasks
    for line in source:
        value = int(line, 16); scalar = value & ((1<<256)-1)
        y = (value>>256) & ((1<<256)-1); x = (value>>512) & ((1<<256)-1)
        infinity = value >> 768
        assert infinity in (0, 1) and scalar < R and x < P and y < P
        assert (x == y == 0) if infinity else (y*y-x*x*x-3) % P == 0
    if n <= 8:
        for task in range(tasks):
            result = None
            for line in source[task*n:(task+1)*n]:
                value = int(line, 16)
                point = None if value>>768 else ((value>>512) & ((1<<256)-1), (value>>256) & ((1<<256)-1))
                result = add(result, mul(point, value & ((1<<256)-1)))
            encoded = 1<<512 if result is None else (result[0]<<256) | result[1]
            assert encoded == int(expected[task], 16)
    return {'parameter_known_answers': True, 'input_domain_checks': True,
            'full_python_crosscheck': n <= 8}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('vectors'); parser.add_argument('--n', type=int, default=8)
    parser.add_argument('--tasks', type=int, default=3)
    args = parser.parse_args()
    print(check(args.vectors, args.n, args.tasks))
