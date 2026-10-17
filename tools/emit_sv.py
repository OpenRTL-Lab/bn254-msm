# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
# Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
# Repository: https://github.com/OpenRTL-Lab/bn254-msm
# File: tools/emit_sv.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""生成独立产品SV、Chisel仿真环境与OpenSSL参考向量。"""
from source_headers import stamp_generated
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
from check_reference import check

ROOT = Path(__file__).resolve().parents[1]
OP = Path('operators/msm-weierstrass')
BUNDLED = Path('rtl/v1.0.0/n65536-w8')

def build_path(value):
    path = Path(value)
    if path.is_absolute() or '..' in path.parts or not path.parts or path.parts[0] != 'build':
        raise ValueError('输出必须是build下面的新相对目录 / Use a new relative directory under build')
    return path

def run(command, output, label, expected=0):
    with (output / (label + '.log')).open('x') as log:
        result = subprocess.run([str(x) for x in command], cwd=ROOT,
                                stdout=log, stderr=subprocess.STDOUT)
    if result.returncode != expected:
        raise RuntimeError(f'{label}: exit {result.returncode}, expected {expected}; see the corresponding log')

def code(text):
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)

def product_closure(rtl):
    stamp_generated(rtl)
    definitions, texts = {}, {}
    for path in sorted(rtl.glob('*.sv')):
        text = re.sub(r'[ \t]*//[^\n]*\.scala:\d+:\d+[^\n]*', '', path.read_text())
        path.write_text(text)
        texts[path] = code(text)
        for module in re.findall(r'\bmodule\s+(\w+)', texts[path]):
            assert module not in definitions, 'Duplicate module'
            definitions[module] = path
    selected, pending = set(), ['Bn254Msm']
    while pending:
        path = definitions[pending.pop()]
        if path in selected: continue
        selected.add(path)
        instances = set(re.findall(r'^[ \t]*(\w+)\b[ \t]+(?:#\s*\([^;]*?\)\s*)?\w+\s*\(', texts[path], re.M | re.S))
        pending.extend(instances & definitions.keys())
    for path in selected:
        stripped = re.sub(r'"(?:[^"\\]|\\.)*"', '""', texts[path])
        assert not re.search(r'\$readmem|%|\b(?:DSP48\w*|RAMB\w*|URAM\w*|BUFG\w*|IOBUF)\b', stripped)
        assert 'SimTop' not in stripped
    assert re.search(r'\breg\s+\[16:0\]\s+length\b', definitions['Bn254Msm'].read_text())
    return sorted(selected)

def simulation_list(rtl, output, bundled=False):
    sources = [p for p in rtl.rglob('*.sv') if not p.name.startswith('layers-')]
    sources += list(rtl.rglob('layers-MsmSimTop-*.sv'))
    if bundled:
        selected = set(product_closure(rtl))
        sources = [p for p in sources if p not in selected]
        sources += sorted(BUNDLED.glob('*.sv'))
    includes = sorted({p.parent for p in rtl.rglob('*.sv')} | {p.parent for p in sources})
    output.write_text(''.join('+incdir+'+str(p)+'\n' for p in includes)
                      + ''.join(str(p)+'\n' for p in sorted(sources)))

def same_product(product):
    reference = {p.name: re.sub(r'\s+', '', code(p.read_text())) for p in BUNDLED.glob('*.sv')}
    generated = {p.name: re.sub(r'\s+', '', code(p.read_text())) for p in product}
    return reference == generated

def generate(output, capacity=65536, n=8, tasks=3):
    os.chdir(ROOT)
    output = build_path(output)
    if output.exists(): raise ValueError('Output already exists; choose a new directory')
    if not (2 <= capacity <= 65536) or capacity & (capacity-1):
        raise ValueError('Capacity must be a power of two from 2 through 65536')
    if not (1 <= n <= capacity) or not (3 <= tasks <= 16):
        raise ValueError('Require 1 <= n <= capacity and 3 <= tasks <= 16')
    output.mkdir(parents=True); vectors = output / 'vectors'; vectors.mkdir()
    compiler = shlex.split(os.environ.get('CC', 'cc'))
    flags = shlex.split(os.environ.get('CPPFLAGS', '')) + shlex.split(os.environ.get('CFLAGS', ''))
    link = shlex.split(os.environ.get('LDFLAGS', ''))
    run(compiler + ['-std=c11', '-O2', '-Wall', '-Wextra', '-Werror', '-Wno-deprecated-declarations']
        + flags + [OP/'cmodel/reference.c', '-o', output/'reference'] + link + ['-lcrypto'], output, 'c-compile')
    run([output/'reference', n, tasks, vectors], output, 'c-reference')
    crosscheck = check(vectors, n, tasks)
    rtl = output/'rtl'
    run(shlex.split(os.environ.get('MILL', 'mill')) + ['-i', 'bn254Msm.tool.runMain', 'zk.msm.Emit',
        rtl, capacity, n, tasks, vectors], output, 'emit')
    selected = product_closure(rtl)
    (rtl/'product.f').write_text(''.join(str(p)+'\n' for p in selected))
    simulation_list(rtl, rtl/'simulation.f')
    result = {'version': '1.0.0', 'capacity': capacity, 'window_bits': 8, 'n': n, 'tasks': tasks,
              'reference_checks': crosscheck, 'bundled_product_equivalent': same_product(selected) if capacity == 65536 else None}
    if capacity == 65536: assert result['bundled_product_equivalent'], 'Bundled and generated product differ'
    (output/'configuration.json').write_text(json.dumps(result, indent=2)+'\n')
    return rtl

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', default='build/emit')
    parser.add_argument('--capacity', type=int, default=65536)
    parser.add_argument('--n', type=int, default=8)
    parser.add_argument('--tasks', type=int, default=3)
    args = parser.parse_args(); generate(args.out, args.capacity, args.n, args.tasks)
    print('SV generation and reference checks passed; product list: rtl/product.f in the output directory')
