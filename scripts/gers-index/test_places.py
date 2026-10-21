"""Tests for the places formats in gers_index.py (ADR-039 WP-P1). No network, no devnet.
Run: ~/Development/scratch/gers-index/.venv/bin/python -I test_places.py"""
import os, sys, unittest, zlib
from unittest import mock
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gers_index as g

U1 = "0a1b2c3d-0000-4000-8000-000000000001"; U2 = "0a1b2c3d-0000-4000-8000-000000000002"; U3 = "ffffffff-0000-4000-8000-0000000000ff"
# (id, name, lat, lon, confidence, category)
PLACES = [(U1, "Café Ünï", 51.500001, -0.120002, 0.97, "cafe"), (U2, "Corner shop", 51.499, -0.1205, None, "convenience_store"),
          (U3, "日本料理", 60.0, 1.9, 0.5, "cafe")]
CI = {"cafe": 0, "convenience_store": 1}

class PlacesFormats(unittest.TestCase):
    def test_unit_round_trip(self):
        # Arrange
        blob = g.encode_unit(PLACES, CI)
        # Act
        got, end = g.decode_unit(b"xx" + blob, 2)
        # Assert
        self.assertEqual(end, 2 + len(blob))
        self.assertEqual([p[0] for p in got], [u.replace("-", "") for u in (U1, U2, U3)])
        self.assertEqual([p[1] for p in got], ["Café Ünï", "Corner shop", "日本料理"])
        for (_, _, la, lo, *_), p in zip(PLACES, got):
            self.assertLessEqual(abs(p[2] - la * 1e6), 1); self.assertLessEqual(abs(p[3] - lo * 1e6), 1)
        self.assertEqual([p[4] for p in got], [round(0.97 * 255), 0, round(0.5 * 255)])
        self.assertEqual([p[5] for p in got], [0, 1, 0])

    def test_chunk_directory_categories_root_round_trip(self):
        # Arrange
        units = [g.encode_unit(PLACES[:2], CI), g.encode_unit(PLACES[2:], CI)]
        chunks, where = g.pack_chunks(units)
        ranges = {0: [(where[0][0], where[0][1], where[0][2], 2, 0)], 2: [(where[1][0], where[1][1], where[1][2], 1, 1)]}
        a = lambda k: bytes([k]).hex() * 32
        # Act
        raw = g.decode_data_chunk(chunks[0])
        sizes, rows = g.decode_places_dir(g.encode_places_dir([len(c) for c in chunks], ranges, 4))
        groups, cats = g.decode_categories(g.encode_categories(["food_and_drink", "shopping"], [("cafe", "food_and_drink"), ("convenience_store", "shopping")]))
        root = g.parse_places_root(g.encode_places_root("2026-09-23.1", "GB", a(1), a(2), [a(3), a(4)]))
        # Assert
        self.assertEqual(len(chunks), 1); self.assertEqual(where[0][1], 4)
        self.assertEqual(g.decode_unit(raw, where[1][1])[0][0][1], "日本料理")
        self.assertEqual(sizes, [len(chunks[0])])
        self.assertEqual(rows, [ranges[0], [], ranges[2], []])
        self.assertEqual((groups, cats), (["food_and_drink", "shopping"], [("cafe", 0), ("convenience_store", 1)]))
        self.assertEqual(root, dict(release="2026-09-23.1", country="GB", directory=a(1), categories=a(2), chunks=[a(3), a(4)]))

    def test_pack_chunks_respects_target_and_never_splits_units(self):
        # Arrange
        import random; rnd = random.Random(3)
        units = [bytes(rnd.getrandbits(8) for _ in range(rnd.randint(200, 3000))) for _ in range(120)]
        # Act
        chunks, where = g.pack_chunks(units, target=20000)
        # Assert
        self.assertGreater(len(chunks), 3)
        self.assertTrue(all(len(c) <= 20000 for c in chunks))
        for u, (ch, off, ln) in zip(units, where):
            self.assertEqual(g.decode_data_chunk(chunks[ch])[off:off + ln], u)

    def test_wrong_magic_is_rejected(self):
        # Arrange
        bad = zlib.compress(b"XXXX")
        # Act / Assert
        for f in (g.decode_data_chunk, g.decode_places_dir, g.decode_categories):
            with self.assertRaises(ValueError): f(bad)
        with self.assertRaises(ValueError): g.parse_places_root(b"VGI2" + bytes(100))

class NoContactColumns(unittest.TestCase):
    def test_extract_select_has_only_the_allowed_columns(self):
        # Arrange / Act
        select = g.extract_select()
        # Assert
        self.assertEqual(g.PLACES_COLUMNS, ("id", "name", "lat", "lon", "confidence", "category", "grp"))
        self.assertEqual([part.rsplit(" AS ", 1)[1] for part in select.split(", ")], list(g.PLACES_COLUMNS))
        for word in ("phones", "emails", "websites", "socials", "addresses", "brand", "freeform", "postcode"): self.assertNotIn(word, select)

    def test_assertion_refuses_contact_and_unknown_columns(self):
        # Arrange / Act / Assert
        self.assertEqual(g.assert_no_contact_columns(list(g.PLACES_COLUMNS)), list(g.PLACES_COLUMNS))
        for col in ("phones", "emails", "websites", "socials", "addresses", "brand", "operating_status"):
            with self.assertRaises(AssertionError): g.assert_no_contact_columns(list(g.PLACES_COLUMNS) + [col])

class DevnetOnly(unittest.TestCase):
    def test_refuses_without_manifest(self):
        # Arrange
        with mock.patch.object(g, "DEVNET", "/nonexistent-devnet"):
            # Act / Assert
            with self.assertRaises(SystemExit): g.require_devnet()

    def test_refuses_real_wallet_settings(self):
        # Arrange
        with mock.patch.dict(os.environ, {"EVM_NETWORK": "arbitrum-one"}):
            if not os.path.isfile(f"{g.DEVNET}/ant/devnet-manifest.json"): self.skipTest("no local devnet manifest")
            # Act / Assert
            with self.assertRaises(SystemExit): g.require_devnet()
        with mock.patch.dict(os.environ, {"SECRET_KEY": "0x" + "11" * 32}):
            if not os.path.isfile(f"{g.DEVNET}/ant/devnet-manifest.json"): self.skipTest("no local devnet manifest")
            with self.assertRaises(SystemExit): g.require_devnet()

if __name__ == "__main__": unittest.main()
