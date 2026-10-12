#!/usr/bin/env python3
"""
The tests that failed in this checkout's Gradle runs, as Markdown for a run's
summary: each one's class, name and the start of its failure.

CI used to upload the HTML reports as artifacts for this, which cost storage
shared with the published packages, and when that storage was full they were
not uploaded at all — the one time they were needed, the failures could not be
read. The JUnit XML is already on the runner; this reads it.
"""
import glob
import xml.etree.ElementTree as ET

MAX_LINES = 12
MAX_FAILURES = 30

failures = []
for path in sorted(glob.glob("**/build/test-results/**/TEST-*.xml", recursive=True)):
    try:
        suite = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    task = path.split("/build/test-results/")[0] + ":" + path.split("/build/test-results/")[1].split("/")[0]
    for case in suite.iter("testcase"):
        for problem in list(case.findall("failure")) + list(case.findall("error")):
            text = (problem.get("message") or "") + "\n" + (problem.text or "")
            lines = [line for line in text.strip().splitlines() if line.strip()][:MAX_LINES]
            failures.append((task, case.get("classname", "?"), case.get("name", "?"), lines))

if not failures:
    print("No test failures were recorded; the failure is in the build's output above.")
else:
    print(f"### {len(failures)} test failure{'s' if len(failures) != 1 else ''}\n")
    for task, cls, name, lines in failures[:MAX_FAILURES]:
        print(f"**{cls.rsplit('.', 1)[-1]} › {name}** (`{task}`)\n")
        print("```")
        print("\n".join(lines))
        print("```\n")
    if len(failures) > MAX_FAILURES:
        print(f"…and {len(failures) - MAX_FAILURES} more.")
