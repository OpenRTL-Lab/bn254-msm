# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
# Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
# Repository: https://github.com/OpenRTL-Lab/bn254-msm
# File: tools/verify.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""对新生成和随包产品进行参考模型核对、协议验证及理想接口周期统计。"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
from emit_sv import ROOT, OP, BUNDLED, build_path, generate, run, simulation_list

def atomic(path, value):
    temporary = path.with_suffix('.pending')
    temporary.write_text(json.dumps(value, indent=2)+'\n'); temporary.replace(path)

def values(line):
    return {key: int(value) for key, value in re.findall(r'(\w+)=\s*(\d+)', line)}

def verify(output, capacity=65536, n=8, tasks=3, jobs=8, modes=None, bundled=False):
    os.chdir(ROOT); output = build_path(output)
    rtl = generate(output, capacity, n, tasks)
    report = {'status': 1, 'configuration': {'capacity': capacity, 'window_bits': 8, 'n': n, 'tasks': tasks},
              'ideal_interfaces': True, 'bundled': bundled, 'tests': {}, 'tools': {}}
    try:
        for tool, argument in [('java', '-version'), ('verilator', '--version'), ('cc', '--version'), ('openssl', 'version')]:
            report['tools'][tool] = subprocess.check_output([tool, argument], stderr=subprocess.STDOUT, text=True).strip()
        product_list = BUNDLED/'files.f' if bundled else rtl/'product.f'
        if bundled:
            if capacity != 65536: raise ValueError('Bundled product capacity is 65536')
            simulation_list(rtl, rtl/'bundled-simulation.f', True)
        run(['verilator', '--lint-only', '--top-module', 'Bn254Msm', '-f', product_list], output, 'product-lint')
        os.environ['CCACHE_DISABLE'] = '1'
        obj = output/'obj'
        run(['verilator', '-O3', '--assert', '--cc', '--exe', '--build', '-j', jobs, '-DENABLE_INITIAL_MEM_',
             '--top-module', 'MsmSimTop', '-Mdir', obj, '-CFLAGS', '-O3 -std=c++17 -Wall -Wextra',
             '-f', rtl/('bundled-simulation.f' if bundled else 'simulation.f'), ROOT/OP/'sim/clock.cpp'], output, 'verilator')
        for mode in (list(range(11)) if modes is None else modes):
            label = f'mode{mode}'; run([obj/'VMsmSimTop', mode], output, label, 1 if mode == 3 else 0)
            log = (output/(label+'.log')).read_text()
            results = [values(s) for s in log.splitlines() if s.startswith('MSM_SIM ')]
            assert len(results) == 1; result = results[0]
            assert result['mismatches'] == (1 if mode == 3 else 0)
            assert result['checked'] == (0 if mode in [2,5,6,7,8,9] else tasks)
            finished = [values(s) for s in log.splitlines() if s.startswith('MSM_COMPLETE ')]
            assert len(finished) == tasks
            report['tests'][label] = {'result': result, 'tasks': finished}
            if mode == 0:
                stats = [values(s) for s in log.splitlines() if s.startswith('MSM_STATS ')]
                assert len(stats) == tasks
                intervals = [finished[i]['tick']-finished[i-1]['tick'] for i in range(1, tasks)]
                report['performance'] = {
                    'task_latency_cycles': [t['tick']-t['A']+1 for t in finished],
                    'data_latency_cycles': [t['O']-t['F']+1 for t in finished],
                    'completion_intervals': intervals,
                    'steady_tasks_per_cycle': (tasks-1)/(finished[-1]['tick']-finished[0]['tick']),
                    'steady_pairs_per_cycle': n*(tasks-1)/(finished[-1]['tick']-finished[0]['tick']), 'stats': stats}
            atomic(output/'progress.json', report)
        report['status'] = 0
    except Exception as error:
        report['error'] = str(error); raise
    finally:
        atomic(output/'result.json', report)
    print('Verification passed: C reference, RTL comparisons, protocol tests and cycle metrics')
    return report

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', default='build/verify')
    parser.add_argument('--capacity', type=int, default=65536)
    parser.add_argument('--n', type=int, default=8)
    parser.add_argument('--tasks', type=int, default=3)
    parser.add_argument('--jobs', type=int, default=8)
    parser.add_argument('--modes', nargs='+', type=int, choices=range(11))
    parser.add_argument('--bundled', action='store_true')
    a = parser.parse_args()
    if a.jobs < 1: parser.error('jobs must be positive')
    verify(a.out, a.capacity, a.n, a.tasks, a.jobs, a.modes, a.bundled)
