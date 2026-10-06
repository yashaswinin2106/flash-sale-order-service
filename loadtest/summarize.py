"""Prints a markdown table from the k6 summary files in loadtest/results."""
import json
import pathlib
import sys

files = sorted(pathlib.Path(__file__).parent.joinpath("results").glob(sys.argv[1] if len(sys.argv) > 1 else "*.json"))
print("| Run | Requests/s | p50 ms | p95 ms | p99 ms | Created | Sold out | Other errors |")
print("|---|---|---|---|---|---|---|---|")
for f in files:
    m = json.loads(f.read_text())["metrics"]
    d = m["http_req_duration"]
    count = lambda name: int(m.get(name, {}).get("count", 0))
    print(f"| {f.stem} | {m['http_reqs']['rate']:.0f} | {d['med']:.0f} | {d['p(95)']:.0f} | {d['p(99)']:.0f} "
          f"| {count('orders_created')} | {count('orders_sold_out')} | {count('orders_other_errors')} |")
