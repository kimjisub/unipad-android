"""Keep test-only text out of release builds without losing debug translations."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

APP = Path(__file__).resolve().parents[2] / "app"
DEBUG_ONLY = {"app_name_test", "workspace_internal_sd_card"}


def strings(source_set):
    result = {}
    for path in (APP / "src" / source_set / "res").glob("values*/*.xml"):
        for element in ET.parse(path).getroot().findall("string"):
            result.setdefault(path.parent.name, {})[element.attrib["name"]] = "".join(element.itertext())
    return result


class VariantStringsTest(unittest.TestCase):
    def test_release_does_not_include_test_only_strings(self):
        for source_set in ("main", "release"):
            for qualifier, values in strings(source_set).items():
                with self.subTest(source_set=source_set, qualifier=qualifier):
                    self.assertFalse(DEBUG_ONLY & values.keys())

    def test_debug_name_and_workspace_text_resolve_in_every_app_locale(self):
        main = strings("main")
        debug = strings("debug")
        for qualifier, values in main.items():
            if "app_name" not in values:
                continue
            with self.subTest(qualifier=qualifier):
                self.assertEqual(debug.get(qualifier, {}).get("app_name_test"), "UniPad Test")
                self.assertTrue(debug.get(qualifier, {}).get("workspace_internal_sd_card"))


if __name__ == "__main__":
    unittest.main()
