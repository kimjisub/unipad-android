#!/usr/bin/env python3
"""
Android string resource analyzer.

Usage:
    python scripts/analyze_strings.py                # Full report
    python scripts/analyze_strings.py --check        # Exit code 1 if issues found (CI-friendly)
    python scripts/analyze_strings.py --apostrophes   # Check unescaped apostrophes only

Compares all values-*/strings.xml against the default values/strings.xml
and reports missing keys, extra keys, and unescaped apostrophes.
"""

import re
import glob
import os
import sys
import argparse

RES_DIR = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")


def get_translatable_keys(path):
    """Get ordered list of translatable string keys from a strings.xml file."""
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    all_keys = re.findall(r'<string\s+name="([^"]+)"', content)
    non_translatable = set(
        re.findall(r'<string\s+name="([^"]+)"\s+translatable="false"', content)
    )
    return [k for k in all_keys if k not in non_translatable]


def get_existing_keys(path):
    """Get set of all string keys in a strings.xml file."""
    with open(path, "r", encoding="utf-8") as f:
        return set(re.findall(r'<string\s+name="([^"]+)"', f.read()))


def find_unescaped_apostrophes(path):
    """Find lines with unescaped apostrophes in string values."""
    issues = []
    with open(path, "r", encoding="utf-8") as f:
        for i, line in enumerate(f, 1):
            m = re.search(r">([^<]+)</string>", line)
            if m:
                content = m.group(1)
                if re.search(r"(?<!\\)'", content):
                    key = re.search(r'name="([^"]+)"', line)
                    key_name = key.group(1) if key else "?"
                    issues.append((i, key_name, content.strip()))
    return issues


def find_duplicate_keys(path):
    """Find duplicate string keys in a file."""
    with open(path, "r", encoding="utf-8") as f:
        keys = re.findall(r'<string\s+name="([^"]+)"', f.read())
    seen = set()
    dupes = []
    for k in keys:
        if k in seen:
            dupes.append(k)
        seen.add(k)
    return dupes


def main():
    parser = argparse.ArgumentParser(description="Analyze Android string resources")
    parser.add_argument(
        "--check", action="store_true", help="Exit with code 1 if issues found"
    )
    parser.add_argument(
        "--apostrophes",
        action="store_true",
        help="Only check for unescaped apostrophes",
    )
    args = parser.parse_args()

    default_path = os.path.join(RES_DIR, "values", "strings.xml")
    if not os.path.exists(default_path):
        print(f"ERROR: Default strings.xml not found at {default_path}")
        sys.exit(2)

    default_keys = get_translatable_keys(default_path)
    default_keys_set = set(default_keys)
    locale_files = sorted(glob.glob(os.path.join(RES_DIR, "values-*", "strings.xml")))

    has_issues = False

    for f in locale_files:
        locale = os.path.basename(os.path.dirname(f)).replace("values-", "")
        issues = []

        if not args.apostrophes:
            # Check missing keys
            existing = get_existing_keys(f)
            missing = [k for k in default_keys if k not in existing]
            extra = existing - default_keys_set
            dupes = find_duplicate_keys(f)

            if missing:
                issues.append(f"  Missing ({len(missing)}): {missing}")
            if extra:
                issues.append(f"  Extra ({len(extra)}): {sorted(extra)}")
            if dupes:
                issues.append(f"  Duplicates: {dupes}")

        # Check apostrophes
        apostrophe_issues = find_unescaped_apostrophes(f)
        if apostrophe_issues:
            for line_no, key, content in apostrophe_issues:
                issues.append(f"  Unescaped apostrophe at line {line_no} ({key}): {content[:80]}")

        if issues:
            has_issues = True
            print(f"=== {locale} ===")
            for issue in issues:
                print(issue)
        else:
            print(f"=== {locale} === OK")

    # Also check default file for apostrophes
    apostrophe_issues = find_unescaped_apostrophes(default_path)
    if apostrophe_issues:
        has_issues = True
        print("=== default (en) ===")
        for line_no, key, content in apostrophe_issues:
            print(f"  Unescaped apostrophe at line {line_no} ({key}): {content[:80]}")
    else:
        print("=== default (en) === OK")

    if args.check and has_issues:
        sys.exit(1)


if __name__ == "__main__":
    main()
