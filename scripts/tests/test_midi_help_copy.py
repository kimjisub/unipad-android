"""Keep the connection guidance bilingual and other locales on the English fallback."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2] / 'app/src/main/res'


class MidiHelpCopyTest(unittest.TestCase):
    def strings(self, folder):
        return {item.attrib['name']: ''.join(item.itertext())
                for item in ET.parse(ROOT / folder / 'strings.xml').getroot()}

    def test_status_and_entry_copy(self):
        expected = {
            'values': {
                'midi_status_none': 'No Launchpad found',
                'midi_status_permission': 'USB permission needed',
                'midi_status_disconnected': 'Disconnected',
                'midi_status_input': 'Pad signal received.',
                'midi_help_title': 'Not connecting?',
                'midi_help_close': 'Close help',
            },
            'values-ko': {
                'midi_status_none': '런치패드를 찾지 못했어요',
                'midi_status_permission': 'USB 사용 허락이 필요해요',
                'midi_status_disconnected': '연결이 끊겼어요',
                'midi_status_input': '패드 신호를 받았어요.',
                'midi_help_title': '연결이 안 되나요?',
                'midi_help_close': '도움말 닫기',
            },
        }
        for folder, entries in expected.items():
            strings = self.strings(folder)
            for key, value in entries.items():
                self.assertEqual(value, strings.get(key), (folder, key))

    def test_other_languages_use_english_guidance(self):
        for folder in ROOT.glob('values-*'):
            if folder.name == 'values-ko' or not (folder / 'strings.xml').exists():
                continue
            for key in self.strings(folder.name):
                self.assertFalse(key.startswith(('midi_status_', 'midi_help_', 'midi_source_')),
                                 (folder.name, key))

    def test_firmware_card_name_preserves_resource_key(self):
        for folder in [ROOT / 'values', *ROOT.glob('values-*')]:
            if not (folder / 'strings.xml').exists():
                continue
            name = self.strings(folder.name).get('midi_lp_pro_cfw')
            if name is not None:
                self.assertEqual('Launchpad Pro (Launchpad Open)', name, folder.name)


if __name__ == '__main__':
    unittest.main()
