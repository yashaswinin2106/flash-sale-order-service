"""Prints a markdown table from k6 summary files in loadtest/results.

Usage: python loadtest/summarize.py [glob]    e.g. "direct-*.json"
"""
import json
import pathlib
import sys

pattern = sys.argv[1] if len(sys.argv) > 1 else "*.json"
files = sorted(pathlib.Path(__file__).parent.joinpath("results").glob(pattern))

BUILT_IN = {"checks", "data_received", "data_sent", "http_req_blocked", "http_req_connecting",
            "http_req_duration", "http_req_failed", "http_req_receiving", "http_req_sending",
            "http_req_tls_handshaking", "http_req_waiting", "http_reqs", "iteration_duration",
            "iterations", "vus", "vus_max"}

print("| Run | Requests/s | p50 ms | p95 ms | p99 ms | Counters |")
print("|---|---|---|---|---|---|")
for f in files:
    m = json.loads(f.read_text())["metrics"]
    d = m["http_req_duration"]
    counters = ", ".join(f"{k}={int(v['count'])}" for k, v in sorted(m.items())
                         if k not in BUILT_IN and "count" in v)
    steps = [(k.split("step:")[1].rstrip("}"), v) for k, v in sorted(m.items())
             if k.startswith("http_req_duration{step:")]
    print(f"| {f.stem} | {m['http_reqs']['rate']:.0f} | {d['med']:.0f} | {d['p(95)']:.0f} "
          f"| {d['p(99)']:.0f} | {counters} |")
    for step, v in steps:
        print(f"| {f.stem} / {step} | | {v['med']:.0f} | {v['p(95)']:.0f} | {v['p(99)']:.0f} | |")
